package com.chatbot.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-base")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:61748"})
public class KnowledgeBaseController {

    @Value("${nlp.url:http://localhost:8000}")
    private String nlpUrl;

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        RestTemplate rt = new RestTemplate(factory);
        rt.getMessageConverters().stream()
            .filter(c -> c instanceof StringHttpMessageConverter)
            .forEach(c -> ((StringHttpMessageConverter) c)
                .setDefaultCharset(StandardCharsets.UTF_8));
        return rt;
    }

    // ─── Liste des documents (avec audience) ──────────────────────
    @GetMapping("/documents")
    public ResponseEntity<String> listDocuments(
            @RequestParam(defaultValue = "student") String audience) {
        try {
            return passThrough(nlpUrl + "/rag/documents?audience=" + audience);
        } catch (Exception e) {
            return err503();
        }
    }

    // ─── Statut RAG ───────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        try {
            return passThrough(nlpUrl + "/rag/status");
        } catch (Exception e) {
            return err503();
        }
    }

    // ─── Upload document (avec audience) ─────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "student") String audience) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate().postForEntity(
                nlpUrl + "/rag/upload?audience=" + audience, request, String.class);
            return utf8Json(resp);
        } catch (Exception e) {
            return err500("Erreur upload : " + e.getMessage());
        }
    }

    // ─── Supprimer un document (avec audience) ────────────────────
    @DeleteMapping("/documents/{filename}")
    public ResponseEntity<String> deleteDocument(
            @PathVariable String filename,
            @RequestParam(defaultValue = "student") String audience) {
        try {
            restTemplate().delete(nlpUrl + "/rag/documents/" + filename + "?audience=" + audience);
            return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body("{\"message\":\"Document supprimé\",\"status\":\"success\"}");
        } catch (Exception e) {
            return err500("Erreur suppression : " + e.getMessage());
        }
    }

    // ─── Lire contenu TXT (avec audience) ────────────────────────
    @GetMapping("/documents/{filename}/content")
    public ResponseEntity<String> getContent(
            @PathVariable String filename,
            @RequestParam(defaultValue = "student") String audience) {
        try {
            ResponseEntity<String> resp = restTemplate().getForEntity(
                nlpUrl + "/rag/documents/" + filename + "/content?audience=" + audience,
                String.class);
            return utf8Json(resp);
        } catch (Exception e) {
            return err500("Erreur lecture : " + e.getMessage());
        }
    }

    // ─── Modifier contenu TXT (avec audience) ─────────────────────
    @PutMapping("/documents/{filename}/content")
    public ResponseEntity<String> updateContent(
            @PathVariable String filename,
            @RequestBody Map<String, String> body,
            @RequestParam(defaultValue = "student") String audience) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = restTemplate().exchange(
                nlpUrl + "/rag/documents/" + filename + "/content?audience=" + audience,
                HttpMethod.PUT, request, String.class);
            return utf8Json(resp);
        } catch (Exception e) {
            return err500("Erreur modification : " + e.getMessage());
        }
    }

    // ─── Réindexer (avec audience) ────────────────────────────────
    @PostMapping("/reindex")
    public ResponseEntity<String> reindex(
            @RequestParam(defaultValue = "all") String audience) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate().postForEntity(
                nlpUrl + "/rag/index?audience=" + audience, request, String.class);
            return utf8Json(resp);
        } catch (Exception e) {
            return err500("Erreur réindexation : " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private ResponseEntity<String> passThrough(String url) {
        ResponseEntity<String> resp = restTemplate().getForEntity(url, String.class);
        return utf8Json(resp);
    }

    private ResponseEntity<String> utf8Json(ResponseEntity<String> resp) {
        return ResponseEntity.status(resp.getStatusCode())
            .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
            .body(resp.getBody());
    }

    private ResponseEntity<String> err503() {
        return ResponseEntity.status(503)
            .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
            .body("{\"error\":\"Service NLP indisponible\"}");
    }

    private ResponseEntity<String> err500(String msg) {
        String safe = msg.replace("\"", "'");
        return ResponseEntity.status(500)
            .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
            .body("{\"error\":\"" + safe + "\"}");
    }
}
