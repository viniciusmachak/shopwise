package com.shopwise.backoffice.internal;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopwise.catalog.CatalogAdmin;
import com.shopwise.catalog.CatalogAdminException;
import com.shopwise.catalog.CatalogImportLine;

/**
 * catalogImportJob: read the uploaded CSV, validate each row, upsert products and variants
 * through the catalog's admin API. Bad rows are skipped and recorded with their line number.
 */
@Configuration(proxyBeanMethods = false)
class CatalogImportJobConfiguration {

    static final String JOB_NAME = "catalogImportJob";
    static final List<String> COLUMNS = List.of("sku", "product_name", "brand", "category", "description", "price",
            "size", "color", "stock");
    static final int MAX_SKIPPED_ROWS = 10_000;

    @Bean
    Job catalogImportJob(JobRepository jobs, Step catalogImportStep, ImportErrors errors) {
        return new JobBuilder(JOB_NAME, jobs).listener(errors).start(catalogImportStep).build();
    }

    @Bean
    Step catalogImportStep(JobRepository jobs, PlatformTransactionManager transactions,
            FlatFileItemReader<CsvRow> catalogCsvReader, CatalogAdmin catalog, ImportErrors errors) {
        return new StepBuilder("catalogImportStep", jobs)
                .<CsvRow, ImportItem>chunk(100)
                .transactionManager(transactions)
                .reader(catalogCsvReader)
                .processor(validator(catalog))
                .writer(writer(catalog))
                .faultTolerant()
                .skip(InvalidRowException.class, FlatFileParseException.class, CatalogAdminException.class)
                .skipLimit(MAX_SKIPPED_ROWS)
                .listener(errors)
                .build();
    }

    @Bean
    @StepScope
    FlatFileItemReader<CsvRow> catalogCsvReader(@Value("#{jobParameters['file']}") String file) {
        var tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(COLUMNS.toArray(String[]::new));
        tokenizer.setStrict(false);
        return new FlatFileItemReaderBuilder<CsvRow>()
                .name("catalogCsvReader")
                .resource(new FileSystemResource(Path.of(file)))
                .linesToSkip(1)
                .lineMapper((line, lineNumber) -> {
                    var fields = tokenizer.tokenize(line);
                    var values = new LinkedHashMap<String, String>();
                    for (var column : COLUMNS) {
                        values.put(column, fields.readRawString(column));
                    }
                    return new CsvRow(lineNumber, values);
                })
                .build();
    }

    private static ItemProcessor<CsvRow, ImportItem> validator(CatalogAdmin catalog) {
        return row -> {
            var missing = Set.of("sku", "product_name", "brand", "category", "price", "stock").stream()
                    .filter(c -> row.get(c).isEmpty()).sorted().toList();
            if (!missing.isEmpty()) {
                throw new InvalidRowException("Missing " + String.join(", ", missing) + ".");
            }
            BigDecimal price;
            int stock;
            try {
                price = new BigDecimal(row.get("price"));
            }
            catch (NumberFormatException e) {
                throw new InvalidRowException("Price \"" + row.get("price") + "\" is not a number.");
            }
            try {
                stock = Integer.parseInt(row.get("stock"));
            }
            catch (NumberFormatException e) {
                throw new InvalidRowException("Stock \"" + row.get("stock") + "\" is not a whole number.");
            }
            if (price.signum() < 0 || stock < 0) {
                throw new InvalidRowException("Price and stock can't be negative.");
            }
            return new ImportItem(row.lineNumber(), new CatalogImportLine(row.get("sku"), row.get("product_name"),
                    row.get("brand"), row.get("category").toLowerCase(), blankToNull(row.get("description")), price,
                    blankToNull(row.get("size")), blankToNull(row.get("color")), stock));
        };
    }

    private static ItemWriter<ImportItem> writer(CatalogAdmin catalog) {
        return chunk -> chunk.forEach(item -> catalog.importLine(item.line()));
    }

    /** A validated row and the CSV line it came from. */
    record ImportItem(int lineNumber, CatalogImportLine line) {
    }

    private static String blankToNull(String s) {
        return s.isBlank() ? null : s;
    }

    @Bean
    ImportErrors importErrors(JdbcClient jdbc, PlatformTransactionManager transactions) {
        var ownTransaction = new TransactionTemplate(transactions);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new ImportErrors(jdbc, ownTransaction);
    }

    /**
     * Records skipped rows against the running job execution. Jobs run on the caller's
     * thread, so the execution id is kept in a thread-local between beforeJob and afterJob.
     */
    static class ImportErrors implements SkipListener<CsvRow, ImportItem>, JobExecutionListener {

        private final JdbcClient jdbc;
        private final TransactionTemplate ownTransaction;
        private final ThreadLocal<Long> execution = new ThreadLocal<>();

        ImportErrors(JdbcClient jdbc, TransactionTemplate ownTransaction) {
            this.jdbc = jdbc;
            this.ownTransaction = ownTransaction;
        }

        @Override
        public void beforeJob(JobExecution jobExecution) {
            execution.set(jobExecution.getId());
        }

        @Override
        public void afterJob(JobExecution jobExecution) {
            execution.remove();
        }

        @Override
        public void onSkipInRead(Throwable t) {
            int line = t instanceof FlatFileParseException parse ? parse.getLineNumber() : 0;
            record(line, "This line could not be read as CSV.");
        }

        @Override
        public void onSkipInProcess(CsvRow item, Throwable t) {
            record(item.lineNumber(), t.getMessage());
        }

        @Override
        public void onSkipInWrite(ImportItem item, Throwable t) {
            record(item.lineNumber(), t.getMessage());
        }

        private void record(int line, String message) {
            var id = execution.get();
            if (id == null) {
                return;
            }
            var text = message == null ? "Unknown error." : message.length() > 500 ? message.substring(0, 500) : message;
            // Its own transaction: a failed write rolls back the chunk, but the error must stay.
            ownTransaction.executeWithoutResult(status -> jdbc
                    .sql("insert into catalog_import_error (job_execution_id, line_number, message) values (:id, :line, :message)")
                    .param("id", id)
                    .param("line", line)
                    .param("message", text)
                    .update());
        }
    }
}
