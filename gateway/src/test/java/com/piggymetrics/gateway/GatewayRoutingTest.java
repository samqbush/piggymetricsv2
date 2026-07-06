package com.piggymetrics.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Characterization / routing-parity tests for the Spring Cloud Gateway edge, now a
 * Backend-for-Frontend (BFF). The parity contract pinned from the legacy Zuul edge:
 * the full request path (including the route prefix) is forwarded unchanged — i.e.
 * no {@code StripPrefix} filter is applied.
 *
 * <p>Phase 5 BFF additions pinned here:
 * <ul>
 *   <li>protected API routes require an authenticated session — an anonymous request
 *       is redirected to the authorization_code login;</li>
 *   <li>the public landing page and the {@code /uaa/**} OAuth2 protocol endpoints
 *       stay anonymously reachable.</li>
 * </ul>
 *
 * <p>A single reactor-netty backend records the last received request so routing can
 * be asserted behaviorally without external services. The test binds to the
 * application context (mock server) so Spring Security test mutators such as
 * {@code mockOidcLogin()} can authenticate the exchange; routing still executes real
 * outbound calls to the recording backend. Routes and the BFF client registration
 * are injected via {@link DynamicPropertySource} because the config server (which
 * serves them at runtime) is disabled here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GatewayRoutingTest {

	private static final DisposableServer BACKEND;
	private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
	private static final AtomicReference<HttpHeaders> LAST_HEADERS = new AtomicReference<>();

	static {
		BACKEND = HttpServer.create()
				.port(0)
				.handle((request, response) -> {
					LAST_PATH.set(request.uri());
					HttpHeaders headers = new HttpHeaders();
					request.requestHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
					LAST_HEADERS.set(headers);
					return response.status(200).sendString(Mono.just("ok"));
				})
				.bindNow();
	}

	@Autowired
	private ApplicationContext context;

	private WebTestClient webClient;

	@BeforeEach
	void setUp() {
		webClient = WebTestClient.bindToApplicationContext(context)
				.apply(springSecurity())
				.configureClient()
				.build();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.cloud.config.enabled", () -> "false");
		registry.add("spring.cloud.discovery.enabled", () -> "false");

		String backendUri = "http://localhost:" + BACKEND.port();
		route(registry, 0, "auth-service", backendUri, "/uaa/**");
		route(registry, 1, "account-service", backendUri, "/accounts/**");
		route(registry, 2, "statistics-service", backendUri, "/statistics/**");
		route(registry, 3, "notification-service", backendUri, "/notifications/**");

		// Minimal BFF client registration so the reactive OAuth2 client wires up
		// (endpoints are unreachable placeholders — no network call at startup).
		String reg = "spring.security.oauth2.client.registration.piggymetrics.";
		registry.add(reg + "provider", () -> "uaa");
		registry.add(reg + "client-id", () -> "browser");
		registry.add(reg + "client-secret", () -> "browser-secret");
		registry.add(reg + "authorization-grant-type", () -> "authorization_code");
		registry.add(reg + "redirect-uri", () -> "{baseUrl}/login/oauth2/code/piggymetrics");
		registry.add(reg + "scope", () -> "openid,ui");
		String prov = "spring.security.oauth2.client.provider.uaa.";
		registry.add(prov + "authorization-uri", () -> "http://localhost:9999/uaa/oauth2/authorize");
		registry.add(prov + "token-uri", () -> "http://localhost:9999/uaa/oauth2/token");
		registry.add(prov + "jwk-set-uri", () -> "http://localhost:9999/uaa/oauth2/jwks");
		registry.add(prov + "user-info-uri", () -> "http://localhost:9999/uaa/userinfo");
		registry.add(prov + "user-name-attribute", () -> "sub");
	}

	private static void route(DynamicPropertyRegistry registry, int index, String id, String uri, String path) {
		registry.add("spring.cloud.gateway.routes[" + index + "].id", () -> id);
		registry.add("spring.cloud.gateway.routes[" + index + "].uri", () -> uri);
		registry.add("spring.cloud.gateway.routes[" + index + "].predicates[0]", () -> "Path=" + path);
	}

	@AfterAll
	static void stopBackend() {
		BACKEND.disposeNow();
	}

	@Test
	void forwardsFullPathForEachRouteWhenAuthenticated() {
		assertRouted("/accounts/123", "/accounts/123");
		assertRouted("/statistics/demo", "/statistics/demo");
		assertRouted("/notifications/recipients/current", "/notifications/recipients/current");
	}

	@Test
	void preservesUaaPathWithoutStrippingPrefix() {
		// /uaa/** is anonymously reachable (OAuth2 protocol endpoints) and the /uaa
		// prefix must reach auth-service unchanged (legacy Zuul stripPrefix:false parity).
		webClient.get().uri("/uaa/oauth2/jwks")
				.exchange()
				.expectStatus().isOk();
		assertThat(LAST_PATH.get()).isEqualTo("/uaa/oauth2/jwks");
	}

	@Test
	void redirectsAnonymousProtectedRequestToLogin() {
		webClient.get().uri("/accounts/current")
				.exchange()
				.expectStatus().isFound()
				.expectHeader().valueMatches(HttpHeaders.LOCATION, ".*/oauth2/authorization/piggymetrics");
	}

	@Test
	void doesNotRouteUnknownPathsWhenAuthenticated() {
		webClient.mutateWith(mockOidcLogin())
				.get().uri("/does-not-exist")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void servesStaticUiIndexPage() {
		webClient.get().uri("/")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).value(body -> assertThat(body).contains("Piggy Metrics"));
	}

	private void assertRouted(String requestPath, String expectedBackendPath) {
		webClient.mutateWith(mockOidcLogin())
				.get().uri(requestPath)
				.exchange()
				.expectStatus().isOk();
		assertThat(LAST_PATH.get()).isEqualTo(expectedBackendPath);
	}
}
