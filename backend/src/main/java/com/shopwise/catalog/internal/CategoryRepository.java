package com.shopwise.catalog.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByName();
}
