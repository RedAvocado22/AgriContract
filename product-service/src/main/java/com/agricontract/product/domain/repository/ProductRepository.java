package com.agricontract.product.domain.repository;

import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(ProductId productId);

    Page<Product> findAll(Pageable pageable);
}
