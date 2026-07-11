package com.agricontract.product.domain.model;

import com.agricontract.product.domain.event.CategoryApprovedEvent;
import com.agricontract.product.domain.event.CategoryRejectedEvent;
import com.agricontract.product.domain.event.DomainEvent;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CategoryStatus;
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

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Category() {
    }

    public static Category reconstruct(CategoryId categoryId, String name, String normalizedName,
                                        CategoryStatus status, String rejectionReason,
                                        String proposedBy, String proposedByEmail) {
        Category category = new Category();
        category.categoryId = categoryId;
        category.name = name;
        category.normalizedName = normalizedName;
        category.status = status;
        category.rejectionReason = rejectionReason;
        category.proposedBy = proposedBy;
        category.proposedByEmail = proposedByEmail;
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

    public void approve() {
        if (this.status != CategoryStatus.PENDING) {
            throw new IllegalStateException("Category status must be PENDING");
        }
        this.status = CategoryStatus.APPROVED;
        this.domainEvents.add(new CategoryApprovedEvent(this.categoryId.value(), this.name, this.proposedByEmail));
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

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }
}
