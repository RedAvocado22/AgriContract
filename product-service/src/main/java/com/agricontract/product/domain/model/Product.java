package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ProductId;
import lombok.Getter;

// Aggregate Root
@Getter
public class Product {

    private ProductId productId;
    private String name;
    private String unit;
    private String category;

    private Product() {
    }

    public static Product reconstruct(ProductId productId, String name, String unit, String category) {
        Product product = new Product();
        product.productId = productId;
        product.name = name;
        product.unit = unit;
        product.category = category;

        return product;

    }

    public static Product create(ProductId productId, String name, String unit, String category) {
        Product product = new Product();
        product.productId = productId;
        product.name = name;
        product.unit = unit;
        product.category = category;

        return product;
    }

    public void updateDetails(String name, String unit, String category) {
        if (this.productId == null) {
            throw new IllegalArgumentException("Product id is null");
        }
        this.name = name;
        this.unit = unit;
        this.category = category;
    }
}
