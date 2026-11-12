package com.shopwise.catalog.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
class Product {

    @Id
    private Long id;

    private String slug;

    private String name;

    private String brand;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    private BigDecimal price;

    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> attributes;

    private String imageUrl;

    private boolean active = true;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
    }

    Long getId() {
        return id;
    }

    String getSlug() {
        return slug;
    }

    String getName() {
        return name;
    }

    String getBrand() {
        return brand;
    }

    String getDescription() {
        return description;
    }

    Category getCategory() {
        return category;
    }

    BigDecimal getPrice() {
        return price;
    }

    String getCurrency() {
        return currency;
    }

    Map<String, Object> getAttributes() {
        return attributes == null ? Map.of() : attributes;
    }

    boolean isActive() {
        return active;
    }

    String getImageUrl() {
        return imageUrl;
    }

    List<ProductVariant> getVariants() {
        return variants;
    }
}
