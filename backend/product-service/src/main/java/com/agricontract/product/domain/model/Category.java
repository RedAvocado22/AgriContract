package com.agricontract.product.domain.model;

import com.agricontract.product.domain.event.CategoryApprovedEvent;
import com.agricontract.product.domain.event.CategoryRejectedEvent;
import com.agricontract.product.domain.event.DomainEvent;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CategoryStatus;
import com.agricontract.product.domain.model.vo.Commodity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

// Aggregate Root
// State machine: PENDING → APPROVED | REJECTED
@Getter
public class Category {

    private CategoryId categoryId;
    private String name;
    private String normalizedName;
    private CategoryStatus status;
    private String rejectionReason;
    private String proposedBy;
    private String proposedByEmail;
    // set by the Operator on approval; null for categories approved before V7 until assignCommodity() maps them
    private Commodity commodity;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Category() {
    }

    public static Category reconstruct(CategoryId categoryId, String name, String normalizedName,
                                        CategoryStatus status, String rejectionReason,
                                        String proposedBy, String proposedByEmail, Commodity commodity) {
        Category category = new Category();
        category.categoryId = categoryId;
        category.name = name;
        category.normalizedName = normalizedName;
        category.status = status;
        category.rejectionReason = rejectionReason;
        category.proposedBy = proposedBy;
        category.proposedByEmail = proposedByEmail;
        category.commodity = commodity;
        return category;
    }

    public static Category propose(CategoryId categoryId, String name, String normalizedName,
                                    String proposedBy, String proposedByEmail) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        if (normalizedName == null || normalizedName.isBlank()) {
            throw new IllegalArgumentException("Category normalizedName must not be blank");
        }
        Category category = new Category();
        category.categoryId = categoryId;
        category.name = name;
        category.normalizedName = normalizedName;
        category.status = CategoryStatus.PENDING;
        category.proposedBy = proposedBy;
        category.proposedByEmail = proposedByEmail;
        return category;
    }

    // The Operator maps the category to its commodity at approval (design §9): the single, one-time mapping point.
    public void approve(Commodity commodity) {
        if (this.status != CategoryStatus.PENDING) {
            throw new IllegalStateException("Category status must be PENDING");
        }
        requireCommodity(commodity);
        this.status = CategoryStatus.APPROVED;
        this.commodity = commodity;
        this.domainEvents.add(new CategoryApprovedEvent(this.categoryId.value(), this.name, this.proposedByEmail));
    }

    // Maps a category approved before V7 added the commodity column. Still one-time: a category that already has a
    // commodity cannot be remapped, since products and listings have snapshotted it.
    public void assignCommodity(Commodity commodity) {
        if (this.status != CategoryStatus.APPROVED) {
            throw new IllegalStateException("Category status must be APPROVED");
        }
        if (this.commodity != null) {
            throw new IllegalStateException("Category commodity is already assigned");
        }
        requireCommodity(commodity);
        this.commodity = commodity;
    }

    public void reject(String reason) {
        if (this.status != CategoryStatus.PENDING) {
            throw new IllegalStateException("Category status must be PENDING");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }
        this.status = CategoryStatus.REJECTED;
        this.rejectionReason = reason;
        this.domainEvents.add(new CategoryRejectedEvent(this.categoryId.value(), this.name, this.proposedByEmail, reason));
    }

    // New products and listings need an approved coffee category (design §1, matrix 22).
    public boolean isApprovedCoffee() {
        return status == CategoryStatus.APPROVED && commodity == Commodity.COFFEE;
    }

    private static void requireCommodity(Commodity commodity) {
        if (commodity == null) {
            throw new IllegalArgumentException("Category commodity must not be null");
        }
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
