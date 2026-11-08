package com.agricontract.escrow.application.dto;

import java.util.List;

/** contract.activation_refund_requested, parsed: the parties stopped a contract whose deposits could not be locked. */
public record ActivationRefundCommand(String eventId, String correlationId, String contractId, List<String> requestedBy,
                                      String trigger) {
}
