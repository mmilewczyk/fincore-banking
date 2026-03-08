package com.matcodem.fincore.payment.infrastructure.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for payment-service.
 * <p>
 * JWT resource server - all requests must carry a Bearer token issued by Keycloak.
 * <p>
 * Role extraction:
 * Keycloak stores roles in JWT under: realm_access.roles -> ["ROLE_USER", "ROLE_ADMIN"]
 * Spring Security expects GrantedAuthority with "ROLE_" prefix.
 * The JwtAuthenticationConverter below reads realm_access.roles and maps each
 * to a SimpleGrantedAuthority, making @PreAuthorize("hasRole('USER')") work correctly.
 * <p>
 * Session policy:
 * STATELESS - no HttpSession. Each request is authenticated independently via JWT.
 * Required for horizontal scaling (K8s replicas share no session state).
 * <p>
 * Endpoint access:
 * - /actuator/health, /actuator/health/** -> public (K8s liveness/readiness probes)
 * - all other /actuator/** -> ADMIN only (metrics, env, loggers)
 * - /api/v1/payments/** -> authenticated (fine-grained control via @PreAuthorize)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class PaymentSecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable) // stateless API - CSRF not needed
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// K8s health probes - must be accessible without token
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						// Prometheus scraping from within the cluster
						.requestMatchers("/actuator/prometheus").permitAll()
						// All other actuator endpoints require ADMIN (metrics, env, loggers)
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						// All payment endpoints - fine-grained access via @PreAuthorize
						.requestMatchers("/api/v1/payments/**").authenticated()
						.anyRequest().denyAll()
				)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				);

		return http.build();
	}

	/**
	 * Extracts roles from Keycloak JWT structure:
	 * <p>
	 * {
	 * "realm_access": {
	 * "roles": ["ROLE_USER", "offline_access", "uma_authorization"]
	 * }
	 * }
	 * <p>
	 * Maps each role to a Spring GrantedAuthority so @PreAuthorize("hasRole('USER')") works.
	 * Roles that don't start with "ROLE_" are skipped - Keycloak includes internal roles
	 * like "offline_access" and "uma_authorization" that are irrelevant to our authorization.
	 */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
			if (realmAccess == null || !realmAccess.containsKey("roles")) {
				return List.of();
			}

			@SuppressWarnings("unchecked")
			Collection<String> roles = (Collection<String>) realmAccess.get("roles");

			return roles.stream()
					.filter(role -> role.startsWith("ROLE_"))
					.map(SimpleGrantedAuthority::new)
					.collect(Collectors.toList());
		});
		return converter;
	}
}