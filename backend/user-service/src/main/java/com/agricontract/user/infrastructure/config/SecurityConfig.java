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
                        // Exactly one staff role per function, no ADMIN fallback (governance §5.2/§5.4).
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/users/*/role").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users").hasRole("OPERATOR")
                        // Full KYC profile: an audited read (#155)
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/*").hasRole("OPERATOR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/admin/users/*/verify", "/api/v1/admin/users/*/reject").hasRole("OPERATOR")
                        .requestMatchers("/api/v1/admin/**").denyAll()
                        // Profile reads need an end-user principal (design §4.1): /me is the owner's own profile, and
                        // the public DTO is for logged-in users. Service-to-service reads go to /internal/v1/users/{userId}.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> HeaderAuthenticationFilter.writeForbidden(response))
                        .accessDeniedHandler((request, response, e) -> HeaderAuthenticationFilter.writeForbidden(response))
                )
                .build();
    }
}
