package com.llmobservability.platform.inferencegateway.adapter.in.web;

import com.llmobservability.platform.inferencegateway.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class TenantProjectAuthorizer {
    private static final Logger log = LoggerFactory.getLogger(TenantProjectAuthorizer.class);

    private final SecurityProperties properties;

    TenantProjectAuthorizer(SecurityProperties properties) {
        this.properties = properties;
    }

    Mono<Void> requireScope(String scope) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return jwt()
                .filter(jwt -> scopes(jwt).contains(scope))
                .switchIfEmpty(Mono.defer(() -> deny("missing required scope " + scope)))
                .then();
    }

    Mono<Void> requireTenantProject(String tenantId, String projectId, String scope) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return jwt()
                .filter(jwt -> scopes(jwt).contains(scope))
                .filter(jwt -> allows(jwt, "tenant_ids", "tenants", tenantId))
                .filter(jwt -> allows(jwt, "project_ids", "projects", projectId))
                .switchIfEmpty(Mono.defer(() -> deny("tenant/project authorization denied")))
                .then();
    }

    private Mono<Jwt> jwt() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .cast(Jwt.class)
                .switchIfEmpty(Mono.defer(() -> deny("authenticated JWT is required")));
    }

    private <T> Mono<T> deny(String reason) {
        log.warn("audit.security.denied reason={}", reason);
        return Mono.error(new AccessDeniedException(reason));
    }

    private Set<String> scopes(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        List<String> scp = jwt.getClaimAsStringList("scp");
        Set<String> scopes = scope == null || scope.isBlank()
                ? Set.of()
                : Set.of(scope.split(" "));
        if (scp == null || scp.isEmpty()) {
            return scopes;
        }
        return java.util.stream.Stream.concat(scopes.stream(), scp.stream()).collect(Collectors.toSet());
    }

    private boolean allows(Jwt jwt, String preferredClaim, String fallbackClaim, String expected) {
        List<String> values = jwt.getClaimAsStringList(preferredClaim);
        if (values == null || values.isEmpty()) {
            Object raw = jwt.getClaim(fallbackClaim);
            if (raw instanceof String stringValue) {
                values = List.of(stringValue);
            } else if (raw instanceof Collection<?> collection) {
                values = collection.stream().map(String::valueOf).toList();
            }
        }
        return values != null && (values.contains("*") || values.contains(expected));
    }
}
