package com.piggymetrics.account.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Restores service-to-service authentication for outbound Feign calls
 * (account &rarr; auth {@code createUser}, account &rarr; statistics) using the
 * {@code client_credentials} grant. Replaces the removed
 * {@code OAuth2FeignRequestInterceptor} / {@code OAuth2RestTemplate}.
 *
 * <p>Uses {@link AuthorizedClientServiceOAuth2AuthorizedClientManager} (not the
 * web-bound manager) so tokens are obtained even when the Feign call runs outside
 * an HTTP request.
 */
@Configuration
public class FeignOAuth2Config {

	static final String CLIENT_REGISTRATION_ID = "account-service";

	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(
			ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientService authorizedClientService) {

		AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
				new AuthorizedClientServiceOAuth2AuthorizedClientManager(
						clientRegistrationRepository, authorizedClientService);
		manager.setAuthorizedClientProvider(
				OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
		return manager;
	}

	@Bean
	public RequestInterceptor oauth2FeignRequestInterceptor(OAuth2AuthorizedClientManager manager) {
		return template -> {
			OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
					.withClientRegistrationId(CLIENT_REGISTRATION_ID)
					.principal(CLIENT_REGISTRATION_ID)
					.build();
			OAuth2AuthorizedClient client = manager.authorize(request);
			if (client == null) {
				throw new IllegalStateException(
						"Unable to obtain client_credentials access token for '"
								+ CLIENT_REGISTRATION_ID + "'; check the auth-service availability and "
								+ "client-secret configuration.");
			}
			template.header("Authorization",
					"Bearer " + client.getAccessToken().getTokenValue());
		};
	}
}
