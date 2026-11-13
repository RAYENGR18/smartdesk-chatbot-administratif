package com.chatbot.backend.config;

import com.chatbot.backend.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sécurité de l'API :
 *  - authentification par token JWT (sans session, sans cookie) ;
 *  - autorisations par rôle (ADMIN, PROFESSEUR, USER = étudiant) ;
 *  - CORS limité aux origines du frontend configurées.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:61748}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Le filtre JWT est ajouté à la chaîne Spring Security : on évite son double enregistrement. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sans cookie : le token est envoyé dans l'en-tête
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // ── Public ─────────────────────────────────────────────
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/filieres", "/api/auth/niveaux").permitAll()
                        // Lecteur RFID : authentifié par sa clé d'appareil (X-Device-Key), pas par JWT
                        .requestMatchers(HttpMethod.POST, "/api/iot/rfid/scan").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/error").permitAll()

                        // ── Administration ────────────────────────────────────
                        .requestMatchers("/api/admin/**").hasRole(ADMIN)
                        .requestMatchers("/api/knowledge-base/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/notes").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/notes/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/notes/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/notifications", "/api/notifications/stats").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/notifications/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/notifications/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/notifications/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/filieres/**", "/api/matieres/**", "/api/salles/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/filieres/**", "/api/matieres/**", "/api/salles/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/filieres/**", "/api/matieres/**", "/api/salles/**").hasRole(ADMIN)
                        .requestMatchers("/api/planning/filiere/**").hasAnyRole(ADMIN, "PROFESSEUR")
                        .requestMatchers("/api/planning/creneaux/**", "/api/planning/generer",
                                "/api/planning/all", "/api/planning/salle/**",
                                "/api/planning/salles-disponibles").hasRole(ADMIN)
                        .requestMatchers("/api/student/filieres", "/api/student/professors",
                                "/api/student/*/name").hasRole(ADMIN)

                        // ── Tout le reste de l'API : utilisateur connecté ─────
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, objectMapper, HttpStatus.UNAUTHORIZED,
                                        "Authentification requise. Veuillez vous reconnecter."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, objectMapper, HttpStatus.FORBIDDEN, "Accès refusé.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper,
                                   HttpStatus status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), body);
    }
}
