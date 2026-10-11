package app.config;

import app.Repo.UserRepo;
import app.models.User;
import app.services.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepo userRepo, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7).trim();

        try {
            String email = jwtService.extractSubject(token);
            if (email == null || email.isBlank()) {
                SecurityContextHolder.clearContext();
                SecurityConfig.writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT");
                return;
            }

            Optional<User> user = userRepo.findByEmail(email);
            if (user.isEmpty()) {
                SecurityContextHolder.clearContext();
                SecurityConfig.writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.get().getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + user.get().getRole().name()))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            SecurityConfig.writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
