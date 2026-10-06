package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ProductId;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;

// Aggregate Root
@Getter
public class Product {

    private static final int MIN_IMAGES = 1;
    private static final int MAX_IMAGES = 5;

    private ProductId productId;
    private String name;
    private String unit;
    private String categoryId;
    private List<String> images;

    private Product() {
    }

    public static Product reconstruct(ProductId productId, String name, String unit,
                                       String categoryId, List<String> images) {
        Product product = new Product();
        product.productId = productId;
        product.name = name;
        product.unit = unit;
        product.categoryId = categoryId;
        product.images = images;
        return product;
    }

    public static Product create(ProductId productId, String name, String unit,
                                  String categoryId, List<String> images) {
        validateImages(images);
        Product product = new Product();
        product.productId = productId;
        product.name = name;
        product.unit = unit;
        product.categoryId = categoryId;
        product.images = images;
        return product;
    }

    public void updateDetails(String name, String unit, String categoryId) {
        if (this.productId == null) {
            throw new IllegalArgumentException("Product id is null");
        }
        this.name = name;
        this.unit = unit;
        this.categoryId = categoryId;
    }

    /**
     * Images have no identity — full replace only, no add/remove of individual entries.
     */
    public void updateImages(List<String> images) {
        validateImages(images);
        this.images = images;
    }

    public String getCoverImageUrl() {
        return this.images.get(0);
    }

    private static void validateImages(List<String> images) {
        if (images == null || images.size() < MIN_IMAGES) {
            throw new IllegalArgumentException("Product must have at least " + MIN_IMAGES + " image");
        }
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Product must have at most " + MAX_IMAGES + " images");
        }
        if (new LinkedHashSet<>(images).size() != images.size()) {
            throw new IllegalArgumentException("Product images must not contain duplicates");
        }
        for (String url : images) {
            if (!isValidUrl(url)) {
                throw new IllegalArgumentException("Invalid image URL: " + url);
            }
        }
    }

    private static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
