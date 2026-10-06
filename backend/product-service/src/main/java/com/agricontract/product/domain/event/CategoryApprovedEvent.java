package com.agricontract.product.domain.event;

import lombok.Getter;

@Getter
public class CategoryApprovedEvent extends DomainEvent {

    private final String categoryId;
    private final String name;
    private final String proposedByEmail;

    public CategoryApprovedEvent(String categoryId, String name, String proposedByEmail) {
        super();
        this.categoryId = categoryId;
        this.name = name;
        this.proposedByEmail = proposedByEmail;
    }

    @Override
    public String getEventType() {
        return "category.approved";
    }
}
