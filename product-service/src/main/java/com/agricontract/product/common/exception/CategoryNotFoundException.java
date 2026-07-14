package com.agricontract.product.common.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(String categoryId) {
        super("Category not found with id: " + categoryId);
    }
}
