package com.agricontract.notification.application.dto;

public record CategoryApprovedCommand(String eventId, String categoryId, String name, String proposedByEmail) {}
