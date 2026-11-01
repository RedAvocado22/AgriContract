package com.agricontract.gateway.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    void allowlist_isParsedAsExactOriginsWithoutCredentials() {
        CorsConfiguration config = CorsConfig.corsConfiguration(" http://localhost:5173 , https://app.agri.vn ,");

        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:5173", "https://app.agri.vn");
        assertThat(config.getAllowCredentials()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "http://localhost:5173,*", "https://*.agri.vn"})
    void wildcardOrigin_isRefusedAtStartup(String origins) {
        assertThatThrownBy(() -> CorsConfig.corsConfiguration(origins)).isInstanceOf(IllegalStateException.class);
    }
}
