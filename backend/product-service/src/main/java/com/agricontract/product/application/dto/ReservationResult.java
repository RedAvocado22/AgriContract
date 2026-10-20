package com.agricontract.product.application.dto;

/**
 * @param created false when an identical earlier reserve was replayed (200 instead of 201)
 */
public record ReservationResult(ListingReservationResponse reservation, boolean created) {}
