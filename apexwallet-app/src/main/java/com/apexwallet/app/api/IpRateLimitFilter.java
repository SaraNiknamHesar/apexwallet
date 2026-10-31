package com.apexwallet.app.api;

import com.apexwallet.app.config.ApexProperties;
import com.apexwallet.app.security.WindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Component
public class IpRateLimitFilter extends OncePerRequestFilter {

    private final WindowRateLimiter limiter;
    private final ApexProperties properties;
    private final Clock clock;

    public IpRateLimitFilter(WindowRateLimiter limiter, ApexProperties properties, Clock clock) {
        this.limiter = limiter;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/transfers")) {
            String ip = request.getRemoteAddr();
            if (!limiter.allow("ip:" + ip, properties.getTransfersPerMinute() * 2, Duration.ofMinutes(1), clock.instant())) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"detail\":\"too many requests\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
