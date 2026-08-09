package org.example.mypaintings.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("getClientIP")
    class GetClientIpTests {

        @Test
        @DisplayName("Should return 'unknown' when request attributes are not present in context")
        void shouldReturnUnknownWhenNoRequestAttributes() {
            // given
            RequestContextHolder.resetRequestAttributes();

            // when
            String clientIp = SecurityUtils.getClientIP();

            // then
            assertThat(clientIp).isEqualTo("unknown");
        }

        @Test
        @DisplayName("Should return correct remote address when request attributes are present")
        void shouldReturnClientIpWhenRequestAttributesArePresent() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.50");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            // when
            String clientIp = SecurityUtils.getClientIP();

            // then
            assertThat(clientIp).isEqualTo("192.168.1.50");
        }
    }
}