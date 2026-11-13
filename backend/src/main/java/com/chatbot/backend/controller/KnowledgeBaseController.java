package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Gestion de la base de connaissances du chatbot (admin uniquement).
 * Relais vers le service NLP, avec validation stricte des noms de fichiers.
 */
@RestController
@RequestMapping("/api/knowledge-base")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private static final Set<String> AUDIENCES = Set.of("student", "student_only", "professor", "all");
    private static final Set<String> EXTENSIONS = Set.of(".txt", ".pdf", ".docx");
    /** Lettres (accents compris), chiffres, espace, '.', '_', '-' ; pas de séparateur de chemin. */
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[\\p{L}\\p{N} ._()-]{1,150}$");
    private static final int MAX_CONTENT_LENGTH = 2_000_000;

    private static final MediaType JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

    private final String nlpUrl;
    private final RestTemplate restTemplate;

    public KnowledgeBaseController(@Value("${app.nlp.url:http://localhost:8000}") String nlpUrl,
                                   @Qualifier("nlpRestTemplate") RestTemplate restTemplate) {
        this.nlpUrl = nlpUrl;
        this.restTemplate = restTemplate;
    }

    // ─── Liste des documents ──────────────────────────────────────
    @GetMapping("/documents")
    public ResponseEntity<String> listDocuments(@RequestParam(defaultValue = "student") String audience) {
        return call(() -> restTemplate.getForEntity(
                uri("/rag/documents", null, audience(audience)), String.class));
    }

    // ─── Statut RAG ───────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return call(() -> restTemplate.getForEntity(uri("/rag/status", null, null), String.class));
    }

    // ─── Upload d'un document ─────────────────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(defaultValue = "student") String audience) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Le fichier est vide.");
        }
        String filename = filename(file.getOriginalFilename());
        String aud = audience(audience);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Fichier illisible.");
        }
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return call(() -> restTemplate.postForEntity(
                uri("/rag/upload", null, aud), new HttpEntity<>(body, headers), String.class));
    }

    // ─── Supprimer un document ────────────────────────────────────
    @DeleteMapping("/documents/{filename}")
    public ResponseEntity<String> deleteDocument(@PathVariable String filename,
                                                 @RequestParam(defaultValue = "student") String audience) {
        String safe = filename(filename);
        String aud = audience(audience);
        return call(() -> restTemplate.exchange(
                uri("/rag/documents/{filename}", safe, aud), HttpMethod.DELETE, null, String.class));
    }

    // ─── Lire le contenu d'un .txt ────────────────────────────────
    @GetMapping("/documents/{filename}/content")
    public ResponseEntity<String> getContent(@PathVariable String filename,
                                             @RequestParam(defaultValue = "student") String audience) {
        String safe = txtFilename(filename);
        String aud = audience(audience);
        return call(() -> restTemplate.getForEntity(
                uri("/rag/documents/{filename}/content", safe, aud), String.class));
    }

    // ─── Modifier le contenu d'un .txt ────────────────────────────
    @PutMapping("/documents/{filename}/content")
    public ResponseEntity<String> updateContent(@PathVariable String filename,
                                                @RequestBody Map<String, String> body,
                                                @RequestParam(defaultValue = "student") String audience) {
        String safe = txtFilename(filename);
        String aud = audience(audience);
        String content = body.get("content");
        if (content == null || content.length() > MAX_CONTENT_LENGTH) {
            throw ApiException.badRequest("Contenu absent ou trop volumineux.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JSON_UTF8);
        return call(() -> restTemplate.exchange(
                uri("/rag/documents/{filename}/content", safe, aud), HttpMethod.PUT,
                new HttpEntity<>(Map.of("content", content), headers), String.class));
    }

    // ─── Réindexer ────────────────────────────────────────────────
    @PostMapping("/reindex")
    public ResponseEntity<String> reindex(@RequestParam(defaultValue = "all") String audience) {
        String aud = audience(audience);
        return call(() -> restTemplate.postForEntity(uri("/rag/index", null, aud), null, String.class));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private URI uri(String path, String filename, String audience) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(nlpUrl).path(path);
        if (audience != null) {
            b.queryParam("audience", audience);
        }
        return filename != null
                ? b.encode().buildAndExpand(Map.of("filename", filename)).toUri()
                : b.encode().build().toUri();
    }

    private static String audience(String audience) {
        String a = audience == null ? "student" : audience.trim().toLowerCase(Locale.ROOT);
        if (!AUDIENCES.contains(a)) {
            throw ApiException.badRequest("Audience invalide.");
        }
        return a;
    }

    private static String filename(String name) {
        if (name == null || !SAFE_FILENAME.matcher(name).matches() || name.contains("..")) {
            throw ApiException.badRequest("Nom de fichier invalide.");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw ApiException.badRequest("Format non supporté (.txt, .pdf ou .docx).");
        }
        return name;
    }

    private static String txtFilename(String name) {
        String safe = filename(name);
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw ApiException.badRequest("Seuls les fichiers .txt peuvent être lus ou modifiés.");
        }
        return safe;
    }

    @FunctionalInterface
    private interface NlpCall {
        ResponseEntity<String> run();
    }

    /** Exécute l'appel au service NLP et renvoie sa réponse JSON telle quelle. */
    private ResponseEntity<String> call(NlpCall nlpCall) {
        try {
            ResponseEntity<String> resp = nlpCall.run();
            return ResponseEntity.status(resp.getStatusCode()).contentType(JSON_UTF8).body(resp.getBody());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).contentType(JSON_UTF8)
                    .body(e.getResponseBodyAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Service NLP injoignable : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(JSON_UTF8)
                    .body("{\"error\":\"Service NLP indisponible\",\"message\":\"Service NLP indisponible\"}");
        }
    }
}
