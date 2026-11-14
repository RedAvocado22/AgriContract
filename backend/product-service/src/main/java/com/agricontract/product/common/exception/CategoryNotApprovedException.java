package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class CategoryNotApprovedException extends ApiException {
    public CategoryNotApprovedException(String categoryId) {
        super(HttpStatus.BAD_REQUEST, "CATEGORY_NOT_APPROVED", "Category is not APPROVED: " + categoryId);
    }
}
