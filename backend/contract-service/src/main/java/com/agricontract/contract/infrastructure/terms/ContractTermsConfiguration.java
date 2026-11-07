package com.agricontract.contract.infrastructure.terms;

import com.agricontract.contract.domain.model.terms.ContractTemplateProfile;
import com.agricontract.contract.domain.model.terms.ContractTermsRules;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(ContractTemplateProperties.class)
public class ContractTermsConfiguration {

    @Bean
    ContractTermsRules contractTermsRules(ContractTemplateProperties properties) {
        return new ContractTermsRules(properties.level2SafetyBufferRate());
    }

    /**
     * The template fingerprint is computed from the template file at startup, never typed into config:
     * replacing the file changes contractTemplateHash and therefore every new signedContentHash.
     */
    @Bean
    ContractTemplateProfile currentContractTemplate(ContractTemplateProperties properties,
                                                    ContractTermsRules rules) throws IOException {
        String text;
        try (InputStream in = properties.templateResource().getInputStream()) {
            // Normalise line endings so a Windows checkout produces the same fingerprint.
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        ContractTemplateProfile profile = new ContractTemplateProfile(properties.templateCode(),
                properties.templateVersion(), CanonicalTerms.sha256(text), properties.coffeeQualityProfileVersion());
        rules.validateTemplate(profile);
        return profile;
    }
}
