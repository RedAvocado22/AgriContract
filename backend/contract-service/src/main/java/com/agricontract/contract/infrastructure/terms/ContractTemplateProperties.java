package com.agricontract.contract.infrastructure.terms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "contract.terms")
public record ContractTemplateProperties(
        String templateCode,
        String templateVersion,
        Resource templateResource,
        String coffeeQualityProfileVersion,
        BigDecimal level2SafetyBufferRate) {
}
