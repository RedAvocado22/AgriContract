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

    private Product() {}

    public static Product create(ProductId productId, String name, String unit, String category) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    public void updateDetails(String name, String unit, String category) { /* TODO */ }
}
