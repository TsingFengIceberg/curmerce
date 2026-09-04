package cn.iocoder.yudao.curmerce.agent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Binds tenant and principal for every Agent HTTP request, including read-only endpoints. */
@Component
public class AgentRequestContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/app-api/agent") && !path.startsWith("/internal-api/agent")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AgentRequestContext.bind(request.getHeader("Authorization"), request.getHeader("tenant-id"));
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } finally {
            AgentRequestContext.clear();
        }
    }
}
