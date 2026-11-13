package com.chatbot.backend.presence;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Pointage des professeurs par badge RFID.
 *
 * Règles pour une journée :
 *   1er passage            -> ARRIVEE (heure d'arrivée enregistrée)
 *   2e passage             -> DEPART  (heure de départ, présence clôturée)
 *   passages suivants      -> DEJA_CLOTURE (rien n'est modifié)
 *   2 passages rapprochés  -> DOUBLON (badge présenté deux fois par erreur)
 *   badge non attribué     -> INCONNU (visible par l'admin pour l'attribuer)
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final PresenceRepository presenceRepository;
    private final ScanRfidRepository scanRepository;
    private final UserRepository userRepository;
    private final ZoneId zone;
    private final long antiRebondSecondes;
    private final Clock clock;

    public PresenceService(PresenceRepository presenceRepository,
                           ScanRfidRepository scanRepository,
                           UserRepository userRepository,
                           @Value("${app.timezone:Africa/Tunis}") String timezone,
                           @Value("${app.rfid.anti-rebond-secondes:60}") long antiRebondSecondes) {
        this.presenceRepository = presenceRepository;
        this.scanRepository = scanRepository;
        this.userRepository = userRepository;
        this.zone = ZoneId.of(timezone);
        this.antiRebondSecondes = Math.max(0, antiRebondSecondes);
        this.clock = Clock.system(zone);
    }

    // ── Données renvoyées ──────────────────────────────────────────────

    public record ResultatScan(ScanRfid.Resultat resultat, String message, String professeur,
                               String heure, String date) {
    }

    public record PresenceView(Long id, Long professeurId, String nom, String prenom, LocalDate date,
                               String heureArrivee, String heureDepart, Long dureeMinutes,
                               String duree, String statut, String lecteur) {
    }

    // ── Badges ────────────────────────────────────────────────────────

    /** "04:A1:3B:2C" / "04 a1 3b 2c" / "0004567890" -> "04A13B2C" / "0004567890". */
    public static String normaliserUid(String uid) {
        if (uid == null) return "";
        return uid.trim().toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
    }

    private static String valider(String uid) {
        String u = normaliserUid(uid);
        if (u.length() < 4 || u.length() > 32) {
            throw ApiException.badRequest("Identifiant de badge invalide (4 à 32 caractères hexadécimaux).");
        }
        return u;
    }

    // ── Passage d'un badge ────────────────────────────────────────────

    @Transactional
    public ResultatScan enregistrerScan(String uidBrut, String lecteur) {
        String uid = valider(uidBrut);
        String nomLecteur = lecteur == null || lecteur.isBlank() ? "ENTREE" : lecteur.trim();
        if (nomLecteur.length() > 50) nomLecteur = nomLecteur.substring(0, 50);

        Optional<User> profOpt = userRepository.findByRfidUid(uid)
                .filter(u -> u.getRole() == User.Role.PROFESSEUR);
        LocalDateTime maintenant = LocalDateTime.now(clock);

        if (profOpt.isEmpty()) {
            journaliser(uid, nomLecteur, ScanRfid.Resultat.INCONNU, null, maintenant);
            log.info("Badge RFID inconnu : {}", uid);
            return new ResultatScan(ScanRfid.Resultat.INCONNU,
                    "Badge non reconnu. Présentez-vous à l'administration.", null,
                    maintenant.format(HEURE), maintenant.toLocalDate().toString());
        }
        return pointer(profOpt.get(), uid, nomLecteur, maintenant);
    }

    /** Simulation depuis l'interface admin (démonstration sans lecteur). */
    @Transactional
    public ResultatScan simuler(Long professeurId) {
        User prof = userRepository.findById(professeurId)
                .filter(u -> u.getRole() == User.Role.PROFESSEUR)
                .orElseThrow(() -> ApiException.notFound("Professeur introuvable."));
        String uid = prof.getRfidUid() != null ? prof.getRfidUid() : "SIM-" + prof.getId();
        return pointer(prof, uid, "SIMULATION", LocalDateTime.now(clock));
    }

    private ResultatScan pointer(User prof, String uid, String lecteur, LocalDateTime maintenant) {
        String nomComplet = (Objects.toString(prof.getFirstName(), "") + " "
                + Objects.toString(prof.getLastName(), "")).trim();
        String heure = maintenant.format(HEURE);
        LocalDate jour = maintenant.toLocalDate();

        // Anti-rebond : même badge déjà enregistré il y a quelques secondes
        if (antiRebondSecondes > 0) {
            Optional<ScanRfid> dernier = scanRepository.findFirstByUidAndResultatInOrderByHorodatageDesc(
                    uid, List.of(ScanRfid.Resultat.ARRIVEE, ScanRfid.Resultat.DEPART));
            if (dernier.isPresent()
                    && Duration.between(dernier.get().getHorodatage(), maintenant).getSeconds() < antiRebondSecondes) {
                journaliser(uid, lecteur, ScanRfid.Resultat.DOUBLON, prof, maintenant);
                return new ResultatScan(ScanRfid.Resultat.DOUBLON,
                        "Passage déjà enregistré il y a quelques secondes.", nomComplet, heure, jour.toString());
            }
        }

        Optional<PresenceProfesseur> existante = presenceRepository.findByProfesseurIdAndDate(prof.getId(), jour);
        if (existante.isEmpty()) {
            PresenceProfesseur p = new PresenceProfesseur();
            p.setProfesseur(prof);
            p.setDate(jour);
            p.setHeureArrivee(maintenant.toLocalTime().withNano(0));
            p.setLecteur(lecteur);
            presenceRepository.save(p);
            journaliser(uid, lecteur, ScanRfid.Resultat.ARRIVEE, prof, maintenant);
            return new ResultatScan(ScanRfid.Resultat.ARRIVEE,
                    "Bonjour " + nomComplet + " : arrivée enregistrée à " + heure + ".", nomComplet, heure, jour.toString());
        }

        PresenceProfesseur p = existante.get();
        if (p.getHeureDepart() == null) {
            p.setHeureDepart(maintenant.toLocalTime().withNano(0));
            presenceRepository.save(p);
            journaliser(uid, lecteur, ScanRfid.Resultat.DEPART, prof, maintenant);
            return new ResultatScan(ScanRfid.Resultat.DEPART,
                    "Au revoir " + nomComplet + " : départ enregistré à " + heure
                            + " (présence : " + formatDuree(dureeMinutes(p)) + ").", nomComplet, heure, jour.toString());
        }

        journaliser(uid, lecteur, ScanRfid.Resultat.DEJA_CLOTURE, prof, maintenant);
        return new ResultatScan(ScanRfid.Resultat.DEJA_CLOTURE,
                "Présence du jour déjà clôturée (départ à " + p.getHeureDepart().format(HEURE) + ").",
                nomComplet, heure, jour.toString());
    }

    private void journaliser(String uid, String lecteur, ScanRfid.Resultat resultat, User prof, LocalDateTime quand) {
        ScanRfid s = new ScanRfid();
        s.setUid(uid);
        s.setLecteur(lecteur);
        s.setResultat(resultat);
        s.setProfesseur(prof);
        s.setHorodatage(quand);
        scanRepository.save(s);
    }

    // ── Consultation (admin) ──────────────────────────────────────────

    public List<PresenceView> rechercher(LocalDate debut, LocalDate fin, Long professeurId) {
        LocalDate aujourdHui = LocalDate.now(clock);
        LocalDate d = debut != null ? debut : aujourdHui;
        LocalDate f = fin != null ? fin : d;
        if (f.isBefore(d)) {
            throw ApiException.badRequest("La date de fin est avant la date de début.");
        }
        if (Duration.between(d.atStartOfDay(), f.atStartOfDay()).toDays() > 366) {
            throw ApiException.badRequest("Période limitée à un an.");
        }
        return presenceRepository.rechercher(d, f, professeurId).stream().map(p -> {
            Long minutes = dureeMinutes(p);
            boolean enCours = p.getHeureDepart() == null;
            String statut = enCours ? (p.getDate().isBefore(aujourdHui) ? "DEPART_NON_POINTE" : "PRESENT") : "TERMINE";
            return new PresenceView(p.getId(), p.getProfesseur().getId(),
                    p.getProfesseur().getLastName(), p.getProfesseur().getFirstName(), p.getDate(),
                    p.getHeureArrivee().format(HEURE),
                    p.getHeureDepart() != null ? p.getHeureDepart().format(HEURE) : null,
                    minutes, minutes != null ? formatDuree(minutes) : null, statut, p.getLecteur());
        }).toList();
    }

    public Map<String, Object> resumeDuJour() {
        LocalDate aujourdHui = LocalDate.now(clock);
        long profs = userRepository.countByRole(User.Role.PROFESSEUR);
        long arrives = presenceRepository.countByDate(aujourdHui);
        long presents = presenceRepository.countByDateAndHeureDepartIsNull(aujourdHui);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", aujourdHui.toString());
        m.put("professeurs", profs);
        m.put("arrives", arrives);
        m.put("presentsMaintenant", presents);
        m.put("partis", arrives - presents);
        m.put("absents", Math.max(0, profs - arrives));
        return m;
    }

    public List<Map<String, Object>> badges() {
        LocalDate aujourdHui = LocalDate.now(clock);
        return userRepository.findByRole(User.Role.PROFESSEUR).stream()
                .sorted(Comparator.comparing((User u) -> Objects.toString(u.getLastName(), ""))
                        .thenComparing(u -> Objects.toString(u.getFirstName(), "")))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("nom", u.getLastName());
                    m.put("prenom", u.getFirstName());
                    m.put("email", u.getEmail());
                    m.put("badge", u.getRfidUid());
                    Optional<PresenceProfesseur> p = presenceRepository.findByProfesseurIdAndDate(u.getId(), aujourdHui);
                    m.put("statutJour", p.isEmpty() ? "ABSENT" : (p.get().getHeureDepart() == null ? "PRESENT" : "PARTI"));
                    return m;
                }).toList();
    }

    @Transactional
    public void attribuerBadge(Long professeurId, String uidBrut) {
        User prof = userRepository.findById(professeurId)
                .filter(u -> u.getRole() == User.Role.PROFESSEUR)
                .orElseThrow(() -> ApiException.notFound("Professeur introuvable."));
        if (uidBrut == null || uidBrut.isBlank()) {
            prof.setRfidUid(null);
            userRepository.save(prof);
            return;
        }
        String uid = valider(uidBrut);
        userRepository.findByRfidUid(uid)
                .filter(u -> !u.getId().equals(prof.getId()))
                .ifPresent(u -> {
                    throw ApiException.conflict("Ce badge est déjà attribué à " + u.getFirstName() + " " + u.getLastName() + ".");
                });
        prof.setRfidUid(uid);
        userRepository.save(prof);
    }

    public List<Map<String, Object>> badgesInconnus() {
        LocalDateTime depuis = LocalDateTime.now(clock).minusDays(7);
        Map<String, Map<String, Object>> parUid = new LinkedHashMap<>();
        for (ScanRfid s : scanRepository.findTop20ByResultatAndHorodatageAfterOrderByHorodatageDesc(
                ScanRfid.Resultat.INCONNU, depuis)) {
            if (userRepository.findByRfidUid(s.getUid()).isPresent()) continue; // attribué depuis
            parUid.computeIfAbsent(s.getUid(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uid", s.getUid());
                m.put("dernierPassage", s.getHorodatage().toString());
                m.put("lecteur", s.getLecteur());
                return m;
            });
        }
        return new ArrayList<>(parUid.values());
    }

    public List<Map<String, Object>> derniersPassages() {
        return scanRepository.findTop30ByOrderByHorodatageDesc().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("horodatage", s.getHorodatage().toString());
            m.put("resultat", s.getResultat());
            m.put("uid", s.getUid());
            m.put("lecteur", s.getLecteur());
            m.put("professeur", s.getProfesseur() == null ? null
                    : (s.getProfesseur().getFirstName() + " " + s.getProfesseur().getLastName()));
            return m;
        }).toList();
    }

    /** Export CSV (séparateur ";" et BOM UTF-8 : s'ouvre directement dans Excel). */
    public String exporterCsv(LocalDate debut, LocalDate fin, Long professeurId) {
        StringBuilder sb = new StringBuilder("﻿Nom;Prénom;Date;Arrivée;Départ;Durée;Statut\r\n");
        for (PresenceView p : rechercher(debut, fin, professeurId)) {
            sb.append(csv(p.nom())).append(';').append(csv(p.prenom())).append(';')
                    .append(p.date()).append(';').append(p.heureArrivee()).append(';')
                    .append(p.heureDepart() == null ? "" : p.heureDepart()).append(';')
                    .append(p.duree() == null ? "" : p.duree()).append(';')
                    .append(p.statut()).append("\r\n");
        }
        return sb.toString();
    }

    // ── Utilitaires ───────────────────────────────────────────────────

    private static Long dureeMinutes(PresenceProfesseur p) {
        if (p.getHeureDepart() == null) return null;
        long m = Duration.between(p.getHeureArrivee(), p.getHeureDepart()).toMinutes();
        return Math.max(0, m);
    }

    private static String formatDuree(Long minutes) {
        if (minutes == null) return "";
        return String.format("%dh%02d", minutes / 60, minutes % 60);
    }

    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        // Neutralise les formules Excel (=, +, -, @) : protection contre l'injection CSV
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(";") || s.contains("\"") ? "\"" + s + "\"" : s;
    }
}
