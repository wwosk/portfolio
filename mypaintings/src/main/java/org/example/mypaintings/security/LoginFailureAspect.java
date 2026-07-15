package org.example.mypaintings.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.example.mypaintings.security.SecurityUtils.getClientIP;

@Aspect
@Component
public class LoginFailureAspect {

    private static final int MAX_BACKOFF_EXPONENT = 6;

    private final Clock clock;

    private final Cache<String, Integer> failureCounts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(10_000)
            .build();

    private final Cache<String, Instant> lockouts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(10_000)
            .build();


    public LoginFailureAspect() {
        this(Clock.systemUTC());
    }

    LoginFailureAspect(Clock clock) {
        this.clock = clock;
    }

    @Around("@annotation(loginRateLimited)")
    public Object handleLoginAttempts(ProceedingJoinPoint joinPoint,
                                      LoginRateLimited loginRateLimited) throws Throwable {
        String clientKey = buildClientKey(joinPoint);

        if (isLockedOut(clientKey)) {
            throw lockoutException(clientKey);
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            return handleFailure(clientKey, loginRateLimited, e);
        }

        failureCounts.invalidate(clientKey);
        lockouts.invalidate(clientKey);
        return result;
    }


    private boolean isLockedOut(String clientKey) {
        Instant lockoutUntil = lockouts.getIfPresent(clientKey);
        if (lockoutUntil == null) {
            return false;
        }
        if (clock.instant().isBefore(lockoutUntil)) {
            return true;
        }
        lockouts.invalidate(clientKey);
        return false;
    }

    private @NonNull ResponseStatusException lockoutException(String clientKey) {
        Instant lockoutUntil = lockouts.getIfPresent(clientKey);
        long secondsLeft = lockoutUntil == null
                ? 0
                : Math.max(0, Duration.between(clock.instant(), lockoutUntil).getSeconds());
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many login attempts try in " + secondsLeft + " seconds.");
    }


    private Object handleFailure(String clientKey,
                                 LoginRateLimited annotation,
                                 Exception original) throws Throwable {
        int attempts = failureCounts.asMap().merge(clientKey, 1, Integer::sum);
        int maxAttempts = annotation.maxAttempts();

        if (attempts < maxAttempts) {
            throw original;
        }

        long backoffMinutes = computeBackoffMinutes(attempts, maxAttempts);
        Instant lockUntil = clock.instant().plus(Duration.ofMinutes(backoffMinutes));
        lockouts.put(clientKey, lockUntil);

        throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many login attempts. Account temporary blocked for "
                        + backoffMinutes + " minutes.");
    }


    static long computeBackoffMinutes(int attempts, int maxAttempts) {
        int excessAttempts = attempts - maxAttempts + 1;
        int exponent = Math.min(excessAttempts, MAX_BACKOFF_EXPONENT);
        exponent = Math.max(exponent, 1);
        return (long) Math.pow(2, exponent);
    }



    private static String buildClientKey(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        return "login_" + getClientIP() + "_" + methodName;
    }
}