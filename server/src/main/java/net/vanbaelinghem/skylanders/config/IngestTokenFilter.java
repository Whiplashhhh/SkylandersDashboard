package net.vanbaelinghem.skylanders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared-token check on {@code /api/ingest} (SPEC.md §9).
 *
 * <p>Defence in depth only — network access is already restricted to the tailnet. A full auth
 * system would be disproportionate for a single user, but an unauthenticated write endpoint on a
 * shared network would not be.
 */
public class IngestTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IngestTokenFilter.class);

    private final byte[] expected;

    public IngestTokenFilter(String expectedToken) {
        this.expected = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String presented = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length())
                : "";
        // Constant-time comparison: the token is short and an attacker on the tailnet could
        // otherwise time their way to it.
        if (!MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Ingestion refusee : token absent ou invalide ({})", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"token d'ingestion invalide\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
