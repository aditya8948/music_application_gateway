package com.musekit.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global Gateway filter that injects user identity headers into downstream requests.
 *
 * Extracts claims from the validated ZITADEL JWT and injects:
 * - X-User-Id: ZITADEL subject ID
 * - X-User-Email: user email address
 * - X-User-Name: user display name
 *
 * This allows downstream microservices to inspect user details directly from HTTP headers.
 */
@Component
public class UserHeaderFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(UserHeaderFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .filter(c -> c.getAuthentication() instanceof JwtAuthenticationToken)
                .map(c -> (JwtAuthenticationToken) c.getAuthentication())
                .map(JwtAuthenticationToken::getToken)
                .flatMap(jwt -> {
                    String userId = jwt.getSubject();
                    String email = extractEmail(jwt);
                    String name = extractName(jwt);

                    log.debug("Injecting user headers into downstream request - userId: {}, email: {}", userId, email);

                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
                    if (userId != null) {
                        requestBuilder.header("X-User-Id", userId);
                    }
                    if (email != null) {
                        requestBuilder.header("X-User-Email", email);
                    }
                    if (name != null) {
                        requestBuilder.header("X-User-Name", name);
                    }

                    return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private String extractEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            return email;
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (preferred != null && preferred.contains("@")) {
            return preferred;
        }
        return null;
    }

    private String extractName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given != null || family != null) {
            return ((given != null ? given : "") + " " + (family != null ? family : "")).trim();
        }
        return null;
    }

    @Override
    public int getOrder() {
        // Run after Spring Security authentication has populated the security context
        return 0;
    }
}
