package com.agricontract.contract.infrastructure.feign;

import com.agricontract.contract.application.dto.ListingReservation;
import com.agricontract.contract.application.dto.ListingResponse;
import com.agricontract.contract.application.exception.ListingDataInvalidException;
import com.agricontract.contract.application.exception.ListingNotFoundException;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.agricontract.contract.application.port.ListingPort;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.agricontract.contract.domain.model.terms.CoffeeGoodsTerms;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.codec.DecodeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class ListingPortAdapter implements ListingPort {
    static final String COMPENSATION_REASON = "CREATE_PERSISTENCE_COMPENSATION";

    private final ProductServiceClient productServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public ListingResponse getListing(String listingId) {
        try {
            return productServiceClient.getListing(listingId);
        } catch (DecodeException unreadable) {
            throw invalid("listing " + listingId, unreadable);
        } catch (FeignException.NotFound missing) {
            throw new ListingNotFoundException(listingId);
        } catch (FeignException failed) {
            throw unavailable("read listing " + listingId, failed);
        }
    }

    @Override
    public void closeListing(String listingId) {
        productServiceClient.closeListing(listingId);
    }

    @Override
    public CoffeeGoodsTerms contractSnapshot(String listingId, String listingVersionToken) {
        try {
            return productServiceClient.getContractSnapshot(listingId, listingVersionToken);
        } catch (DecodeException unreadable) {
            throw invalid("contract snapshot of listing " + listingId, unreadable);
        } catch (FeignException.NotFound missing) {
            throw new ListingNotFoundException(listingId);
        } catch (FeignException.Conflict conflict) {
            throw conflict(conflict);
        } catch (FeignException failed) {
            throw unavailable("read the contract snapshot of listing " + listingId, failed);
        }
    }

    @Override
    public ListingReservation reserve(String contractId, String listingId, BigDecimal requestedQuantity,
                                      String listingVersionToken) {
        try {
            return productServiceClient.reserve(new ProductServiceClient.ReserveRequest(contractId, listingId,
                    requestedQuantity, listingVersionToken));
        } catch (DecodeException unreadable) {
            // The reserve may have committed: CreateContractUseCase gives it back like any unknown outcome.
            throw invalid("reservation of listing " + listingId + " for contract " + contractId, unreadable);
        } catch (FeignException.NotFound missing) {
            throw new ListingNotFoundException(listingId);
        } catch (FeignException.Conflict conflict) {
            throw conflict(conflict);
        } catch (FeignException failed) {
            throw unavailable("reserve listing " + listingId + " for contract " + contractId, failed);
        }
    }

    @Override
    public void releaseReservation(String contractId, String listingId, BigDecimal reservedQuantity) {
        try {
            productServiceClient.release(contractId, new ProductServiceClient.ReleaseRequest(listingId,
                    reservedQuantity, COMPENSATION_REASON));
        } catch (FeignException.NotFound neverReserved) {
            // Nothing to give back: the reserve never committed on product-service's side.
        } catch (FeignException.Conflict conflict) {
            throw conflict(conflict);
        } catch (FeignException failed) {
            throw unavailable("release the reservation of contract " + contractId, failed);
        }
    }

    /** product-service answers 409 with the shared error envelope; its code is what the client branches on. */
    private ContractConflictException conflict(FeignException.Conflict conflict) {
        String code = "LISTING_STATE_CONFLICT";
        String message = "The listing cannot back this contract right now; reload it.";
        try {
            JsonNode body = objectMapper.readTree(conflict.contentUTF8());
            if (body != null && body.hasNonNull("code")) {
                code = body.get("code").asText();
            }
            if (body != null && body.hasNonNull("message")) {
                message = body.get("message").asText();
            }
        } catch (Exception unreadable) {
            // Keep the generic state conflict: the 409 itself is authoritative.
        }
        return new ContractConflictException(code, message);
    }

    /**
     * product-service answered (DecodeException carries its HTTP status) but the body breaks the frozen schema, e.g.
     * a categoryId that is not a UUID. Retrying returns the same body, so this is not "unavailable".
     */
    private static ListingDataInvalidException invalid(String what, DecodeException unreadable) {
        return new ListingDataInvalidException("product-service sent an unreadable " + what + " (HTTP "
                + unreadable.status() + ", field " + badField(unreadable) + ")", unreadable);
    }

    /** The JSON path Jackson stopped at, e.g. {@code CoffeeGoodsTerms["categoryId"]}; "unknown" if none. */
    private static String badField(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
                return mapping.getPathReference();
            }
        }
        return "unknown";
    }

    private static ListingServiceUnavailableException unavailable(String action, FeignException failed) {
        return new ListingServiceUnavailableException("product-service could not " + action
                + " (HTTP " + failed.status() + ")", failed);
    }
}
