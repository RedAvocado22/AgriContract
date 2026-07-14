package com.agricontract.notification.application.dto;

public record CategoryRejectedCommand(
        String eventId, String categoryId, String name, String proposedByEmail, String rejectionReason) {}
