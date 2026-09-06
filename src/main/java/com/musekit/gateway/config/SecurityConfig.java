package com.musekit.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive Security configuration for Spring Cloud Gateway.
 *
 * Validates JWT tokens issued by ZITADEL at the gateway level.
 * Public routes (browsing songs, swagger docs, actuator) pass freely.
 * Protected routes (playlists, users, uploading songs) require a valid token.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:https://musekit-oua2yk.us1.zitadel.cloud/oauth/v2/keys}")
    private String jwkSetUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Disable CSRF for stateless REST API Gateway
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Route-level authorization rules
                .authorizeExchange(exchanges -> exchanges
                        // Allow CORS pre-flight requests
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()

                        // Public documentation & health endpoints
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/**", "/uploads/**").permitAll()

                        // All core APIs require valid ZITADEL authentication
                        .pathMatchers("/api/songs/**").authenticated()
                        .pathMatchers("/api/playlists/**").authenticated()
                        .pathMatchers("/api/users/**").authenticated()

                        // Any other route requires authentication
                        .anyExchange().authenticated()
                )

                // OAuth2 Resource Server validates tokens using ZITADEL's JWKS
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
