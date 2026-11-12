package com.shopwise.catalog.internal;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.catalog.AdminProductRow;
import com.shopwise.catalog.AdminProductView;
import com.shopwise.catalog.CatalogAdmin;
import com.shopwise.catalog.CatalogAdminException;
import com.shopwise.catalog.CatalogAdminException.Reason;
import com.shopwise.catalog.CatalogImportLine;
import com.shopwise.catalog.ProductForm;
import com.shopwise.catalog.ProductUpdated;
import com.shopwise.catalog.VariantForm;

import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
class JdbcCatalogAdmin implements CatalogAdmin {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    JdbcCatalogAdmin(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminProductRow> list(@Nullable String query, boolean includeArchived) {
        var pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return jdbc.sql("""
                select p.id, p.name, p.brand, c.name as category, p.price, p.active,
                       count(v.id) as variant_count, coalesce(sum(i.quantity), 0) as total_stock
                from product p
                join category c on c.id = p.category_id
                left join product_variant v on v.product_id = p.id
                left join inventory i on i.variant_id = v.id
                where (:includeArchived or p.active)
                  and (cast(:pattern as text) is null
                       or lower(p.name) like :pattern or lower(p.brand) like :pattern
                       or exists (select 1 from product_variant sv where sv.product_id = p.id and lower(sv.sku) like :pattern))
                group by p.id, c.name
                order by p.active desc, p.name
                """)
                .param("includeArchived", includeArchived)
                .param("pattern", pattern)
                .query(AdminProductRow.class)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminProductView> get(long productId) {
        return jdbc.sql("""
                select p.id, p.slug, p.name, p.brand, c.slug as category, p.description, p.price,
                       p.attributes::text as attributes, p.image_url, p.active
                from product p join category c on c.id = p.category_id
                where p.id = :id
                """)
                .param("id", productId)
                .query((rs, n) -> new AdminProductView(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("brand"), rs.getString("category"), rs.getString("description"),
                        rs.getBigDecimal("price"), parse(rs.getString("attributes")), rs.getString("image_url"),
                        rs.getBoolean("active"), variants(productId)))
                .optional();
    }

    @Override
    public AdminProductView create(ProductForm form) {
        long id = jdbc.sql("""
                insert into product (slug, name, brand, description, category_id, price, attributes, image_url)
                values (:slug, :name, :brand, :description, :category, :price, cast(:attributes as jsonb), :image)
                returning id
                """)
                .param("slug", uniqueSlug(form.name()))
                .params(fields(form))
                .query(Long.class)
                .single();
        return changed(id);
    }

    @Override
    public AdminProductView update(long productId, ProductForm form) {
        requireProduct(productId);
        jdbc.sql("""
                update product set name = :name, brand = :brand, description = :description, category_id = :category,
                    price = :price, attributes = cast(:attributes as jsonb), image_url = :image, updated_at = now()
                where id = :id
                """)
                .params(fields(form))
                .param("id", productId)
                .update();
        return changed(productId);
    }

    @Override
    public AdminProductView setActive(long productId, boolean active) {
        requireProduct(productId);
        jdbc.sql("update product set active = :active, updated_at = now() where id = :id")
                .param("active", active).param("id", productId).update();
        return changed(productId);
    }

    @Override
    public AdminProductView addVariant(long productId, VariantForm form, int initialStock) {
        requireProduct(productId);
        checkStock(initialStock);
        long variantId;
        try {
            variantId = jdbc.sql("""
                    insert into product_variant (product_id, sku, size, color, price_override)
                    values (:product, :sku, :size, :color, :price) returning id
                    """)
                    .param("product", productId)
                    .params(variantFields(form))
                    .query(Long.class)
                    .single();
        }
        catch (DuplicateKeyException e) {
            throw duplicateSku(form.sku());
        }
        jdbc.sql("insert into inventory (variant_id, quantity) values (:id, :q)")
                .param("id", variantId).param("q", initialStock).update();
        return changed(productId);
    }

    @Override
    public AdminProductView updateVariant(long variantId, VariantForm form) {
        long productId = productOf(variantId);
        try {
            jdbc.sql("update product_variant set sku = :sku, size = :size, color = :color, price_override = :price where id = :id")
                    .params(variantFields(form))
                    .param("id", variantId)
                    .update();
        }
        catch (DuplicateKeyException e) {
            throw duplicateSku(form.sku());
        }
        return changed(productId);
    }

    @Override
    public AdminProductView setStock(long variantId, int quantity) {
        checkStock(quantity);
        long productId = productOf(variantId);
        jdbc.sql("""
                insert into inventory (variant_id, quantity) values (:id, :q)
                on conflict (variant_id) do update set quantity = excluded.quantity
                """)
                .param("id", variantId).param("q", quantity).update();
        return changed(productId);
    }

    @Override
    public ImportOutcome importLine(CatalogImportLine line) {
        var form = new ProductForm(line.productName(), line.brand(),
                line.description() == null || line.description().isBlank() ? line.productName() : line.description(),
                line.category(), line.price(), null, null);
        var existing = jdbc.sql("select id from product where lower(name) = lower(:name) and lower(brand) = lower(:brand)")
                .param("name", line.productName().trim())
                .param("brand", line.brand().trim())
                .query(Long.class)
                .optional();
        boolean createdProduct = existing.isEmpty();
        long productId;
        if (createdProduct) {
            productId = jdbc.sql("""
                    insert into product (slug, name, brand, description, category_id, price, attributes, image_url)
                    values (:slug, :name, :brand, :description, :category, :price, cast(:attributes as jsonb), :image)
                    returning id
                    """)
                    .param("slug", uniqueSlug(form.name()))
                    .params(fields(form))
                    .query(Long.class)
                    .single();
        }
        else {
            productId = existing.get();
            var params = fields(form);
            jdbc.sql("""
                    update product set category_id = :category, price = :price, updated_at = now(),
                        description = case when :keepDescription then description else :description end
                    where id = :id
                    """)
                    .param("category", params.get("category"))
                    .param("price", params.get("price"))
                    .param("description", params.get("description"))
                    .param("keepDescription", line.description() == null || line.description().isBlank())
                    .param("id", productId)
                    .update();
        }

        var variant = new VariantForm(line.sku(), line.size(), line.color(), null);
        var variantId = jdbc.sql("select id, product_id from product_variant where sku = :sku")
                .param("sku", line.sku().trim().toUpperCase(Locale.ROOT))
                .query((rs, n) -> new long[] { rs.getLong("id"), rs.getLong("product_id") })
                .optional();
        ImportOutcome outcome;
        if (variantId.isPresent()) {
            if (variantId.get()[1] != productId) {
                throw new CatalogAdminException(Reason.DUPLICATE_SKU,
                        "SKU " + line.sku() + " already belongs to another product.");
            }
            updateVariant(variantId.get()[0], variant);
            setStock(variantId.get()[0], line.stock());
            outcome = ImportOutcome.UPDATED_VARIANT;
        }
        else {
            addVariant(productId, variant, line.stock());
            outcome = createdProduct ? ImportOutcome.CREATED_PRODUCT : ImportOutcome.ADDED_VARIANT;
        }
        return outcome;
    }

    private AdminProductView changed(long productId) {
        events.publishEvent(new ProductUpdated(productId));
        return get(productId).orElseThrow();
    }

    private Map<String, Object> fields(ProductForm form) {
        var category = jdbc.sql("select id from category where slug = :slug").param("slug", form.category())
                .query(Long.class).optional()
                .orElseThrow(() -> new CatalogAdminException(Reason.UNKNOWN_CATEGORY, "Unknown category " + form.category() + "."));
        var params = new HashMap<String, Object>();
        params.put("name", form.name().trim());
        params.put("brand", form.brand().trim());
        params.put("description", form.description().trim());
        params.put("category", category);
        params.put("price", form.price());
        params.put("attributes", JSON.writeValueAsString(form.attributes() == null ? Map.of() : form.attributes()));
        params.put("image", form.imageUrl() == null || form.imageUrl().isBlank() ? null : form.imageUrl().trim());
        return params;
    }

    private static Map<String, Object> variantFields(VariantForm form) {
        var params = new HashMap<String, Object>();
        params.put("sku", form.sku().trim().toUpperCase(Locale.ROOT));
        params.put("size", blankToNull(form.size()));
        params.put("color", blankToNull(form.color()));
        params.put("price", form.priceOverride());
        return params;
    }

    private List<AdminProductView.Variant> variants(long productId) {
        return jdbc.sql("""
                select v.id, v.sku, v.size, v.color, v.price_override, coalesce(i.quantity, 0) as stock
                from product_variant v left join inventory i on i.variant_id = v.id
                where v.product_id = :id order by v.id
                """)
                .param("id", productId)
                .query(AdminProductView.Variant.class)
                .list();
    }

    private void requireProduct(long productId) {
        if (jdbc.sql("select count(*) from product where id = :id").param("id", productId).query(Integer.class).single() == 0) {
            throw new CatalogAdminException(Reason.NOT_FOUND, "Product not found.");
        }
    }

    private long productOf(long variantId) {
        return jdbc.sql("select product_id from product_variant where id = :id").param("id", variantId)
                .query(Long.class).optional()
                .orElseThrow(() -> new CatalogAdminException(Reason.NOT_FOUND, "Variant not found."));
    }

    private String uniqueSlug(String name) {
        var base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "product";
        }
        var slug = base;
        for (int i = 2; jdbc.sql("select count(*) from product where slug = :slug").param("slug", slug)
                .query(Integer.class).single() > 0; i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private static void checkStock(int quantity) {
        if (quantity < 0) {
            throw new CatalogAdminException(Reason.INVALID_STOCK, "Stock can't be negative.");
        }
    }

    private static CatalogAdminException duplicateSku(String sku) {
        return new CatalogAdminException(Reason.DUPLICATE_SKU, "SKU " + sku + " is already used.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return json == null ? Map.of() : JSON.readValue(json, Map.class);
    }

    private static @Nullable String blankToNull(@Nullable String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
