package org.example.mypaintings.painting.attachedimages;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileUrlService Test Suite")
class FileUrlServiceTest {

    private FileUrlService fileUrlService;

    @BeforeEach
    void setUp() {
        fileUrlService = new FileUrlService();
        String testBaseUrl = "https://mypaintings.app";
        ReflectionTestUtils.setField(fileUrlService, "baseUrl", testBaseUrl);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ------------------------------------------------------------------
    // buildUrl
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("buildUrl")
    class BuildUrl {

        @Test
        @DisplayName("falls back to base path configuration when no request context exists")
        void shouldFallbackToBasePathWhenNoRequestContext() {
            // given
            RequestContextHolder.resetRequestAttributes();
            String fileKey = "images/painting-1.jpg";

            // when
            String resultUrl = fileUrlService.buildUrl(fileKey);

            // then
            assertThat(resultUrl).isEqualTo("https://mypaintings.app/uploads/images/painting-1.jpg");
        }

        @Test
        @DisplayName("builds URL from current request context when request exists")
        void shouldBuildUrlFromCurrentContextPathWhenRequestExists() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setScheme("http");
            request.setServerName("localhost");
            request.setServerPort(8080);
            request.setContextPath("");

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            String fileKey = "images/painting-2.jpg";

            // when
            String resultUrl = fileUrlService.buildUrl(fileKey);

            // then
            assertThat(resultUrl).isEqualTo("http://localhost:8080/uploads/images/painting-2.jpg");
        }

        @Test
        @DisplayName("handles null fileKey gracefully using fallback baseUrl")
        void shouldHandleNullFileKeyWithFallback() {
            // given
            RequestContextHolder.resetRequestAttributes();

            // when
            String resultUrl = fileUrlService.buildUrl(null);

            // then
            assertThat(resultUrl).isEqualTo("https://mypaintings.app/uploads/null");
        }
    }
}
