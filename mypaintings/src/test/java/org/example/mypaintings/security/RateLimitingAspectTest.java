package org.example.mypaintings.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RateLimitingAspectTest {

    private RateLimitingAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        aspect = new RateLimitingAspect();
        joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("SomeService.someMethod()");
        when(joinPoint.getSignature()).thenReturn(signature);

        setRequestIp("203.0.113.7");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("Happy Path & Capacity Enforcement")
    class HappyPathTests {

        @Test
        @DisplayName("Should proceed successfully when request count is under rate limit capacity")
        void shouldProceedWhenUnderLimit() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("result");

            // when
            Object result = aspect.limitRate(joinPoint);

            // then
            assertThat(result).isEqualTo("result");
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("Should allow exactly capacity number of requests without throwing exception")
        void shouldAllowExactlyCapacityRequests() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");

            // when
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }

            // then
            verify(joinPoint, times(100)).proceed();
        }

        @Test
        @DisplayName("Should reject request with 429 status when capacity limit is exceeded")
        void shouldRejectWhenOverCapacity() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }

            // when & then
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode().value()).isEqualTo(429);
                        assertThat(rse.getReason()).contains("Too many requests");
                    });

            verify(joinPoint, times(100)).proceed();
        }

        @Test
        @DisplayName("Should not call proceed target method when rate limit is exceeded")
        void shouldNotCallProceedWhenLimitExceeded() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }

            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn("SomeService.someMethod()");
            when(joinPoint.getSignature()).thenReturn(sig);

            // when & then
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);

            verify(joinPoint, never()).proceed();
        }
    }

    @Nested
    @DisplayName("Bucket Isolation (IP & Method)")
    class IsolationTests {

        @Test
        @DisplayName("Should isolate rate-limiting buckets independently per client IP address")
        void shouldIsolateBucketsPerIp() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");
            setRequestIp("1.1.1.1");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);

            // when
            setRequestIp("2.2.2.2");

            // then
            assertThat(aspect.limitRate(joinPoint)).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should isolate rate-limiting buckets independently per target method signature")
        void shouldIsolateBucketsPerMethod() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);

            // when
            ProceedingJoinPoint otherJoinPoint = mock(ProceedingJoinPoint.class);
            Signature otherSig = mock(Signature.class);
            when(otherSig.toShortString()).thenReturn("OtherService.otherMethod()");
            when(otherJoinPoint.getSignature()).thenReturn(otherSig);
            when(otherJoinPoint.proceed()).thenReturn("other");

            // then
            assertThat(aspect.limitRate(otherJoinPoint)).isEqualTo("other");
        }
    }

    @Nested
    @DisplayName("User Authentication & Client Key Resolution")
    class AuthenticationResolutionTests {

        @Test
        @DisplayName("Should use authenticated user ID bucket when user details contain Long id")
        void shouldUseUserBucketWhenDetailsIsLong() throws Throwable {
            // given
            authenticateUser(42L);
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);

            // when
            authenticateUser(99L);

            // then
            assertThat(aspect.limitRate(joinPoint)).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should not share bucket between different authenticated users sharing the same NAT IP")
        void shouldNotShareBucketBetweenUsersEvenFromSameIp() throws Throwable {
            // given
            setRequestIp("10.0.0.1");
            authenticateUser(1L);
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);

            // when
            authenticateUser(2L);

            // then
            assertThat(aspect.limitRate(joinPoint)).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should fall back to IP-based bucket when request is unauthenticated")
        void shouldFallBackToIpWhenNotAuthenticated() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }

            // when & then
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("Should fall back to IP-based bucket for anonymous authentication tokens")
        void shouldFallBackToIpForAnonymousAuthentication() throws Throwable {
            // given
            var anonymous = new AnonymousAuthenticationToken(
                    "key", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
            SecurityContextHolder.getContext().setAuthentication(anonymous);
            when(joinPoint.proceed()).thenReturn("ok");
            for (int i = 0; i < 100; i++) {
                aspect.limitRate(joinPoint);
            }

            // when & then
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Nested
    @DisplayName("Exception Propagation & Token Consumption")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should propagate original exception thrown from proceed execution")
        void shouldPropagateExceptionFromProceed() throws Throwable {
            // given
            when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

            // when & then
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");
        }

        @Test
        @DisplayName("Should consume token before proceeding even when proceed throws an exception (DoS protection)")
        void shouldConsumeTokenBeforeProceedingEvenWhenExceptionOccurs() throws Throwable {
            // given
            when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));
            assertThatThrownBy(() -> aspect.limitRate(joinPoint))
                    .isInstanceOf(IllegalStateException.class);

            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn("SomeService.someMethod()");
            when(joinPoint.getSignature()).thenReturn(sig);
            when(joinPoint.proceed()).thenReturn("ok");

            // when & then
            assertThat(aspect.limitRate(joinPoint)).isEqualTo("ok");
        }
    }

    private void authenticateUser(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, null, AuthorityUtils.NO_AUTHORITIES);
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    protected static void setRequestIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}