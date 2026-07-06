package com.piggymetrics.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 authorization-server contract tests. Pins the token endpoint and JWKS
 * that the resource servers and BFF depend on:
 * <ul>
 *   <li>{@code client_credentials} with valid service credentials returns a signed
 *       JWT access token;</li>
 *   <li>bad client credentials are rejected;</li>
 *   <li>the JWKS endpoint publishes the signing key so resource servers can verify
 *       tokens.</li>
 * </ul>
 * Secrets fall back to the {@code AuthorizationServerConfig} defaults
 * ({@code account-secret}) when the environment does not override them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthorizationServerContractTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void clientCredentialsGrantReturnsSignedJwt() throws Exception {
		mockMvc.perform(post("/oauth2/token")
						.with(httpBasic("account-service", "account-secret"))
						.param("grant_type", "client_credentials")
						.param("scope", "server"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andExpect(jsonPath("$.scope").value("server"))
				// A compact JWT has three dot-separated segments.
				.andExpect(jsonPath("$.access_token").value(containsString(".")));
	}

	@Test
	void tokenEndpointRejectsBadClientSecret() throws Exception {
		mockMvc.perform(post("/oauth2/token")
						.with(httpBasic("account-service", "wrong-secret"))
						.param("grant_type", "client_credentials")
						.param("scope", "server"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void jwksEndpointPublishesSigningKey() throws Exception {
		mockMvc.perform(get("/oauth2/jwks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"));
	}
}
