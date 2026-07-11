package com.agricontract.product.common.exception;

public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException(String name) {
        super("Category already proposed/exists with an equivalent name: " + name);
    }
}
