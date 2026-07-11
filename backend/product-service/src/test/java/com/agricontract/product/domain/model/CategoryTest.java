package com.agricontract.product.domain.model;

import com.agricontract.product.domain.event.CategoryApprovedEvent;
import com.agricontract.product.domain.event.CategoryRejectedEvent;
import com.agricontract.product.domain.event.DomainEvent;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CategoryTest {

    private static final CategoryId CATEGORY_ID = new CategoryId("category-1");
    private static final String NAME = "Ca Phe";
    private static final String NORMALIZED_NAME = "ca phe";
    private static final String PROPOSED_BY = "seller-1";
    private static final String PROPOSED_BY_EMAIL = "seller@test.com";

    private Category pending() {
        return Category.propose(CATEGORY_ID, NAME, NORMALIZED_NAME, PROPOSED_BY, PROPOSED_BY_EMAIL);
    }

    @Test
    void propose_happyPath_statusPendingNoEvent() {
        Category category = pending();

        assertThat(category.getStatus()).isEqualTo(CategoryStatus.PENDING);
        assertThat(category.getName()).isEqualTo(NAME);
        assertThat(category.getNormalizedName()).isEqualTo(NORMALIZED_NAME);
        assertThat(category.getProposedBy()).isEqualTo(PROPOSED_BY);
        assertThat(category.pullDomainEvents()).isEmpty();
    }

    @Test
    void propose_blankName_throws() {
        assertThatThrownBy(() -> Category.propose(CATEGORY_ID, " ", NORMALIZED_NAME, PROPOSED_BY, PROPOSED_BY_EMAIL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approve_fromPending_statusApprovedAndEventEmitted() {
        Category category = pending();

        category.approve();

        assertThat(category.getStatus()).isEqualTo(CategoryStatus.APPROVED);
        List<DomainEvent> events = category.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CategoryApprovedEvent.class);
        CategoryApprovedEvent event = (CategoryApprovedEvent) events.get(0);
        assertThat(event.getCategoryId()).isEqualTo(CATEGORY_ID.value());
        assertThat(event.getProposedByEmail()).isEqualTo(PROPOSED_BY_EMAIL);
    }

    @Test
    void approve_notPending_throws() {
        Category category = pending();
        category.approve();

        assertThatThrownBy(category::approve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reject_fromPending_statusRejectedAndEventEmitted() {
        Category category = pending();

        category.reject("duplicate meaning");

        assertThat(category.getStatus()).isEqualTo(CategoryStatus.REJECTED);
        assertThat(category.getRejectionReason()).isEqualTo("duplicate meaning");
        List<DomainEvent> events = category.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(CategoryRejectedEvent.class);
        CategoryRejectedEvent event = (CategoryRejectedEvent) events.get(0);
        assertThat(event.getRejectionReason()).isEqualTo("duplicate meaning");
    }

    @Test
    void reject_blankReason_throws() {
        Category category = pending();

        assertThatThrownBy(() -> category.reject(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reject_notPending_throws() {
        Category category = pending();
        category.reject("bad name");

        assertThatThrownBy(() -> category.reject("again")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pullDomainEvents_clearsInternalList() {
        Category category = pending();
        category.approve();

        category.pullDomainEvents();

        assertThat(category.pullDomainEvents()).isEmpty();
    }
}
