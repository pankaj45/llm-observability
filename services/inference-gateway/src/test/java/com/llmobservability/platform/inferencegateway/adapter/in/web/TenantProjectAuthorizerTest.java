package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class TenantProjectAuthorizerTest {

    @Test
    void allowsMatchingScopeTenantAndProjectClaims() {
        TenantProjectAuthorizer authorizer = new TenantProjectAuthorizer(enabledProperties());
        Jwt jwt = jwt(Map.of(
                "scope", "conversation:read analytics:read",
                "tenant_ids", List.of("tenant-a"),
                "project_ids", List.of("project-a")));

        StepVerifier.create(authorizer.requireTenantProject("tenant-a", "project-a", "conversation:read")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(MonoSecurity.context(jwt))))
                .verifyComplete();
    }

    @Test
    void deniesMissingScope() {
        TenantProjectAuthorizer authorizer = new TenantProjectAuthorizer(enabledProperties());
        Jwt jwt = jwt(Map.of(
                "scope", "analytics:read",
                "tenant_ids", List.of("tenant-a"),
                "project_ids", List.of("project-a")));

        StepVerifier.create(authorizer.requireTenantProject("tenant-a", "project-a", "conversation:read")
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(MonoSecurity.context(jwt))))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    private SecurityProperties enabledProperties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setEnabled(true);
        properties.setIssuerUri("https://issuer.example.test");
        properties.setAudience("llm-observability-platform");
        return properties;
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T01:00:00Z"),
                Map.of("alg", "none"),
                claims);
    }

    private static final class MonoSecurity {
        static reactor.core.publisher.Mono<SecurityContextImpl> context(Jwt jwt) {
            return reactor.core.publisher.Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt, java.util.List.of())));
        }
    }
}
