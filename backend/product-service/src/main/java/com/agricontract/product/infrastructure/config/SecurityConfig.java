package com.agricontract.product.infrastructure.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;

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
                        // sendError (missing header, malformed body, unknown path) is re-dispatched to /error as an ERROR
                        // dispatch without the request's authentication; let only that through so the client gets the
                        // real status instead of an empty 403 (#424, #429). An ordinary request to /error stays denied.
                        .requestMatchers(new AndRequestMatcher(new DispatcherTypeRequestMatcher(DispatcherType.ERROR),
                                new AntPathRequestMatcher("/error"))).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/listings",
                                "/api/v1/listings/{listingId}",
                                "/api/v1/products/{productId}").permitAll()
                        // only reachable with a valid X-Internal-Secret, enforced by HeaderAuthenticationFilter
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }
}
