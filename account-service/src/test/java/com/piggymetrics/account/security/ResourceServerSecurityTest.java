package com.piggymetrics.account.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.domain.User;
import com.piggymetrics.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 resource-server contract tests: exercises the real JWT
 * {@code SecurityFilterChain} (unlike the {@code standaloneSetup} controller test,
 * which bypasses it). Pins the scope-separated authorization rules:
 * <ul>
 *   <li>no token → 401;</li>
 *   <li>{@code /current} → any authenticated (user) token;</li>
 *   <li>{@code /{name}} → requires the {@code server} scope (service token) —
 *       a user token is forbidden (IDOR protection);</li>
 *   <li>{@code POST /} registration and the {@code /demo} showcase stay public;</li>
 *   <li>{@code /actuator/prometheus} stays public so Prometheus can scrape.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ResourceServerSecurityTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AccountService accountService;

	@Test
	void currentRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/current"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentAllowsAuthenticatedUserToken() throws Exception {
		when(accountService.findByName(anyString())).thenReturn(new Account());
		mockMvc.perform(get("/current").with(jwt()))
				.andExpect(status().isOk());
	}

	@Test
	void getAccountByNameRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/someone-else"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getAccountByNameForbiddenWithUserToken() throws Exception {
		mockMvc.perform(get("/someone-else").with(jwt()))
				.andExpect(status().isForbidden());
	}

	@Test
	void getAccountByNameAllowedWithServerScope() throws Exception {
		when(accountService.findByName(anyString())).thenReturn(new Account());
		mockMvc.perform(get("/someone-else")
						.with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_server"))))
				.andExpect(status().isOk());
	}

	@Test
	void demoShowcaseIsPublic() throws Exception {
		when(accountService.findByName("demo")).thenReturn(new Account());
		mockMvc.perform(get("/demo"))
				.andExpect(status().isOk());
	}

	@Test
	void registrationIsPublic() throws Exception {
		when(accountService.create(any(User.class))).thenReturn(new Account());
		User user = new User();
		user.setUsername("newuser");
		user.setPassword("password");
		mockMvc.perform(post("/")
						.contentType(MediaType.APPLICATION_JSON)
						.content(MAPPER.writeValueAsString(user)))
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
