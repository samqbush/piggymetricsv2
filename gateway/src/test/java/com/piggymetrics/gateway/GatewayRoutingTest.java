package com.piggymetrics.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization / routing-parity tests for the Spring Cloud Gateway edge
 * (replacing the legacy Netflix Zuul gateway). The legacy Zuul routes all used
 * {@code stripPrefix: false} and cleared {@code sensitiveHeaders}, so the parity
 * contract this pins is:
 * <ul>
 *   <li>the full request path (including the route prefix) is forwarded unchanged
 *       — i.e. no {@code StripPrefix} filter is applied;</li>
 *   <li>{@code Authorization} and {@code Cookie} request headers are forwarded
 *       downstream (the UI relies on this for {@code /uaa/oauth/token} and
 *       {@code /accounts/current}).</li>
 * </ul>
 * A single reactor-netty backend (already on the gateway classpath) records the
 * last received request so routing can be asserted behaviorally without external
 * services. Routes are injected via {@link DynamicPropertySource} because the real
 * routes are served by the config server at runtime, which is disabled here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
	private WebTestClient webClient;

	@DynamicPropertySource
	static void routes(DynamicPropertyRegistry registry) {
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("spring.cloud.config.enabled", () -> "false");
		registry.add("spring.cloud.discovery.enabled", () -> "false");

		String backendUri = "http://localhost:" + BACKEND.port();
		// Mirror the production routes; downstream URIs point at the recording backend.
		route(registry, 0, "auth-service", backendUri, "/uaa/**");
		route(registry, 1, "account-service", backendUri, "/accounts/**");
		route(registry, 2, "statistics-service", backendUri, "/statistics/**");
		route(registry, 3, "notification-service", backendUri, "/notifications/**");
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
	void forwardsFullPathForEachRoute() {
		assertRouted("/accounts/123", "/accounts/123");
		assertRouted("/statistics/demo", "/statistics/demo");
		assertRouted("/notifications/recipients/current", "/notifications/recipients/current");
	}

	@Test
	void preservesUaaPathWithoutStrippingPrefix() {
		// Legacy Zuul used stripPrefix:false — the /uaa prefix must reach the backend.
		assertRouted("/uaa/oauth/token", "/uaa/oauth/token");
	}

	@Test
	void forwardsAuthorizationAndCookieHeaders() {
		webClient.get().uri("/accounts/current")
				.header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
				.header(HttpHeaders.COOKIE, "SESSION=abc123")
				.exchange()
				.expectStatus().isOk();

		HttpHeaders forwarded = LAST_HEADERS.get();
		assertThat(forwarded.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
		assertThat(forwarded.getFirst(HttpHeaders.COOKIE)).isEqualTo("SESSION=abc123");
	}

	@Test
	void doesNotRouteUnknownPaths() {
		webClient.get().uri("/does-not-exist")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void servesStaticUiIndexPage() {
		// The UI static content is bundled under src/main/resources/static and must
		// still be served by the reactive gateway (WebFlux welcome-page handling).
		webClient.get().uri("/")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).value(body -> assertThat(body).contains("Piggy Metrics"));
	}

	private void assertRouted(String requestPath, String expectedBackendPath) {
		webClient.get().uri(requestPath)
				.exchange()
				.expectStatus().isOk();
		assertThat(LAST_PATH.get()).isEqualTo(expectedBackendPath);
	}
}
