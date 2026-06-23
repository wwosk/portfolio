package org.example.mypaintings.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.example.mypaintings.security.SecurityUtils.getClientIP;

@Aspect
@Component
public class RateLimitingAspect {

    private static final int BUCKET_CAPACITY = 100;
    private static final int REFILL_TOKENS = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    private Bucket createNewBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(BUCKET_CAPACITY,
                        Refill.intervally(REFILL_TOKENS, REFILL_PERIOD)))
                .build();
    }

    @Around("@annotation(org.example.mypaintings.security.RateLimited)")
    public Object limitRate(@NonNull ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        String bucketKey = methodName + "_" + resolveClientKey();

        Bucket bucket = buckets.get(bucketKey, _ -> createNewBucket());

        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests, slow down!");
        }

        return joinPoint.proceed();
    }

    private String resolveClientKey() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getDetails() instanceof Long userId) {
            return "user_" + userId;
        }

        return "ip_" + getClientIP();
    }
}