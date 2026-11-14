package com.agricontract.product.infrastructure.web;

import com.agricontract.product.application.dto.AddPlotRequest;
import com.agricontract.product.application.dto.PlotImportResult;
import com.agricontract.product.application.dto.PlotResponse;
import com.agricontract.product.application.dto.RegisteredPlot;
import com.agricontract.product.application.usecase.AddPlotManuallyUseCase;
import com.agricontract.product.application.usecase.ImportPlotsFromKmlUseCase;
import com.agricontract.product.domain.model.vo.Province;
import com.agricontract.web.error.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

/**
 * registerPlot (supplementary contract): one path, two inputs told apart by Content-Type —
 * a KML file (main path, design §2.2) or a JSON manual pin (POINT-only fallback).
 * Idempotency-Key is required by the contract; replaying the same request is already safe because both inputs
 * update the seller's entry for the same household in place instead of creating a second one.
 */
@RestController
@RequestMapping("/api/v1/plots")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class PlotController {

    static final String KML = "application/vnd.google-earth.kml+xml";
    static final int MAX_KML_BYTES = 5 * 1024 * 1024;

    private final ImportPlotsFromKmlUseCase importPlotsFromKmlUseCase;
    private final AddPlotManuallyUseCase addPlotManuallyUseCase;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlotResponse> addPlotManually(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid AddPlotRequest request,
            @AuthenticationPrincipal String sellerId) {
        RegisteredPlot result = addPlotManuallyUseCase.execute(sellerId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.plot());
    }

    /**
     * 201 when at least one household is new, 200 when every stored placemark updated an existing entry,
     * 400 when no placemark could be stored. Rejected placemarks are always listed by name.
     */
    @PostMapping(consumes = {KML, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<PlotImportResult> importKml(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam Province declaredProvince,
            InputStream body,
            @AuthenticationPrincipal String sellerId) throws IOException {
        PlotImportResult result = importPlotsFromKmlUseCase.execute(sellerId, readLimited(body), declaredProvince);
        if (result.created().isEmpty() && result.updated().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_PLACEMARK_IMPORTED", rejectionMessage(result));
        }
        return ResponseEntity.status(result.created().isEmpty() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    // An error is an ErrorEnvelope (D32), so the per-placemark reasons travel in its message.
    private static String rejectionMessage(PlotImportResult result) {
        String reasons = result.rejected().stream()
                .map(r -> r.placemark() + ": " + r.reason())
                .collect(Collectors.joining("; "));
        return reasons.isEmpty() ? "No placemark could be imported"
                : "No placemark could be imported. " + reasons;
    }

    // read at most MAX_KML_BYTES + 1 so an oversized upload is refused without buffering all of it
    private static byte[] readLimited(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_KML_BYTES + 1);
        if (bytes.length > MAX_KML_BYTES) {
            throw new IllegalArgumentException("KML file is larger than 5 MB; split it into several imports");
        }
        return bytes;
    }
}
