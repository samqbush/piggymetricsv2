package com.piggymetrics.statistics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT resource-server security (Phase 5). {@code /current} is the authenticated
 * user's own statistics; {@code /{accountName}} (GET/PUT) is a service-to-service
 * operation requiring the {@code server} scope, restoring the pre-Phase-3
 * {@code @PreAuthorize} rules.
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
						.requestMatchers("/current").authenticated()
						.requestMatchers("/{accountName}").hasAuthority("SCOPE_server")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}
}
