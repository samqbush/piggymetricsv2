package com.piggymetrics.auth.config;

import com.piggymetrics.auth.domain.User;
import com.piggymetrics.auth.repository.UserRepository;
import com.piggymetrics.auth.service.security.MongoUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Application-level security for auth-service, kept separate from the Authorization
 * Server protocol chain in {@link AuthorizationServerConfig}:
 *
 * <ul>
 *   <li>{@code /users/**} — stateless JWT resource server. {@code POST /users}
 *       requires a service token ({@code SCOPE_server}); {@code /users/current}
 *       requires an authenticated user token.</li>
 *   <li>everything else — form login (used by the authorization_code flow) plus
 *       open actuator health/prometheus for scraping.</li>
 * </ul>
 */
@Configuration
public class DefaultSecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(DefaultSecurityConfig.class);

	/**
	 * Stateless resource-server chain for the custom user-management API. Bearer
	 * JWTs only; CSRF disabled because there is no browser session here.
	 */
	@Bean
	@Order(2)
	public SecurityFilterChain userManagementSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatcher("/users/**")
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/users").hasAuthority("SCOPE_server")
						.requestMatchers("/users/current").authenticated()
						.anyRequest().authenticated())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(c -> c.disable())
				.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/**
	 * Default chain: form login for the authorization_code user authentication and
	 * open actuator endpoints for monitoring.
	 */
	@Bean
	@Order(3)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
						.anyRequest().authenticated())
				.formLogin(Customizer.withDefaults());
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(
			MongoUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new org.springframework.security.authentication.ProviderManager(provider);
	}

	/**
	 * Seeds a default {@code demo} user (BCrypt-encoded) when absent, so the UI
	 * login flow works out of the box. Disabled by setting
	 * {@code piggymetrics.auth.seed-demo-user=false}.
	 */
	@Bean
	public CommandLineRunner demoUserSeeder(
			UserRepository repository,
			PasswordEncoder passwordEncoder,
			@Value("${piggymetrics.auth.seed-demo-user:true}") boolean seed,
			@Value("${piggymetrics.auth.demo-user-password:demo}") String demoPassword) {
		return args -> {
			if (!seed || repository.findById("demo").isPresent()) {
				return;
			}
			User demo = new User();
			demo.setUsername("demo");
			demo.setPassword(passwordEncoder.encode(demoPassword));
			repository.save(demo);
			log.info("seeded default demo user");
		};
	}
}
