package com.agricontract.inspection.infrastructure.config;

import com.agricontract.inspection.infrastructure.security.GatewayAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.time.Clock;

@Configuration
public class InspectionConfiguration {
    @Bean
    Clock inspectionClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain inspectionSecurity(HttpSecurity http,
            @Value("${gateway.internal-secret}") String gatewaySecret) throws Exception {
        // Report submission and assignment creation transports are outside issue #112.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .addFilterBefore(new GatewayAuthenticationFilter(gatewaySecret), AnonymousAuthenticationFilter.class)
                .build();
    }
}
