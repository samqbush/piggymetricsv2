package com.piggymetrics.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TODO(Phase 5): temporary permit-all shim.
 *
 * <p>The original OAuth2 resource-server security ({@code @EnableResourceServer},
 * {@code CustomUserInfoTokenServices}, {@code OAuth2FeignRequestInterceptor}) was
 * built on {@code spring-security-oauth2}, which is removed in Spring Security 6.
 * Phase 3 is a platform lift only; the real JWT resource-server + client-credentials
 * security is rewritten in Phase 5 (Spring Authorization Server). Until then every
 * request is permitted so the service compiles and its non-security tests pass.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}
}
