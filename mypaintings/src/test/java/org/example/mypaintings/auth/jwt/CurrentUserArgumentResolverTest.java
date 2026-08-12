package org.example.mypaintings.auth.jwt;

import org.example.mypaintings.auth.CurrentUserArgumentResolver;
import org.example.mypaintings.auth.CurrentUserId;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Supports Parameter Validation")
    class SupportsParameterTests {

        @Test
        @DisplayName("Should support parameter annotated with @CurrentUserId of type Long")
        void shouldSupportAnnotatedLong() throws Exception {
            // given
            MethodParameter param = parameterOf("annotatedLong", Long.class);

            // when
            boolean supports = resolver.supportsParameter(param);

            // then
            assertThat(supports).isTrue();
        }

        @Test
        @DisplayName("Should not support parameter lacking @CurrentUserId annotation")
        void shouldNotSupportUnannotatedLong() throws Exception {
            // given
            MethodParameter param = parameterOf("unannotatedLong", Long.class);

            // when
            boolean supports = resolver.supportsParameter(param);

            // then
            assertThat(supports).isFalse();
        }

        @Test
        @DisplayName("Should not support parameter with @CurrentUserId of primitive long type")
        void shouldNotSupportAnnotatedPrimitiveLong() throws Exception {
            // given
            MethodParameter param = parameterOf("annotatedPrimitiveLong", long.class);

            // when
            boolean supports = resolver.supportsParameter(param);

            // then
            assertThat(supports).isFalse();
        }

        @Test
        @DisplayName("Should not support parameter with @CurrentUserId of String type")
        void shouldNotSupportAnnotatedString() throws Exception {
            // given
            MethodParameter param = parameterOf("annotatedString", String.class);

            // when
            boolean supports = resolver.supportsParameter(param);

            // then
            assertThat(supports).isFalse();
        }

        @Test
        @DisplayName("Should not support parameter with @CurrentUserId of Integer type")
        void shouldNotSupportAnnotatedInteger() throws Exception {
            // given
            MethodParameter param = parameterOf("annotatedInteger", Integer.class);

            // when
            boolean supports = resolver.supportsParameter(param);

            // then
            assertThat(supports).isFalse();
        }
    }

    @Nested
    @DisplayName("Resolve Argument Validation")
    class ResolveArgumentTests {

        @Test
        @DisplayName("Should return user ID from principal when authentication is valid")
        void shouldReturnUserIdFromPrincipal() {
            // given
            setAuthentication(42L);

            // when
            Object result = resolve();

            // then
            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when authentication is null")
        void shouldThrowWhenAuthenticationIsNull() {
            // given
            SecurityContextHolder.clearContext();

            // when & then
            assertThatThrownBy(CurrentUserArgumentResolverTest.this::resolve)
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("No authenticated user");
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when authentication is not authenticated")
        void shouldThrowWhenAuthenticationIsNotAuthenticated() {
            // given
            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(false);
            SecurityContextHolder.getContext().setAuthentication(auth);

            // when & then
            assertThatThrownBy(CurrentUserArgumentResolverTest.this::resolve)
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("No authenticated user");
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when principal is not of type Long")
        void shouldThrowWhenPrincipalIsNotLong() {
            // given
            setAuthentication("not-a-long");

            // when & then
            assertThatThrownBy(CurrentUserArgumentResolverTest.this::resolve)
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("valid user ID");
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException for anonymous authentication token")
        void shouldThrowForAnonymousAuthentication() {
            // given
            var anonymous = new AnonymousAuthenticationToken(
                    "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
            SecurityContextHolder.getContext().setAuthentication(anonymous);

            // when & then
            assertThatThrownBy(CurrentUserArgumentResolverTest.this::resolve)
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("valid user ID");
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when principal is Integer instead of Long")
        void shouldThrowWhenPrincipalIsIntegerInsteadOfLong() {
            // given
            setAuthentication(42);

            // when & then
            assertThatThrownBy(CurrentUserArgumentResolverTest.this::resolve)
                    .isInstanceOf(UnauthorizedAccessException.class)
                    .hasMessageContaining("valid user ID");
        }
    }

    private Object resolve() {
        return resolver.resolveArgument(null, null, null, null);
    }

    private static void setAuthentication(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, AuthorityUtils.NO_AUTHORITIES));
    }

    private MethodParameter parameterOf(String methodName, Class<?> paramType) throws Exception {
        Method method = Target.class.getDeclaredMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    static class Target {
        void annotatedLong(@CurrentUserId Long userId) {}
        void unannotatedLong(Long userId) {}
        void annotatedPrimitiveLong(@CurrentUserId long userId) {}
        void annotatedString(@CurrentUserId String userId) {}
        void annotatedInteger(@CurrentUserId Integer userId) {}
    }
}
