package com.agricontract.reputation.infrastructure.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayConfigurationTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t"})
    void missingConfiguredTrustSecretPreventsStartup(String secret) {
        assertThatThrownBy(() -> new GatewayIdentityFilter(secret)).isInstanceOf(IllegalArgumentException.class);
    }
}
