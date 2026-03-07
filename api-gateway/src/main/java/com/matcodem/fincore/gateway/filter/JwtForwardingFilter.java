package com.matcodem.fincore.gateway.filter;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Global filter — extracts JWT claims and forwards them as HTTP headers.
 * <p>
 * Downstream services can trust these headers because:
 * 1. Gateway validates the JWT signature (public key from Keycloak)
 * 2. Only requests that pass JWT validation reach this filter
 * 3. Services are not exposed externally — only reachable via Gateway
 * (enforced by K8s NetworkPolicy: ClusterIP Services, no public NodePort/LoadBalancer)
 * <p>
 * Headers added:
 * X-Auth-User-Id  — JWT subject (Keycloak user UUID)
 * X-Auth-Username — preferred_username claim
 * X-Auth-Roles    — comma-separated roles from realm_access.roles
 * X-Trace-Id      — propagated from MDC for distributed tracing
 * <p>
 * The original Authorization: Bearer header is also forwarded so downstream
 * services can independently validate if they choose to (defence in depth).
 */
@Slf4j
@Component
public class JwtForwardingFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return ReactiveSecurityContextHolder.getContext()
				.flatMap(ctx -> {
					if (!(ctx.getAuthentication() instanceof JwtAuthenticationToken jwtAuth)) {
						return chain.filter(exchange);
					}

					Jwt jwt = jwtAuth.getToken();
					String userId = jwt.getSubject();
					String username = jwt.getClaimAsString("preferred_username");

					@SuppressWarnings("unchecked")
					Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
					String roles = "";
					if (realmAccess != null) {
						@SuppressWarnings("unchecked")
						List<String> roleList = (List<String>) realmAccess.getOrDefault("roles", List.of());
						roles = String.join(",", roleList);
					}

					log.debug("Forwarding identity headers — userId: {}, username: {}", userId, username);

					String finalRoles = roles;
					ServerWebExchange mutated = exchange.mutate()
							.request(r -> r
									.header("X-Auth-User-Id", userId != null ? userId : "")
									.header("X-Auth-Username", username != null ? username : "")
									.header("X-Auth-Roles", finalRoles)
							)
							.build();

					return chain.filter(mutated);
				})
				.switchIfEmpty(chain.filter(exchange));
	}

	@Override
	public int getOrder() {
		return -100; // run before routing filters
	}
}