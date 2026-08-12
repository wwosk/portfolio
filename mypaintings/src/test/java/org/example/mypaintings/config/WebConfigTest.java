package org.example.mypaintings.config;

import org.example.mypaintings.auth.CurrentUserArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebConfigTest {

    private CurrentUserArgumentResolver resolver;
    private WebConfig webConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resolver = mock(CurrentUserArgumentResolver.class);
        webConfig = new WebConfig(resolver);
        ReflectionTestUtils.setField(webConfig, "uploadPath", tempDir.toString());
    }

    @Nested
    @DisplayName("Argument Resolvers Configuration")
    class ArgumentResolversTests {

        @Test
        @DisplayName("Should successfully add CurrentUserArgumentResolver to resolvers list")
        void shouldAddArgumentResolver() {
            // given
            List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

            // when
            webConfig.addArgumentResolvers(resolvers);

            // then
            assertThat(resolvers)
                    .hasSize(1)
                    .containsExactly(resolver);
        }
    }

    @Nested
    @DisplayName("Resource Handlers Configuration")
    class ResourceHandlersTests {

        @Test
        @DisplayName("Should register resource handler for uploads with a valid absolute file URI")
        void shouldRegisterResourceHandlerWithAbsoluteUri() {
            // given
            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
            var registration = mock(
                    org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration.class);
            when(registry.addResourceHandler("/uploads/**")).thenReturn(registration);

            // when
            webConfig.addResourceHandlers(registry);

            // then
            verify(registry).addResourceHandler("/uploads/**");
            verify(registration).addResourceLocations(argThat((String uri) ->
                    uri.startsWith("file:") && uri.endsWith("/")
            ));
        }

        @Test
        @DisplayName("Should correctly resolve relative or custom upload paths into proper file URIs")
        void shouldHandleRelativeOrCustomUploadPath() {
            ReflectionTestUtils.setField(webConfig, "uploadPath", "./custom-uploads");

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
            var registration = mock(
                    org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration.class);
            when(registry.addResourceHandler(anyString())).thenReturn(registration);

            // when
            webConfig.addResourceHandlers(registry);

            // then
            verify(registration).addResourceLocations(argThat((String uri) ->
                    uri.startsWith("file:") && uri.contains("custom-uploads")
            ));
        }

        @Test
        @DisplayName("Should build valid URI matching temporary path configured in properties")
        void shouldBuildValidUriFromConfiguredPath(@TempDir Path anotherDir) {
            // given
            ReflectionTestUtils.setField(webConfig, "uploadPath", anotherDir.toString());

            ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
            var registration = mock(
                    org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration.class);
            when(registry.addResourceHandler(anyString())).thenReturn(registration);

            // when
            webConfig.addResourceHandlers(registry);

            // then
            verify(registration).addResourceLocations(anotherDir.toUri().toString());
        }
    }
}