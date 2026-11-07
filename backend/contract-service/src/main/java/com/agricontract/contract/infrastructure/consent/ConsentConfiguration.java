package com.agricontract.contract.infrastructure.consent;

import com.agricontract.contract.domain.model.consent.ConsentStatement;
import com.agricontract.contract.domain.model.consent.ConsentStatements;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.terms.CanonicalTerms;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(ConsentStatementProperties.class)
public class ConsentConfiguration {

    @Bean
    ConsentStatements consentStatements(ConsentStatementProperties properties) throws IOException {
        Map<SignerRole, ConsentStatement> signContract = new EnumMap<>(SignerRole.class);
        for (SignerRole role : SignerRole.values()) {
            String code = ConsentStatements.signContractCode(role);
            String text = load("consent-statement/" + code + "-" + properties.signContractVersion() + ".txt");
            signContract.put(role, new ConsentStatement(code, properties.signContractVersion(), text,
                    CanonicalTerms.sha256(text)));
        }
        return new ConsentStatements(signContract);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The hashed text is the sentence itself: CRLF normalised and surrounding whitespace (file's final newline) removed. */
    static String load(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        }
    }
}
