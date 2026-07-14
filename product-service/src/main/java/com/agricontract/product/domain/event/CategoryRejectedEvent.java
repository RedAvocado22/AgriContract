package com.agricontract.product.domain.event;

import lombok.Getter;

@Getter
public class CategoryRejectedEvent extends DomainEvent {

    private final String categoryId;
    private final String name;
    private final String proposedByEmail;
    private final String rejectionReason;

    public CategoryRejectedEvent(String categoryId, String name, String proposedByEmail, String rejectionReason) {
        super();
        this.categoryId = categoryId;
        this.name = name;
        this.proposedByEmail = proposedByEmail;
        this.rejectionReason = rejectionReason;
    }

    @Override
    public String getEventType() {
        return "category.rejected";
    }
}
