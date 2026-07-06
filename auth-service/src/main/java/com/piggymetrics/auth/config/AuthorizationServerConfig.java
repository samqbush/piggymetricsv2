package com.piggymetrics.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Spring Authorization Server configuration — replaces the removed
 * {@code spring-security-oauth2} {@code @EnableAuthorizationServer} stack.
 *
 * <p>Issues stateless signed JWTs (RSA). Registered clients are held in memory and
 * seeded from configuration/environment (no relational store); user accounts remain
 * in MongoDB via {@link com.piggymetrics.auth.service.security.MongoUserDetailsService}.
 */
@Configuration
public class AuthorizationServerConfig {

	/**
	 * Dedicated filter chain for the OAuth2/OIDC protocol endpoints
	 * ({@code /oauth2/**}, {@code /.well-known/**}). Highest priority.
	 */
	@Bean
	@Order(1)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
		http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
				.oidc(Customizer.withDefaults());

		RequestMatcher endpointsMatcher = http
				.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
				.getEndpointsMatcher();

		http
				// Redirect unauthenticated authorization-code requests to the form login.
				.exceptionHandling(e -> e.authenticationEntryPoint(
						new LoginUrlAuthenticationEntryPoint("/login")))
				// Bearer tokens on the protocol endpoints (e.g. userinfo) are JWTs.
				.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
				// Back-channel token/introspection calls carry no CSRF token.
				.csrf(c -> c.ignoringRequestMatchers(endpointsMatcher));

		return http.build();
	}

	/**
	 * In-memory registered clients seeded from environment-provided secrets. The
	 * raw secret is supplied by the client application; auth-service persists only
	 * the BCrypt hash.
	 */
	@Bean
	public RegisteredClientRepository registeredClientRepository(
			PasswordEncoder passwordEncoder,
			@Value("${GATEWAY_URL:http://localhost:4000}") String gatewayUrl,
			@Value("${BROWSER_CLIENT_SECRET:browser-secret}") String browserSecret,
			@Value("${ACCOUNT_SERVICE_PASSWORD:account-secret}") String accountSecret,
			@Value("${STATISTICS_SERVICE_PASSWORD:statistics-secret}") String statisticsSecret,
			@Value("${NOTIFICATION_SERVICE_PASSWORD:notification-secret}") String notificationSecret) {

		// BFF (gateway) confidential client — authorization_code + PKCE, refresh.
		RegisteredClient browser = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("browser")
				.clientSecret(passwordEncoder.encode(browserSecret))
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUri(gatewayUrl + "/login/oauth2/code/piggymetrics")
				.postLogoutRedirectUri(gatewayUrl + "/")
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.scope("ui")
				.clientSettings(ClientSettings.builder()
						.requireProofKey(true)
						.requireAuthorizationConsent(false)
						.build())
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(Duration.ofMinutes(15))
						.refreshTokenTimeToLive(Duration.ofHours(12))
						.reuseRefreshTokens(false)
						.build())
				.build();

		return new InMemoryRegisteredClientRepository(
				browser,
				serviceClient("account-service", accountSecret, passwordEncoder),
				serviceClient("statistics-service", statisticsSecret, passwordEncoder),
				serviceClient("notification-service", notificationSecret, passwordEncoder));
	}

	private RegisteredClient serviceClient(String clientId, String rawSecret, PasswordEncoder encoder) {
		return RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId(clientId)
				.clientSecret(encoder.encode(rawSecret))
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
				.scope("server")
				.tokenSettings(TokenSettings.builder()
						.accessTokenTimeToLive(Duration.ofMinutes(15))
						.build())
				.build();
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		RSAKey rsaKey = generateRsaKey();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return new ImmutableJWKSet<>(jwkSet);
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings(
			@Value("${AUTH_ISSUER_URI:http://auth-service:5000/uaa}") String issuer) {
		return AuthorizationServerSettings.builder()
				.issuer(issuer)
				.build();
	}

	private static RSAKey generateRsaKey() {
		KeyPair keyPair;
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			keyPair = generator.generateKeyPair();
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to generate RSA key for JWT signing", ex);
		}
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		return new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
	}
}
