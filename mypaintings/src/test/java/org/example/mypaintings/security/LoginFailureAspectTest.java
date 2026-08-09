package org.example.mypaintings.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.mypaintings.security.RateLimitingAspectTest.setRequestIp;
import static org.mockito.Mockito.*;

class LoginFailureAspectTest {

    private static final String IP = "203.0.113.7";
    private static final String METHOD = "AuthService.login()";

    private MutableClock clock;
    private LoginFailureAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private LoginRateLimited annotation;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        aspect = new LoginFailureAspect(clock);
        joinPoint = mock(ProceedingJoinPoint.class);
        Signature sig = mock(Signature.class);
        when(sig.toShortString()).thenReturn(METHOD);
        when(joinPoint.getSignature()).thenReturn(sig);

        annotation = mock(LoginRateLimited.class);
        when(annotation.maxAttempts()).thenReturn(3);

        setRequestIp(IP);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("Constructor Initialization")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize default constructor with UTC system clock")
        void defaultConstructor_shouldInitializeWithSystemUtcClock() {
            // when
            LoginFailureAspect defaultAspect = new LoginFailureAspect();

            // then
            assertThat(defaultAspect).isNotNull();
        }
    }

    @Nested
    @DisplayName("Happy Path & Success Handling")
    class HappyPathTests {

        @Test
        @DisplayName("Should proceed and return result on successful invocation")
        void shouldProceedAndReturnResultOnSuccess() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("jwt-token");

            // when
            Object result = aspect.handleLoginAttempts(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("jwt-token");
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("Should not trigger lockout initially when attempts succeed")
        void shouldNotLockOutInitially() throws Throwable {
            // given
            when(joinPoint.proceed()).thenReturn("ok");

            // when
            Object result = aspect.handleLoginAttempts(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should clear failure counts and lockout state after successful attempt")
        void shouldClearFailureStateAfterSuccess() throws Throwable {
            // given
            doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();
            for (int i = 0; i < 2; i++) {
                try {
                    aspect.handleLoginAttempts(joinPoint, annotation);
                } catch (Exception ignored) {
                }
            }

            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn(METHOD);
            when(joinPoint.getSignature()).thenReturn(sig);
            when(joinPoint.proceed()).thenReturn("token");

            // when
            aspect.handleLoginAttempts(joinPoint, annotation);

            // then
            reset(joinPoint);
            when(joinPoint.getSignature()).thenReturn(sig);
            doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();

            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Failure Increment & Exception Propagation")
    class FailureIncrementTests {

        @Test
        @DisplayName("Should rethrow original exception when failure count is below max attempts")
        void shouldRethrowOriginalExceptionBelowMaxAttempts() throws Throwable {
            // given
            doThrow(new IllegalArgumentException("bad creds")).when(joinPoint).proceed();

            // when & then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bad creds");
        }

        @Test
        @DisplayName("Should rethrow original exception up to max attempts minus one")
        void shouldRethrowUpToMaxMinusOne() throws Throwable {
            // given
            doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();

            // when & then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should propagate unexpected runtime exceptions directly")
        void shouldPropagateUnexpectedRuntimeExceptions() throws Throwable {
            // given
            doThrow(new IllegalStateException("db down")).when(joinPoint).proceed();

            // when & then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("db down");
        }
    }

    @Nested
    @DisplayName("Lockout & Rate Limiting Enforcement")
    class LockoutTests {

        @Test
        @DisplayName("Should trigger 429 ResponseStatusException when max attempts reached")
        void shouldLockOutOnMaxAttempts() throws Throwable {
            // given
            doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();

            try { aspect.handleLoginAttempts(joinPoint, annotation); } catch (Exception ignored) {}
            try { aspect.handleLoginAttempts(joinPoint, annotation); } catch (Exception ignored) {}

            // when & then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode().value()).isEqualTo(429);
                        assertThat(rse.getReason()).contains("temporary blocked");
                    });
        }

        @Test
        @DisplayName("Should reject request during active lockout without calling proceed")
        void shouldRejectDuringActiveLockoutWithoutCallingProceed() throws Throwable {
            // given
            lockOut(3);

            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn(METHOD);
            when(joinPoint.getSignature()).thenReturn(sig);

            // when & then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode().value()).isEqualTo(429);
                        assertThat(rse.getReason()).contains("seconds");
                    });

            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("Should reject even valid credentials during active lockout")
        void shouldRejectEvenCorrectPasswordDuringLockout() throws Throwable {
            // given
            lockOut(3);

            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn(METHOD);
            when(joinPoint.getSignature()).thenReturn(sig);
            when(joinPoint.proceed()).thenReturn("token");

            // when & then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(ResponseStatusException.class);
            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("Should allow login attempts after lockout duration expires")
        void shouldAllowAttemptsAfterLockoutExpires() throws Throwable {
            // given
            lockOut(3);
            clock.advance(Duration.ofMinutes(3));

            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn(METHOD);
            when(joinPoint.getSignature()).thenReturn(sig);
            when(joinPoint.proceed()).thenReturn("token");

            // when
            Object result = aspect.handleLoginAttempts(joinPoint, annotation);

            // then
            assertThat(result).isEqualTo("token");
        }

        @Test
        @DisplayName("Should enforce first lockout duration of exactly two minutes")
        void firstLockoutShouldLastTwoMinutes() throws Throwable {
            // given
            lockOut(3);

            // when (after 1 minute)
            clock.advance(Duration.ofMinutes(1));

            // then
            assertThatThrownBy(() -> aspect.handleLoginAttempts(joinPoint, annotation))
                    .isInstanceOf(ResponseStatusException.class);

            // when (after 2 minutes total)
            clock.advance(Duration.ofMinutes(1));
            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn(METHOD);
            when(joinPoint.getSignature()).thenReturn(sig);
            when(joinPoint.proceed()).thenReturn("ok");

            // then
            assertThat(aspect.handleLoginAttempts(joinPoint, annotation)).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("Request Isolation")
    class IsolationTests {

        @Test
        @DisplayName("Should isolate lockout state per client IP address")
        void shouldIsolateFailuresPerIp() throws Throwable {
            // given
            doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();
            lockOut(3);

            // when
            setRequestIp("198.51.100.42");
            reset(joinPoint);
            Signature sig = mock(Signature.class);
            when(sig.toShortString()).thenReturn(METHOD);
            when(joinPoint.getSignature()).thenReturn(sig);
            when(joinPoint.proceed()).thenReturn("token");

            // then
            assertThat(aspect.handleLoginAttempts(joinPoint, annotation)).isEqualTo("token");
        }

        @Test
        @DisplayName("Should isolate lockout state per target method")
        void shouldIsolateFailuresPerMethod() throws Throwable {
            // given
            doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();
            lockOut(3);

            // when
            Signature otherSig = mock(Signature.class);
            when(otherSig.toShortString()).thenReturn("AuthService.refresh()");
            ProceedingJoinPoint otherJp = mock(ProceedingJoinPoint.class);
            when(otherJp.getSignature()).thenReturn(otherSig);
            when(otherJp.proceed()).thenReturn("new-token");

            // then
            assertThat(aspect.handleLoginAttempts(otherJp, annotation)).isEqualTo("new-token");
        }
    }

    @Nested
    @DisplayName("Exponential Backoff Calculation")
    class BackoffCalculationTests {

        @Test
        @DisplayName("Should calculate 2 minutes backoff for first lockout")
        void firstLockoutShouldBeTwoMinutes() {
            // when & then
            assertThat(LoginFailureAspect.computeBackoffMinutes(3, 3)).isEqualTo(2);
        }

        @Test
        @DisplayName("Should calculate 4 minutes backoff for second lockout")
        void secondLockoutShouldBeFourMinutes() {
            // when & then
            assertThat(LoginFailureAspect.computeBackoffMinutes(4, 3)).isEqualTo(4);
        }

        @Test
        @DisplayName("Should calculate 8 minutes backoff for third lockout")
        void thirdLockoutShouldBeEightMinutes() {
            // when & then
            assertThat(LoginFailureAspect.computeBackoffMinutes(5, 3)).isEqualTo(8);
        }

        @Test
        @DisplayName("Should cap maximum backoff duration at 64 minutes")
        void shouldCapAt64Minutes() {
            // when & then
            assertThat(LoginFailureAspect.computeBackoffMinutes(10, 3)).isEqualTo(64);
            assertThat(LoginFailureAspect.computeBackoffMinutes(100, 3)).isEqualTo(64);
        }

        @Test
        @DisplayName("Should never return backoff duration less than two minutes")
        void shouldNeverReturnLessThanTwoMinutes() {
            // when & then
            assertThat(LoginFailureAspect.computeBackoffMinutes(1, 10)).isEqualTo(2);
        }
    }

    private void lockOut(int maxAttempts) throws Throwable {
        doThrow(new IllegalArgumentException("bad")).when(joinPoint).proceed();

        int illegalArgumentCount = 0;
        int responseStatusCount = 0;

        for (int i = 1; i <= maxAttempts; i++) {
            try {
                aspect.handleLoginAttempts(joinPoint, annotation);
                org.assertj.core.api.Assertions.fail(
                        "Expected exception on attempt " + i + " but none was thrown");
            } catch (IllegalArgumentException e) {
                illegalArgumentCount++;
            } catch (ResponseStatusException e) {
                responseStatusCount++;
            }
        }

        assertThat(illegalArgumentCount).isEqualTo(maxAttempts - 1);
        assertThat(responseStatusCount).isEqualTo(1);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}