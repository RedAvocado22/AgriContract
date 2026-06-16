package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ProductId;
import lombok.Getter;

import java.time.Instant;

// Aggregate Root
@Getter
public class Product {

    private ProductId productId;
    private String name;
    private String unit;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;

    private Product() {}

    public static Product create(String productId, String name, String unit, String category) {
        Product p = new Product();
        p.productId = new ProductId(productId);
        p.name = name;
        p.unit = unit;
        p.category = category;
        p.createdAt = p.updatedAt = Instant.now();
        return p;
    }

    public static Product reconstitute(String productId, String name, String unit, String category) {
        Product p = new Product();
        p.productId = new ProductId(productId);
        p.name = name;
        p.unit = unit;
        p.category = category;
        return p;
    }

    public void updateDetails(String name, String unit, String category) {
        this.name = name;
        this.unit = unit;
        this.category = category;
        this.updatedAt = Instant.now();
    }
}
