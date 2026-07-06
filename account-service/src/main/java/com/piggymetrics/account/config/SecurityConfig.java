package com.piggymetrics.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT resource-server security (Phase 5). Validates bearer JWTs issued by
 * auth-service (Spring Authorization Server). Scope-separated authorization
 * restores the pre-Phase-3 rules that were carried as {@code @PreAuthorize} TODOs:
 *
 * <ul>
 *   <li>{@code POST /} — anonymous registration (a brand-new user has no token).</li>
 *   <li>{@code /current} — the authenticated end user's own account.</li>
 *   <li>{@code /{name}} — service-to-service read, requires the {@code server}
 *       scope (a {@code client_credentials} token), preventing a user token from
 *       reading arbitrary accounts (IDOR).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
						.requestMatchers(HttpMethod.POST, "/").permitAll()
						.requestMatchers(HttpMethod.GET, "/demo").permitAll()
						.requestMatchers("/current").authenticated()
						.requestMatchers(HttpMethod.GET, "/{name}").hasAuthority("SCOPE_server")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}
}
