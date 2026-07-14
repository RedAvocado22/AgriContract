package com.agricontract.product.common.exception;

public class CategoryNotApprovedException extends RuntimeException {
    public CategoryNotApprovedException(String categoryId) {
        super("Category is not APPROVED: " + categoryId);
    }
}
