package com.agricontract.contract.infrastructure.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agricontract.contract.application.exception.ListingNotFoundException;
import com.agricontract.contract.application.exception.ListingServiceUnavailableException;
import com.agricontract.contract.domain.exception.ContractConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** product-service answers are sorted into 409 (its code), 404 and fail-closed 503. */
class ListingPortAdapterTest {
    private static final String CONTRACT = "8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String LISTING = "11111111-1111-1111-1111-111111111111";
    private static final BigDecimal QTY = new BigDecimal("2500.5");

    private final ProductServiceClient client = mock(ProductServiceClient.class);
    private final ListingPortAdapter adapter = new ListingPortAdapter(client, new ObjectMapper());

    @Test
    void reserveConflict_carriesProductServiceCode() {
        when(client.reserve(any())).thenThrow(error(409,
                "{\"status\":409,\"code\":\"INSUFFICIENT_LISTING_QUANTITY\",\"message\":\"Only 100 kg left\"}"));

        assertThatThrownBy(() -> adapter.reserve(CONTRACT, LISTING, QTY, "t"))
                .isInstanceOfSatisfying(ContractConflictException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INSUFFICIENT_LISTING_QUANTITY");
                    assertThat(e.getMessage()).isEqualTo("Only 100 kg left");
                });
    }

    @Test
    void reserveSendsTheDerivedQuantityAndToken() {
        adapter.reserve(CONTRACT, LISTING, QTY, "t");

        verify(client).reserve(new ProductServiceClient.ReserveRequest(CONTRACT, LISTING, QTY, "t"));
    }

    @Test
    void snapshotVersionMismatch_isA409() {
        when(client.getContractSnapshot(LISTING, "old")).thenThrow(error(409,
                "{\"code\":\"LISTING_VERSION_MISMATCH\",\"message\":\"stale\"}"));

        assertThatThrownBy(() -> adapter.contractSnapshot(LISTING, "old"))
                .isInstanceOfSatisfying(ContractConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LISTING_VERSION_MISMATCH"));
    }

    @Test
    void unreadableConflictBody_isStillA409() {
        when(client.reserve(any())).thenThrow(error(409, "not json"));

        assertThatThrownBy(() -> adapter.reserve(CONTRACT, LISTING, QTY, "t"))
                .isInstanceOfSatisfying(ContractConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LISTING_STATE_CONFLICT"));
    }

    @Test
    void serverErrorOrNoAnswer_failsClosed() {
        when(client.reserve(any())).thenThrow(error(500, "{}"));
        when(client.getContractSnapshot(eq(LISTING), any())).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> adapter.reserve(CONTRACT, LISTING, QTY, "t"))
                .isInstanceOf(ListingServiceUnavailableException.class);
        assertThatThrownBy(() -> adapter.contractSnapshot(LISTING, "t"))
                .isInstanceOf(ListingServiceUnavailableException.class);
    }

    @Test
    void unknownListing_isNotFound() {
        when(client.getContractSnapshot(LISTING, "t")).thenThrow(error(404, "{}"));

        assertThatThrownBy(() -> adapter.contractSnapshot(LISTING, "t"))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void releaseOfANeverTakenReservation_isNothingToDo() {
        org.mockito.Mockito.doThrow(error(404, "{\"code\":\"RESERVATION_NOT_FOUND\"}"))
                .when(client).release(eq(CONTRACT), any());

        assertThatCode(() -> adapter.releaseReservation(CONTRACT, LISTING, QTY)).doesNotThrowAnyException();
    }

    @Test
    void releaseSendsTheCompensationReason() {
        adapter.releaseReservation(CONTRACT, LISTING, QTY);

        verify(client).release(CONTRACT, new ProductServiceClient.ReleaseRequest(LISTING, QTY,
                "CREATE_PERSISTENCE_COMPENSATION"));
    }

    private static FeignException error(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "http://product-service/internal", Map.of(),
                null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("ProductServiceClient", Response.builder()
                .status(status).reason("x").request(request).headers(Map.of())
                .body(body, StandardCharsets.UTF_8).build());
    }
}
