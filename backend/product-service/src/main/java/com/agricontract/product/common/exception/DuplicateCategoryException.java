package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class DuplicateCategoryException extends ApiException {
    public DuplicateCategoryException(String name) {
        super(HttpStatus.CONFLICT, "DUPLICATE_CATEGORY", "Category already proposed/exists with an equivalent name: " + name);
    }
}
