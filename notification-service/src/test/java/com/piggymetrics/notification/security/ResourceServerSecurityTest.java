package com.piggymetrics.notification.security;

import com.piggymetrics.notification.domain.Recipient;
import com.piggymetrics.notification.service.RecipientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 resource-server contract tests for notification-service, exercising the
 * real JWT {@code SecurityFilterChain}. Every {@code /recipients/**} endpoint acts
 * on the authenticated end user's own settings:
 * <ul>
 *   <li>no token → 401;</li>
 *   <li>an authenticated (user) token → allowed;</li>
 *   <li>{@code /actuator/prometheus} stays public so Prometheus can scrape.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ResourceServerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private RecipientService recipientService;

	@Test
	void currentRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/recipients/current"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentAllowsAuthenticatedUserToken() throws Exception {
		when(recipientService.findByAccountName(anyString())).thenReturn(new Recipient());
		mockMvc.perform(get("/recipients/current").with(jwt()))
				.andExpect(status().isOk());
	}

	@Test
	void prometheusScrapeIsNotBlockedBySecurity() throws Exception {
		// The scrape endpoint is only fully wired at runtime (config server / observability
		// stack). Here we assert only the Phase 5 SecurityConfig rule: an anonymous scrape
		// is not rejected by security (no 401/403), so Prometheus can reach it.
		int status = mockMvc.perform(get("/actuator/prometheus"))
				.andReturn().getResponse().getStatus();
		assertThat(status).isNotIn(401, 403);
	}
}
