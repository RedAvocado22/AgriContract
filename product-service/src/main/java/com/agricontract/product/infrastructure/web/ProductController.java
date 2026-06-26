package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.application.usecase.CreateProductUseCase;
import com.agricontract.product.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestBody @Valid CreateProductRequest request) {
        ProductResponse response = createProductUseCase.execute(request);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }
}
