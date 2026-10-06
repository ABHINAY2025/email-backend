package com.applyflow.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client address for rate limiting. Render/Vercel sit in front of the app as proxies, so the first hop of
 * {@code X-Forwarded-For} is used when present, otherwise the socket address.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return resolve(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
    }

    static String resolve(String forwardedFor, String remoteAddr) {
        if (forwardedFor != null) {
            int comma = forwardedFor.indexOf(',');
            String first = (comma >= 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
            if (!first.isEmpty() && first.length() <= 64) {
                return first;
            }
        }
        return remoteAddr == null ? "unknown" : remoteAddr;
    }
}
