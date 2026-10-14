package com.agricontract.reputation.infrastructure.config;
import com.agricontract.reputation.infrastructure.security.GatewayIdentityFilter;
import com.agricontract.reputation.infrastructure.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper json,
                                  @Value("${gateway.internal-secret}") String secret) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        // Only container ERROR dispatches to the error renderer bypass re-authentication.
                        // Ordinary requests/forwards to /error and all business routes retain their rules.
                        .requestMatchers(new AndRequestMatcher(new DispatcherTypeRequestMatcher(DispatcherType.ERROR),
                                new AntPathRequestMatcher("/error"))).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reputation/*/public-summary").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), ErrorResponse.of(request, 401, "Unauthorized", "UNAUTHORIZED"));
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), ErrorResponse.of(request, 403, "Forbidden", "FORBIDDEN"));
                }))
                .addFilterBefore(new GatewayIdentityFilter(secret), UsernamePasswordAuthenticationFilter.class).build();
    }
}
