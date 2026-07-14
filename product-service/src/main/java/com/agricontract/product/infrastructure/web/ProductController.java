package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.CreateProductRequest;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ProductQueryRequest;
import com.agricontract.product.application.dto.ProductResponse;
import com.agricontract.product.application.dto.UpdateProductImagesRequest;
import com.agricontract.product.application.usecase.CreateProductUseCase;
import com.agricontract.product.application.usecase.ListProductsUseCase;
import com.agricontract.product.application.usecase.UpdateProductImagesUseCase;
import com.agricontract.product.common.ApiResponse;
import com.agricontract.product.common.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final UpdateProductImagesUseCase updateProductImagesUseCase;

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestBody @Valid CreateProductRequest request) {
        ProductResponse response = createProductUseCase.execute(request);
        return ResponseEntity.status(201).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<ProductResponse>>> getProducts(
            @ModelAttribute @Valid ProductQueryRequest request) {
        Pageable pageable = request.toPageable();
        PagedResult<ProductResponse> result = listProductsUseCase.execute(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PaginatedResponse.from(result, pageable)));
    }

    @PreAuthorize("hasRole('SELLER')")
    @PutMapping("/{productId}/images")
    public ResponseEntity<ApiResponse<ProductResponse>> updateImages(
            @PathVariable String productId,
            @RequestBody @Valid UpdateProductImagesRequest request) {
        ProductResponse response = updateProductImagesUseCase.execute(productId, request.imageUrls());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
