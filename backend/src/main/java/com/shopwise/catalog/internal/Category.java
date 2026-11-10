package com.shopwise.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
class Category {

    @Id
    private Long id;

    private String slug;

    private String name;

    protected Category() {
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
}
