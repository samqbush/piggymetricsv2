package com.piggymetrics.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import java.net.URI;

/**
 * Backend-for-Frontend (BFF) security for the reactive Spring Cloud Gateway.
 *
 * <p>The gateway is a confidential OAuth2 client using the authorization_code + PKCE
 * flow. After login the browser holds only an {@code HttpOnly} session cookie; the
 * access-token JWT lives in the gateway session and is relayed downstream by the
 * {@code TokenRelay} default filter. This replaces the removed password-grant flow
 * that stored a bearer token in the browser's {@code localStorage}.
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityWebFilterChain springSecurityFilterChain(
			ServerHttpSecurity http,
			ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver) {

		http
				.authorizeExchange(exchange -> exchange
						// Public landing page + static UI assets.
						.pathMatchers("/", "/index.html", "/attribution.html", "/favicon.ico",
								"/css/**", "/js/**", "/images/**", "/fonts/**").permitAll()
						.pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
						// Anonymous self-service registration (a brand-new user has no session yet).
						.pathMatchers(HttpMethod.POST, "/accounts/").permitAll()
						// Public demo showcase (parity with the legacy demo mode).
						.pathMatchers(HttpMethod.GET, "/accounts/demo", "/statistics/demo").permitAll()
						// OAuth2/OIDC protocol endpoints proxied to auth-service (login, token, jwks).
						.pathMatchers("/uaa/**").permitAll()
						// Everything else requires an authenticated BFF session.
						.anyExchange().authenticated())
				.oauth2Login(oauth2 -> oauth2
						.authorizationRequestResolver(authorizationRequestResolver))
				.logout(logout -> {
					RedirectServerLogoutSuccessHandler handler = new RedirectServerLogoutSuccessHandler();
					handler.setLogoutSuccessUrl(URI.create("/"));
					logout.logoutSuccessHandler(handler);
				})
				.csrf(csrf -> csrf
						// XSRF-TOKEN cookie readable by the SPA (double-submit), paired with the
						// X-XSRF-TOKEN header on state-changing XHR.
						.csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
						.requireCsrfProtectionMatcher(csrfProtectionMatcher()));

		return http.build();
	}

	/**
	 * Require CSRF on all state-changing requests except anonymous registration
	 * ({@code POST /accounts/}) and the proxied {@code /uaa/**} OAuth2 protocol
	 * endpoints (protected by PKCE / one-time authorization codes).
	 */
	private ServerWebExchangeMatcher csrfProtectionMatcher() {
		ServerWebExchangeMatcher stateChanging = new NegatedServerWebExchangeMatcher(
				new OrServerWebExchangeMatcher(
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/**"),
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.HEAD, "/**"),
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.OPTIONS, "/**"),
						ServerWebExchangeMatchers.pathMatchers(HttpMethod.TRACE, "/**")));
		ServerWebExchangeMatcher exempt = new OrServerWebExchangeMatcher(
				ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/accounts/"),
				ServerWebExchangeMatchers.pathMatchers("/uaa/**"));
		return new AndServerWebExchangeMatcher(stateChanging, new NegatedServerWebExchangeMatcher(exempt));
	}

	/**
	 * Sends a PKCE {@code code_challenge} even though the client is confidential, as
	 * required by the {@code browser} RegisteredClient ({@code requireProofKey}).
	 */
	@Bean
	public ServerOAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
			ReactiveClientRegistrationRepository clientRegistrationRepository) {
		DefaultServerOAuth2AuthorizationRequestResolver resolver =
				new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
		resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		return resolver;
	}
}
