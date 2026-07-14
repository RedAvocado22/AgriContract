package com.agricontract.product.domain.model;

import com.agricontract.product.domain.model.vo.ProductId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    private static final ProductId PRODUCT_ID = new ProductId("product-1");

    private Product create(List<String> images) {
        return Product.create(PRODUCT_ID, "Gao ST25", "kg", "category-1", images);
    }

    @Test
    void create_happyPath_setsFieldsAndCoverImage() {
        Product product = create(List.of("https://cdn.test/a.jpg", "https://cdn.test/b.jpg"));

        assertThat(product.getName()).isEqualTo("Gao ST25");
        assertThat(product.getCategoryId()).isEqualTo("category-1");
        assertThat(product.getImages()).hasSize(2);
        assertThat(product.getCoverImageUrl()).isEqualTo("https://cdn.test/a.jpg");
    }

    @Test
    void create_noImages_throws() {
        assertThatThrownBy(() -> create(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_nullImages_throws() {
        assertThatThrownBy(() -> create(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_moreThanFiveImages_throws() {
        List<String> images = List.of(
                "https://cdn.test/1.jpg", "https://cdn.test/2.jpg", "https://cdn.test/3.jpg",
                "https://cdn.test/4.jpg", "https://cdn.test/5.jpg", "https://cdn.test/6.jpg");

        assertThatThrownBy(() -> create(images)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_duplicateImages_throws() {
        List<String> images = List.of("https://cdn.test/a.jpg", "https://cdn.test/a.jpg");

        assertThatThrownBy(() -> create(images)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_invalidUrl_throws() {
        assertThatThrownBy(() -> create(List.of("not-a-url"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_nonHttpScheme_throws() {
        assertThatThrownBy(() -> create(List.of("ftp://cdn.test/a.jpg"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateImages_fullReplace_overwritesAll() {
        Product product = create(List.of("https://cdn.test/a.jpg"));

        product.updateImages(List.of("https://cdn.test/x.jpg", "https://cdn.test/y.jpg"));

        assertThat(product.getImages()).containsExactly("https://cdn.test/x.jpg", "https://cdn.test/y.jpg");
        assertThat(product.getCoverImageUrl()).isEqualTo("https://cdn.test/x.jpg");
    }

    @Test
    void updateImages_invalid_throwsAndKeepsOldState() {
        Product product = create(List.of("https://cdn.test/a.jpg"));

        assertThatThrownBy(() -> product.updateImages(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getImages()).containsExactly("https://cdn.test/a.jpg");
    }
}
