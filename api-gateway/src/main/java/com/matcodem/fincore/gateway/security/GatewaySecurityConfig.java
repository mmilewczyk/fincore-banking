package com.matcodem.fincore.gateway.security;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Flux;

/**
 * Gateway-level security - single JWT validation point for all services.
 * <p>
 * Downstream services still validate JWT (defence in depth),
 * but the Gateway is the first and main enforcement point.
 * <p>
 * Route-level authorization:
 * /api/v1/accounts/**    -> ROLE_USER or ROLE_ADMIN
 * /api/v1/payments/**    -> ROLE_USER or ROLE_ADMIN
 * /api/v1/fx/**          -> ROLE_USER or ROLE_ADMIN
 * /api/v1/fraud/**       -> ROLE_COMPLIANCE or ROLE_ADMIN only
 * /actuator/**           -> internal only (blocked from external traffic)
 * <p>
 * CORS: configured for the frontend domain.
 * All JWT claims (sub, roles) are forwarded to downstream services
 * via X-Auth-User-Id and X-Auth-Roles headers (added by JwtForwardingFilter).
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchanges -> exchanges
						// Public health check
						.pathMatchers("/actuator/health").permitAll()

						// Fraud cases - compliance officers only
						.pathMatchers("/api/v1/fraud/**")
						.hasAnyRole("COMPLIANCE", "ADMIN")

						// All other API calls - authenticated users
						.pathMatchers("/api/v1/**")
						.hasAnyRole("USER", "COMPLIANCE", "ADMIN")

						// Deny everything else (including /actuator endpoints)
						.anyExchange().denyAll()
				)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
				);

		return http.build();
	}

	@Bean
	public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
		ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
			if (realmAccess == null) return Flux.empty();
			@SuppressWarnings("unchecked")
			List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());
			return Flux.fromIterable(roles)
					.map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
		});
		return converter;
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(List.of(
				"https://fincore.bank.pl",
				"http://localhost:3000"  // local dev
		));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}