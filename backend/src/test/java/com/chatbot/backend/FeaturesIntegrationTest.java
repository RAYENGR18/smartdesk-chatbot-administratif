package com.chatbot.backend;

import com.chatbot.backend.model.Filiere;
import com.chatbot.backend.model.Matiere;
import com.chatbot.backend.model.Salle;
import com.chatbot.backend.registre.RegistreEtablissement;
import com.chatbot.backend.registre.RegistreEtablissementRepository;
import com.chatbot.backend.registre.TypePersonne;
import com.chatbot.backend.repository.FiliereRepository;
import com.chatbot.backend.repository.MatiereRepository;
import com.chatbot.backend.repository.SalleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests des 4 évolutions : inscription par CIN, import des notes,
 * emploi du temps par filière/niveau, présence RFID des professeurs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeaturesIntegrationTest {

    private static final String DEVICE_KEY = "cle-appareil-de-test-0123456789";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private RegistreEtablissementRepository registre;
    @Autowired private FiliereRepository filieres;
    @Autowired private MatiereRepository matieres;
    @Autowired private SalleRepository salles;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin", "Admin-Test-123");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private String login(String u, String p) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", u, "password", p))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private static String b(String t) {
        return "Bearer " + t;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importNotes(MockMultipartFile f) {
        return multipart("/api/notes/import").file(f).header("Authorization", b(admin));
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Filiere filiere(String code, String nom) {
        return filieres.findByCode(code).orElseGet(() -> {
            Filiere f = new Filiere();
            f.setCode(code);
            f.setNom(nom);
            return filieres.save(f);
        });
    }

    private void personne(String cin, String nom, String prenom, TypePersonne type, String filiere, String niveau) {
        RegistreEtablissement r = new RegistreEtablissement();
        r.setCin(cin);
        r.setNom(nom);
        r.setPrenom(prenom);
        r.setType(type);
        r.setFiliere(filiere);
        r.setNiveau(niveau);
        registre.save(r);
    }

    private MvcResult register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn();
    }

    private Map<String, Object> etudiant(String username, String cin, String nom, String prenom, Long filiereId, String niveau) {
        Map<String, Object> m = new HashMap<>();
        m.put("username", username);
        m.put("email", username + "@test.local");
        m.put("password", "Motdepasse-123");
        m.put("role", "USER");
        m.put("cin", cin);
        m.put("lastName", nom);
        m.put("firstName", prenom);
        m.put("filiereId", filiereId);
        m.put("niveau", niveau);
        return m;
    }

    /** Inscrit un étudiant présent dans le registre et renvoie son token. */
    private String etudiantInscrit(String cin, Filiere f, String niveau) throws Exception {
        String nom = "Nom" + uid();
        personne(cin, nom, "Prénom", TypePersonne.ETUDIANT, f.getCode(), niveau);
        String username = "etu" + uid();
        assertThat(register(etudiant(username, cin, nom, "Prenom", f.getId(), niveau))
                .getResponse().getStatus()).isEqualTo(200);
        return login(username, "Motdepasse-123");
    }

    private String professeurInscrit(String cin) throws Exception {
        String nom = "Prof" + uid();
        personne(cin, nom, "Karim", TypePersonne.PROFESSEUR, null, null);
        String username = "prof" + uid();
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.local");
        body.put("password", "Motdepasse-123");
        body.put("role", "PROFESSEUR");
        body.put("cin", cin);
        body.put("lastName", nom);
        body.put("firstName", "Karim");
        assertThat(register(body).getResponse().getStatus()).isEqualTo(200);
        return login(username, "Motdepasse-123");
    }

    private JsonNode me(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/student/me").header("Authorization", b(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    // ── Modification 1 : inscription vérifiée par le CIN ──────────────

    @Test
    void inscriptionVerifieeAvecLeRegistre() throws Exception {
        Filiere info = filiere("INFOT", "Informatique test");
        Filiere chim = filiere("CHIMT", "Chimie test");
        personne("01234567", "Ben Salah", "Amine", TypePersonne.ETUDIANT, "INFOT", "L1");
        String u = "amine" + uid();

        // CIN inconnu
        MvcResult r = register(etudiant(u, "07777777", "Ben Salah", "Amine", info.getId(), "L1"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("ne figure pas");

        // Nom différent, filière différente, niveau différent : refusé
        assertThat(register(etudiant(u, "01234567", "Autre", "Amine", info.getId(), "L1")).getResponse().getStatus()).isEqualTo(400);
        assertThat(register(etudiant(u, "01234567", "Ben Salah", "Amine", chim.getId(), "L1")).getResponse().getStatus()).isEqualTo(400);
        assertThat(register(etudiant(u, "01234567", "Ben Salah", "Amine", info.getId(), "L2")).getResponse().getStatus()).isEqualTo(400);

        // Bonnes informations (casse, accents et zéro initial perdu tolérés)
        r = register(etudiant(u, "1234567", "ben salah", "AMINE", info.getId(), "Licence 1"));
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode profil = me(login(u, "Motdepasse-123"));
        assertThat(profil.get("cin").asText()).isEqualTo("01234567");
        assertThat(profil.get("lastName").asText()).isEqualTo("Ben Salah");
        assertThat(profil.get("fieldOfStudy").asText()).isEqualTo("Informatique test");

        // Second compte pour le même CIN : refusé
        assertThat(register(etudiant("autre" + uid(), "01234567", "Ben Salah", "Amine", info.getId(), "L1"))
                .getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void registrePublicEtImportAdmin() throws Exception {
        mvc.perform(get("/api/auth/filieres")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/niveaux")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("L1"));

        // CSV Excel FR : BOM absent, Windows-1252, séparateur ";", niveau en toutes lettres, CIN sans zéro
        String csv = "CIN;Nom;Prénom;Type;Filière;Niveau\r\n"
                + "2345678;Hammami;Inès;Étudiant;INFOT;Licence 2\r\n"
                + "88800002;Haddad;Karim;Professeur;;\r\n"
                + ";SansCin;X;etudiant;INFOT;L1\r\n";
        MockMultipartFile f = new MockMultipartFile("file", "registre.csv", "text/csv",
                csv.getBytes(Charset.forName("windows-1252")));
        mvc.perform(multipart("/api/admin/registre/import").file(f).header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ajoutes").value(2))
                .andExpect(jsonPath("$.ignores").value(1));
        RegistreEtablissement ines = registre.findByCin("02345678").orElseThrow();
        assertThat(ines.getPrenom()).isEqualTo("Inès");
        assertThat(ines.getNiveau()).isEqualTo("L2");

        String etu = etudiantInscrit("T" + uid().toUpperCase(), filiere("INFOT", "Informatique test"), "L1");
        mvc.perform(get("/api/admin/registre").header("Authorization", b(etu))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/registre/import").file(f).header("Authorization", b(etu)))
                .andExpect(status().isForbidden());
    }

    @Test
    void donneesDeDemonstration() throws Exception {
        mvc.perform(post("/api/admin/registre/demo").header("Authorization", b(admin)))
                .andExpect(status().isOk());
        assertThat(registre.findByCin("99000101")).isPresent();
        assertThat(filieres.findByCode("INFO")).isPresent();
        // Rechargement : rien n'est dupliqué
        mvc.perform(post("/api/admin/registre/demo").header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filieresCreees").value(0));
    }

    // ── Modification 2 : import des notes ─────────────────────────────

    @Test
    void importDesNotesCsvEtExcel() throws Exception {
        Filiere info = filiere("INFOT", "Informatique test");
        String cinA = "0" + (1000000 + (int) (Math.random() * 8999999));
        String cinB = "T" + uid().toUpperCase();
        String alice = etudiantInscrit(cinA, info, "L1");
        String bob = etudiantInscrit(cinB, info, "L1");

        // CSV avec BOM UTF-8, en-têtes accentués, CIN sans son zéro initial, virgule décimale
        String csv = "﻿CIN;Nom de la matière;Type;Note;Commentaire\n"
                + Integer.parseInt(cinA) + ";Algorithmique;Examen;15,5;Très bien\n"
                + Integer.parseInt(cinA) + ";Algorithmique;DS;12;\n"
                + "99999999;Algorithmique;Examen;10;\n"
                + Integer.parseInt(cinA) + ";Réseaux;Examen;25;\n";
        mvc.perform(importNotes(new MockMultipartFile("file", "notes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.skipped").value(2));

        // Excel : CIN numérique, note calculée par une formule
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Notes");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("cin");
            h.createCell(1).setCellValue("Matière");
            h.createCell(2).setCellValue("Note");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(cinB);
            r1.createCell(1).setCellValue("Bases de données");
            r1.createCell(2).setCellFormula("(14+16)/2");
            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue(Double.parseDouble(cinA));
            r2.createCell(1).setCellValue("Algorithmique");
            r2.createCell(2).setCellValue(17);
            wb.write(out);
        }
        mvc.perform(importNotes(new MockMultipartFile("file", "notes.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.updated").value(1));

        // Chaque étudiant ne voit que ses notes
        mvc.perform(get("/api/notes/me").header("Authorization", b(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.typeEvaluation=='Examen')].valeur").value(17.0))
                .andExpect(jsonPath("$[0].nomMatiere").value("Algorithmique"));
        mvc.perform(get("/api/notes/me").header("Authorization", b(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].valeur").value(15.0));
        String bodyBob = mvc.perform(get("/api/notes/me").header("Authorization", b(bob)))
                .andReturn().getResponse().getContentAsString();
        assertThat(bodyBob).doesNotContain("Algorithmique").doesNotContain(cinA);

        // Fichier sans les bonnes colonnes : message clair
        mvc.perform(importNotes(new MockMultipartFile("file", "x.csv", "text/csv", "a;b\n1;2\n".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Colonnes obligatoires")));
    }

    // ── Modification 3 : emploi du temps par filière et niveau ────────

    @Test
    void emploiDuTempsLimiteAFiliereEtNiveau() throws Exception {
        Filiere info = filiere("INFOP", "Informatique planning");
        Filiere chim = filiere("CHIMP", "Chimie planning");
        Matiere m = new Matiere();
        m.setCode("M-" + uid());
        m.setNom("Matière planning");
        matieres.save(m);
        Salle s1 = new Salle();
        s1.setNom("S-" + uid());
        salles.save(s1);

        String[][] creneaux = {{"INFOP", "L1", "LUNDI"}, {"INFOP", "L2", "MARDI"}, {"CHIMP", "L1", "MERCREDI"}};
        for (String[] c : creneaux) {
            Long fid = c[0].equals("INFOP") ? info.getId() : chim.getId();
            mvc.perform(post("/api/planning/creneaux").header("Authorization", b(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("jour", c[2], "heureDebut", "08:30",
                                    "heureFin", "10:00", "matiereId", m.getId(), "salleId", s1.getId(),
                                    "filiereId", fid, "niveau", c[1]))))
                    .andExpect(status().isOk());
        }
        // Créneau sans niveau : refusé
        mvc.perform(post("/api/planning/creneaux").header("Authorization", b(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("jour", "JEUDI", "heureDebut", "08:30",
                                "heureFin", "10:00", "matiereId", m.getId(), "salleId", s1.getId(),
                                "filiereId", info.getId()))))
                .andExpect(status().isBadRequest());

        String etu = etudiantInscrit("T" + uid().toUpperCase(), info, "L1");
        mvc.perform(get("/api/planning/me").header("Authorization", b(etu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveau").value("L1"))
                .andExpect(jsonPath("$.filiere.code").value("INFOP"))
                .andExpect(jsonPath("$.creneaux.length()").value(1))
                .andExpect(jsonPath("$.creneaux[0].jour").value("LUNDI"));

        // Un étudiant ne peut pas demander l'emploi du temps d'une autre filière
        mvc.perform(get("/api/planning/filiere/" + chim.getId()).header("Authorization", b(etu)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/planning/filiere/" + info.getId()).param("niveau", "L2").header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── Modification 4 : présence RFID des professeurs ────────────────

    @Test
    void pointageRfid() throws Exception {
        String prof = professeurInscrit("P" + uid().toUpperCase());
        long profId = me(prof).get("id").asLong();
        String badge = "04:A1:" + uid().substring(0, 2).toUpperCase() + ":3B";
        String scan = json.writeValueAsString(Map.of("uid", badge, "lecteur", "ENTREE"));

        // Sans clé d'appareil / mauvaise clé
        mvc.perform(post("/api/iot/rfid/scan").contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/iot/rfid/scan").header("X-Device-Key", "mauvaise")
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isUnauthorized());

        // Badge pas encore attribué
        mvc.perform(post("/api/iot/rfid/scan").header("X-Device-Key", DEVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("INCONNU"));
        String normalise = badge.replace(":", "");
        mvc.perform(get("/api/admin/presences/badges-inconnus").header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.uid=='" + normalise + "')]").exists());

        // Attribution puis arrivée / départ / déjà clôturé
        mvc.perform(put("/api/admin/presences/badges/" + profId).header("Authorization", b(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("uid", badge))))
                .andExpect(status().isOk());
        for (String attendu : new String[]{"ARRIVEE", "DEPART", "DEJA_CLOTURE"}) {
            mvc.perform(post("/api/iot/rfid/scan").header("X-Device-Key", DEVICE_KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(scan))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultat").value(attendu));
        }

        mvc.perform(get("/api/admin/presences").param("professeurId", String.valueOf(profId))
                        .header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].heureArrivee").exists())
                .andExpect(jsonPath("$[0].heureDepart").exists())
                .andExpect(jsonPath("$[0].dureeMinutes").value(0))
                .andExpect(jsonPath("$[0].statut").value("TERMINE"));
        mvc.perform(get("/api/admin/presences/export").header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nom;Prénom;Date")));

        // Le badge ne peut pas être donné à un second professeur
        String prof2 = professeurInscrit("Q" + uid().toUpperCase());
        long prof2Id = me(prof2).get("id").asLong();
        mvc.perform(put("/api/admin/presences/badges/" + prof2Id).header("Authorization", b(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("uid", normalise))))
                .andExpect(status().isConflict());

        // Simulation (démo) et droits
        mvc.perform(post("/api/admin/presences/simuler/" + prof2Id).header("Authorization", b(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("ARRIVEE"));
        mvc.perform(get("/api/admin/presences").header("Authorization", b(prof)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/presences/simuler/" + profId).header("Authorization", b(prof)))
                .andExpect(status().isForbidden());
    }
}
