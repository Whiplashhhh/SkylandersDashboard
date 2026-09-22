package net.vanbaelinghem.skylanders.bridge;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class BridgeSecurity {
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> bridgeTokenFilter(
            @Value("${skylanders.bridge.connector-token:}") String connectorToken,
            @Value("${skylanders.bridge.control-token:}") String controlToken) {
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                                       FilterChain chain) throws ServletException, IOException {
                String expected = req.getRequestURI().equals(req.getContextPath() + "/api/bridge/exchange")
                        ? connectorToken : controlToken;
                String header = req.getHeader("Authorization");
                String supplied = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
                if (expected.isBlank() || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"PORTAL_AUTH_REQUIRED\"}");
                    return;
                }
                res.setHeader("Cache-Control", "no-store");
                chain.doFilter(req, res);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.addUrlPatterns("/api/bridge", "/api/bridge/*");
        registration.setName("bridgeTokenFilter");
        return registration;
    }
}
