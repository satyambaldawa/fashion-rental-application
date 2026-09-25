package com.fashionrental.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_LENGTH = 45;

    private ClientIpResolver() {
    }

    /**
     * Contract warning: the returned value is attacker-controlled when the request arrives
     * through a proxy that forwards a client-supplied X-Forwarded-For. Use it for abuse
     * throttling only — never for authorization.
     */
    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return truncate(request.getRemoteAddr());
        }
        String firstHop = forwardedFor.split(",")[0].trim();
        return firstHop.isEmpty() ? truncate(request.getRemoteAddr()) : truncate(firstHop);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
