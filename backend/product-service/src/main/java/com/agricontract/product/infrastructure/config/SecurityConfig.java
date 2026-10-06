package com.agricontract.product.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Value("${service.internal-secret}")
    private String serviceInternalSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new HeaderAuthenticationFilter(gatewaySecret, serviceInternalSecret),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/listings",
                                "/api/v1/listings/{listingId}",
                                "/api/v1/products/{productId}").permitAll()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/listings/{listingId}/close").permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }
}
