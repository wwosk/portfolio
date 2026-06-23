package org.example.mypaintings.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class SecurityUtils {

    private static final String UNKNOWN = "unknown";

    private SecurityUtils() {
    }

    public static String getClientIP() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return UNKNOWN;
        }

        return attributes.getRequest().getRemoteAddr();
    }
}