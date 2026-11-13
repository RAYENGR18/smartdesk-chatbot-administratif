package com.chatbot.backend;

import com.chatbot.backend.model.Filiere;
import com.chatbot.backend.model.Notification;
import com.chatbot.backend.registre.RegistreEtablissement;
import com.chatbot.backend.registre.RegistreEtablissementRepository;
import com.chatbot.backend.registre.TypePersonne;
import com.chatbot.backend.repository.FiliereRepository;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.NotificationRepository;
import com.chatbot.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Vérifie les règles de sécurité de bout en bout (MockMvc + base H2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin-Test-123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RegistreEtablissementRepository registreRepository;
    @Autowired
    private FiliereRepository filiereRepository;

    private String adminToken;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private String login(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Ajoute la personne au registre de l'établissement, inscrit le compte et renvoie son token. */
    private String registerUser(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String cin = "T" + suffix.toUpperCase().replaceAll("[^A-Z0-9]", "0");
        boolean etudiant = role.equals("USER");
        Filiere filiere = filiereTest();

        RegistreEtablissement r = new RegistreEtablissement();
        r.setCin(cin);
        r.setNom("Nom" + suffix);
        r.setPrenom("Prenom");
        r.setType(etudiant ? TypePersonne.ETUDIANT : TypePersonne.PROFESSEUR);
        if (etudiant) {
            r.setFiliere(filiere.getCode());
            r.setNiveau("L1");
        }
        registreRepository.save(r);

        String username = role.toLowerCase() + suffix;
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "username", username,
                "email", username + "@test.local",
                "password", "Motdepasse-123",
                "cin", cin,
                "firstName", "Prenom",
                "lastName", "Nom" + suffix,
                "role", role));
        if (etudiant) {
            body.put("filiereId", filiere.getId());
            body.put("niveau", "L1");
        }
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        return login(username, "Motdepasse-123");
    }

    private Filiere filiereTest() {
        return filiereRepository.findByCode("TESTF").orElseGet(() -> {
            Filiere f = new Filiere();
            f.setCode("TESTF");
            f.setNom("Filiere de test");
            return filiereRepository.save(f);
        });
    }

    private JsonNode me(String token) throws Exception {
        MvcResult res = mvc.perform(get("/api/student/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/reset")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/filieres")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/send").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedOrInvalidTokenIsRejected() throws Exception {
        String forged = Jwts.builder()
                .issuer("smartdesk")
                .subject("admin")
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("une-autre-cle-secrete-de-plus-de-32-caracteres!!"
                        .getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer pas-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanAccessAdminApi() throws Exception {
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbAdmins").value(1));
        // Le hash du mot de passe n'est jamais exposé
        MvcResult res = mvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("$2a$").doesNotContain("password");
    }

    @Test
    void studentCannotUseAdminApi() throws Exception {
        String student = registerUser("USER");
        mvc.perform(get("/api/admin/stats").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/reset").param("confirm", "RESET").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/1/role").header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/knowledge-base/status").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/filieres").header("Authorization", bearer(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"X\",\"code\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notes").header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        // Lecture du catalogue autorisée
        mvc.perform(get("/api/filieres").header("Authorization", bearer(student)))
                .andExpect(status().isOk());
    }

    @Test
    void usersOnlySeeTheirOwnData() throws Exception {
        String alice = registerUser("USER");
        String bob = registerUser("USER");
        JsonNode aliceProfile = me(alice);
        JsonNode bobProfile = me(bob);

        // Profil, historique, documents, notes d'un autre : interdit
        mvc.perform(get("/api/student/profile/" + bobProfile.get("username").asText())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/student/profile/" + bobProfile.get("username").asText())
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/chat/history/" + bobProfile.get("username").asText())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/list/" + bobProfile.get("username").asText())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notes/etudiant/" + bobProfile.get("id").asLong())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());

        // Ses propres données : autorisé
        mvc.perform(get("/api/student/profile/" + aliceProfile.get("username").asText())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notes/etudiant/" + aliceProfile.get("id").asLong())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void chatMessageIsAlwaysAttributedToTheTokenOwner() throws Exception {
        String alice = registerUser("USER");
        JsonNode aliceProfile = me(alice);

        // Tentative d'usurpation : le champ "username" du corps est ignoré
        mvc.perform(post("/api/chat/send").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"bonjour\",\"language\":\"fr\",\"username\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty());

        mvc.perform(get("/api/chat/history/admin").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/chat/history/" + aliceProfile.get("username").asText())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cannotRegisterAsAdminOrReuseIdentity() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", "pirate", "email", "pirate@test.local",
                                "password", "Motdepasse-123", "cin", "12345678",
                                "firstName", "A", "lastName", "B", "role", "ADMIN"))))
                .andExpect(status().isBadRequest());
        // Mot de passe trop court
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", "court", "email", "court@test.local",
                                "password", "123", "cin", "12345678", "firstName", "A", "lastName", "B"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bruteForceIsBlocked() throws Exception {
        String body = json.writeValueAsString(Map.of("username", "victime", "password", "mauvais"));
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void documentTypeCannotBeUsedForPathTraversal() throws Exception {
        String alice = registerUser("USER");
        mvc.perform(post("/api/documents/generate").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "documentType", "../../evil", "fullName", "X", "format", "PDF"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentCanGenerateAndDownloadOnlyOwnDocument() throws Exception {
        String alice = registerUser("USER");
        String bob = registerUser("USER");

        MvcResult res = mvc.perform(post("/api/documents/generate").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "documentType", "certificat_scolarite", "fullName", "Nom Usurpe",
                                "additionalInfo", "", "format", "PDF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filePath").doesNotExist())
                .andReturn();
        JsonNode doc = json.readTree(res.getResponse().getContentAsString());
        // Le nom officiel (identité administrative) remplace le nom saisi
        assertThat(doc.get("fullName").asText()).startsWith("Prenom Nom");

        long id = doc.get("id").asLong();
        mvc.perform(get("/api/documents/download/" + id).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/documents/download/" + id).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/documents/download/" + id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void professorNotificationsUseExactIdMatching() throws Exception {
        String prof = registerUser("PROFESSEUR");
        long profId = me(prof).get("id").asLong();

        Notification other = new Notification();
        other.setTitle("Pour un autre");
        other.setContent("x");
        other.setTarget(Notification.NotifTarget.PROFESSOR);
        other.setTargetIds((profId * 10 + 1) + "," + (profId + 1000)); // contient l'id en sous-chaîne
        notificationRepository.save(other);

        Notification mine = new Notification();
        mine.setTitle("Pour moi");
        mine.setContent("y");
        mine.setTarget(Notification.NotifTarget.PROFESSOR);
        mine.setTargetIds("999999," + profId);
        notificationRepository.save(mine);

        MvcResult res = mvc.perform(get("/api/notifications/professor")
                        .param("userId", String.valueOf(profId * 10 + 1)) // ignoré pour un professeur
                        .header("Authorization", bearer(prof)))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("Pour moi").doesNotContain("Pour un autre");
    }

    @Test
    void adminCannotDeleteOrDemoteLastAdmin() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        mvc.perform(delete("/api/admin/users/" + admin.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + admin.getId() + "/role").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/reset").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanDeleteUserWithHistory() throws Exception {
        String alice = registerUser("USER");
        JsonNode profile = me(alice);
        mvc.perform(post("/api/chat/send").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"bonjour\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/users/" + profile.get("id").asLong())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        // Le token d'un compte supprimé n'est plus accepté
        mvc.perform(get("/api/student/me").header("Authorization", bearer(alice)))
                .andExpect(status().isUnauthorized());
    }
}
