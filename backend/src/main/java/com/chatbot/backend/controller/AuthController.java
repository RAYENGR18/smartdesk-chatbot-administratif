package com.chatbot.backend.controller;

import com.chatbot.backend.model.AuthResponse;
import com.chatbot.backend.model.LoginRequest;
import com.chatbot.backend.model.RegisterRequest;
import com.chatbot.backend.repository.FiliereRepository;
import com.chatbot.backend.service.AuthService;
import com.chatbot.backend.util.Niveaux;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final FiliereRepository filiereRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.register(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }

    /** Filières proposées dans le formulaire d'inscription (public : id, code et nom seulement). */
    @GetMapping("/filieres")
    public List<Map<String, Object>> filieres() {
        return filiereRepository.findAll(Sort.by("nom")).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("code", f.getCode());
            m.put("nom", f.getNom());
            return m;
        }).toList();
    }

    /** Niveaux d'études proposés dans le formulaire d'inscription. */
    @GetMapping("/niveaux")
    public List<Map<String, String>> niveaux() {
        return Niveaux.tous().entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "libelle", e.getValue()))
                .toList();
    }
}
