package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class CategoryNotFoundException extends ApiException {
    public CategoryNotFoundException(String categoryId) {
        super(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found with id: " + categoryId);
    }
}
