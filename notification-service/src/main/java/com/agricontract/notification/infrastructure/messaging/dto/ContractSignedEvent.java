package com.agricontract.notification.infrastructure.messaging.dto;

import lombok.Getter;


@Getter
public class ContractSignedEvent extends BaseNotificationEvent {
    private String contractId;
    private String buyerId;
    private String sellerId;
    private String listingId;
    private ContractTermsDto terms;
}
