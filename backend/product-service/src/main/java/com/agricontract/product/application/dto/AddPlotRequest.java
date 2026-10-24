package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.Province;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Manual pin fallback (design §4 AddPlotManually): one POINT from the browser Geolocation API.
public record AddPlotRequest(
        @NotBlank @Size(max = 255) String householdLabel,
        @NotNull Double latitude,
        @NotNull Double longitude,
        // a point carries no area; > 4 ha needs a KML polygon
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal areaHectares,
        // coords.accuracy in meters; stored for a UI warning, never a block
        @DecimalMin("0") BigDecimal accuracyMeters,
        @NotNull Province declaredProvince
) {
    // geometryType and source are decided by the server, never sent by the client
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property: " + name);
    }
}
