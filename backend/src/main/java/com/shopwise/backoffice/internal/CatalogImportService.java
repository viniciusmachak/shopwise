package com.shopwise.backoffice.internal;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Runs catalog imports and reads their reports. Imports are small, so the job runs while the
 * request waits; every run is kept in Spring Batch's tables for the import history.
 */
@Service
class CatalogImportService {

    record ImportError(int line, String message) {
    }

    record ImportReport(long id, String fileName, String status, Instant startedAt, long read, long imported,
            long skipped, List<ImportError> errors) {
    }

    private final JobOperator jobs;
    private final Job catalogImportJob;
    private final JdbcClient jdbc;

    CatalogImportService(JobOperator jobs, Job catalogImportJob, JdbcClient jdbc) {
        this.jobs = jobs;
        this.catalogImportJob = catalogImportJob;
        this.jdbc = jdbc;
    }

    ImportReport run(String fileName, InputStream csv) throws Exception {
        var file = Files.createTempFile("catalog-import-", ".csv");
        try {
            Files.copy(csv, file, StandardCopyOption.REPLACE_EXISTING);
            var parameters = new JobParametersBuilder()
                    .addString("file", file.toString())
                    .addString("fileName", fileName)
                    .addLong("startedAt", System.nanoTime())
                    .toJobParameters();
            var result = jobs.start(catalogImportJob, parameters);
            return report(result.getId()).orElseThrow();
        }
        finally {
            Files.deleteIfExists(file);
        }
    }

    List<ImportReport> recent() {
        return jdbc.sql("""
                select e.job_execution_id from batch_job_execution e
                join batch_job_instance i on i.job_instance_id = e.job_instance_id
                where i.job_name = :job order by e.job_execution_id desc limit 20
                """)
                .param("job", CatalogImportJobConfiguration.JOB_NAME)
                .query(Long.class)
                .list()
                .stream()
                .map(id -> report(id).orElseThrow())
                .toList();
    }

    Optional<ImportReport> report(long executionId) {
        return jdbc.sql("""
                select e.job_execution_id as id,
                       (select p.parameter_value from batch_job_execution_params p
                        where p.job_execution_id = e.job_execution_id and p.parameter_name = 'fileName') as file_name,
                       e.status, e.start_time,
                       coalesce(sum(s.read_count), 0) as read_count,
                       coalesce(sum(s.write_count), 0) as write_count,
                       coalesce(sum(s.read_skip_count + s.process_skip_count + s.write_skip_count), 0) as skip_count
                from batch_job_execution e
                left join batch_step_execution s on s.job_execution_id = e.job_execution_id
                where e.job_execution_id = :id
                group by e.job_execution_id
                """)
                .param("id", executionId)
                .query((rs, n) -> new ImportReport(rs.getLong("id"), rs.getString("file_name"), rs.getString("status"),
                        rs.getTimestamp("start_time") == null ? Instant.now() : rs.getTimestamp("start_time").toInstant(),
                        rs.getLong("read_count"), rs.getLong("write_count"), rs.getLong("skip_count"),
                        errors(rs.getLong("id"))))
                .optional();
    }

    private List<ImportError> errors(long executionId) {
        return jdbc.sql("select line_number as line, message from catalog_import_error where job_execution_id = :id order by line_number, id limit 200")
                .param("id", executionId)
                .query(ImportError.class)
                .list();
    }

    static String template() {
        return String.join(",", CatalogImportJobConfiguration.COLUMNS) + "\n"
                + "TRAIL-RUNNER-X-13,Trail Runner X,Northpeak,running-shoes,,89.90,13,Black,4\n"
                + "SUMMIT-SOCK-M,Summit Wool Socks,Alpstep,t-shirts,Warm merino hiking socks.,19.00,M,Gray,25\n";
    }
}
