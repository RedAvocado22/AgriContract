package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends ApiException {
    public ProductNotFoundException(String productId) {
        super(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found with id: " + productId);
    }
}
