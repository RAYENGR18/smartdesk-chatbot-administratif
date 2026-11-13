package com.chatbot.backend.controller;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.DocumentRequest;
import com.chatbot.backend.repository.DocumentRepository;
import com.chatbot.backend.security.AuthenticatedUser;
import com.chatbot.backend.security.CurrentUser;
import com.chatbot.backend.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;

    // GÉNÉRER UN DOCUMENT — toujours pour l'utilisateur connecté
    @PostMapping("/generate")
    public ResponseEntity<DocumentRequest> generateDocument(@RequestBody Map<String, String> request)
            throws Exception {
        AuthenticatedUser me = CurrentUser.get();
        DocumentRequest doc = documentService.generateDocument(
                request.get("documentType"),
                request.get("fullName"),
                request.get("additionalInfo"),
                request.get("format"),
                me.username()
        );
        return ResponseEntity.ok(doc);
    }

    // TÉLÉCHARGER UN DOCUMENT (propriétaire ou admin)
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        DocumentRequest doc = documentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Document non trouvé."));

        Long ownerId = doc.getUser() != null ? doc.getUser().getId() : null;
        CurrentUser.requireSelfOrAdmin(ownerId);

        File file = documentService.resolveFile(doc);
        boolean pdf = "PDF".equals(doc.getFormat());
        MediaType contentType = pdf
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        String downloadName = doc.getDocumentType() + "_" + doc.getId() + (pdf ? ".pdf" : ".docx");

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(downloadName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    // LISTE DES DOCUMENTS D'UN UTILISATEUR (soi-même ou admin)
    @GetMapping("/list/{username}")
    public ResponseEntity<List<DocumentRequest>> getUserDocuments(@PathVariable String username) {
        CurrentUser.requireSelfOrAdmin(username);
        return ResponseEntity.ok(documentService.getUserDocuments(username));
    }
}
