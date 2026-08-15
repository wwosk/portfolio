package org.example.mypaintings.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mypaintings.auth.jwt.JwtService;
import org.example.mypaintings.config.WebConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CurrentUserIdE2ETest.TestController.class)
@Import({
        CurrentUserArgumentResolver.class,
        WebConfig.class,
        CurrentUserIdE2ETest.TestController.class
})
class CurrentUserIdE2ETest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    ObjectMapper objectMapper;

    @RestController
    static class TestController {
        @GetMapping("/whoami")
        Long whoami(@CurrentUserId Long userId) {
            return userId;
        }
    }

    @Nested
    @DisplayName("CurrentUserId Argument Resolver E2E Tests")
    class ArgumentResolverTests {

        @Test
        @DisplayName("Should successfully inject user ID from security context when authenticated")
        void shouldInjectUserIdFromSecurityContext() throws Exception {
            // given & when & then
            mockMvc.perform(get("/whoami")
                            .with(authentication(
                                    new UsernamePasswordAuthenticationToken(
                                            7L, null, AuthorityUtils.NO_AUTHORITIES))))
                    .andExpect(status().isOk())
                    .andExpect(content().string("7"));
        }

        @Test
        @DisplayName("Should reject request and handle missing authentication appropriately")
        void shouldFailWhenNotAuthenticated() throws Exception {
            // given & when & then
            mockMvc.perform(get("/whoami"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
