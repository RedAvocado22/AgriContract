package com.agricontract.user.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Value("${service.internal-secret}")
    private String serviceInternalSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new HeaderAuthenticationFilter(gatewaySecret, serviceInternalSecret),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Full KYC profile (audited read, #155): OPERATOR only, no ADMIN fallback (governance §5.4).
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/*").hasRole("OPERATOR")
                        // KYC decisions: OPERATOR is the day-to-day reviewer, ADMIN the fallback (governance §5).
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "OPERATOR")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> HeaderAuthenticationFilter.writeForbidden(response))
                        .accessDeniedHandler((request, response, e) -> HeaderAuthenticationFilter.writeForbidden(response))
                )
                .build();
    }
}
