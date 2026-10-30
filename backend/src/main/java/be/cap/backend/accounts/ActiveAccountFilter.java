package be.cap.backend.accounts;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Check current activation on private requests, including a login racing deactivation. */
final class ActiveAccountFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    ActiveAccountFilter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath();
        if(path.equals("/app/") || path.equals("/app/index.html"))return false;
        return !path.startsWith("/api/") || java.util.Set.of("/api/health","/api/accounts/login","/api/accounts/csrf","/api/accounts/logout","/api/accounts/accept").contains(path);
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
            && jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE email=? AND active=TRUE",Integer.class,auth.getName())==0) {
            new SecurityContextLogoutHandler().logout(request,response,auth);
            response.setHeader("Cache-Control","no-store");
            if(request.getServletPath().startsWith("/app/")){response.sendRedirect("/accounts/index.html");return;}
            response.setStatus(401);response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Je account is gedeactiveerd. Neem contact op met de beheerder.\"}");return;
        }
        chain.doFilter(request,response);
    }
}
