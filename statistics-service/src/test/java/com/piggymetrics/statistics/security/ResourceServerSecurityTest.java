package com.piggymetrics.statistics.security;

import com.piggymetrics.statistics.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 resource-server contract tests for statistics-service, exercising the
 * real JWT {@code SecurityFilterChain}:
 * <ul>
 *   <li>no token → 401;</li>
 *   <li>{@code /current} → any authenticated (user) token;</li>
 *   <li>{@code /{accountName}} → requires the {@code server} scope; a user token
 *       is forbidden;</li>
 *   <li>{@code /demo} showcase and {@code /actuator/prometheus} stay public.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ResourceServerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private StatisticsService statisticsService;

	@Test
	void currentRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/current"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentAllowsAuthenticatedUserToken() throws Exception {
		when(statisticsService.findByAccountName(anyString())).thenReturn(Collections.emptyList());
		mockMvc.perform(get("/current").with(jwt()))
				.andExpect(status().isOk());
	}

	@Test
	void getByAccountNameRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/someone-else"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getByAccountNameForbiddenWithUserToken() throws Exception {
		mockMvc.perform(get("/someone-else").with(jwt()))
				.andExpect(status().isForbidden());
	}

	@Test
	void getByAccountNameAllowedWithServerScope() throws Exception {
		when(statisticsService.findByAccountName(anyString())).thenReturn(Collections.emptyList());
		mockMvc.perform(get("/someone-else")
						.with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_server"))))
				.andExpect(status().isOk());
	}

	@Test
	void demoShowcaseIsPublic() throws Exception {
		when(statisticsService.findByAccountName("demo")).thenReturn(Collections.emptyList());
		mockMvc.perform(get("/demo"))
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
