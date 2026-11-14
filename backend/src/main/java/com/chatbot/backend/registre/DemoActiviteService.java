package com.chatbot.backend.registre;

import com.chatbot.backend.model.EmploiDuTemps;
import com.chatbot.backend.model.Filiere;
import com.chatbot.backend.model.Matiere;
import com.chatbot.backend.model.Note;
import com.chatbot.backend.model.Notification;
import com.chatbot.backend.model.User;
import com.chatbot.backend.presence.PresenceProfesseur;
import com.chatbot.backend.presence.PresenceRepository;
import com.chatbot.backend.presence.ScanRfid;
import com.chatbot.backend.presence.ScanRfidRepository;
import com.chatbot.backend.repository.EmploiDuTempsRepository;
import com.chatbot.backend.repository.FiliereRepository;
import com.chatbot.backend.repository.MatiereRepository;
import com.chatbot.backend.repository.NoteRepository;
import com.chatbot.backend.repository.NotificationRepository;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.service.PlanningService;
import com.chatbot.backend.util.Niveaux;
import com.chatbot.backend.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Activité FICTIVE de démonstration, à partir du registre de démo (CIN 99xxxxxx) :
 * comptes étudiants et professeurs, emplois du temps, notes, badges et historique
 * de présence, notifications. Tout est idempotent : un élément déjà présent n'est
 * ni dupliqué ni modifié. Les valeurs « aléatoires » sont reproductibles (graine fixe).
 */
@Service
public class DemoActiviteService {

    private static final Logger log = LoggerFactory.getLogger(DemoActiviteService.class);

    /** Personnes du registre de démo laissées SANS compte, pour essayer l'inscription. */
    static final Set<String> CIN_SANS_COMPTE = Set.of("99000101", "99000209");

    /** Matières enseignées par chaque professeur de démo (CIN → codes). */
    private static final Map<String, List<String>> ENSEIGNEMENTS = new LinkedHashMap<>();

    static {
        ENSEIGNEMENTS.put("99000201", List.of("INFO-L1-ALGO", "INFO-L2-POO", "INFO-L3-IA", "INFO-M1-ML"));
        ENSEIGNEMENTS.put("99000202", List.of("INFO-L2-BD", "INFO-L3-WEB", "INFO-M1-CLOUD"));
        ENSEIGNEMENTS.put("99000206", List.of("INFO-L1-ARCHI", "INFO-L1-MATH"));
        ENSEIGNEMENTS.put("99000203", List.of("CHIM-L1-GEN", "CHIM-L2-ORG"));
        ENSEIGNEMENTS.put("99000207", List.of("CHIM-L1-PHYS", "CHIM-L3-ANA"));
        ENSEIGNEMENTS.put("99000204", List.of("MATH-L1-ANA", "MATH-L2-ANA2", "MATH-M1-OPT"));
        ENSEIGNEMENTS.put("99000208", List.of("MATH-L1-ALG", "MATH-L2-PROBA"));
        ENSEIGNEMENTS.put("99000205", List.of("GEST-L1-COMPTA", "GEST-L1-ECO", "GEST-L2-MKT", "GEST-L3-FIN"));
    }

    private final RegistreEtablissementRepository registre;
    private final UserRepository users;
    private final FiliereRepository filieres;
    private final MatiereRepository matieres;
    private final EmploiDuTempsRepository creneaux;
    private final NoteRepository notes;
    private final NotificationRepository notifications;
    private final PresenceRepository presences;
    private final ScanRfidRepository scans;
    private final PlanningService planning;
    private final PasswordEncoder encoder;
    private final String motDePasseDemo;
    private final ZoneId zone;

    public DemoActiviteService(RegistreEtablissementRepository registre, UserRepository users,
                               FiliereRepository filieres, MatiereRepository matieres,
                               EmploiDuTempsRepository creneaux, NoteRepository notes,
                               NotificationRepository notifications, PresenceRepository presences,
                               ScanRfidRepository scans, PlanningService planning, PasswordEncoder encoder,
                               @Value("${app.demo.mot-de-passe:Demo2026}") String motDePasseDemo,
                               @Value("${app.timezone:Africa/Tunis}") String timezone) {
        this.registre = registre;
        this.users = users;
        this.filieres = filieres;
        this.matieres = matieres;
        this.creneaux = creneaux;
        this.notes = notes;
        this.notifications = notifications;
        this.presences = presences;
        this.scans = scans;
        this.planning = planning;
        this.encoder = encoder;
        this.motDePasseDemo = motDePasseDemo;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional
    public Map<String, Object> remplir() {
        Map<String, Object> res = new LinkedHashMap<>();
        int[] comptes = creerComptes();
        res.put("comptesEtudiantsCrees", comptes[0]);
        res.put("comptesProfesseursCrees", comptes[1]);
        res.put("creneauxCrees", genererEmploisDuTemps());
        res.put("notesCreees", genererNotes());
        res.put("presencesCreees", genererPresences());
        res.put("notificationsCreees", genererNotifications());
        res.put("motDePasseComptesDemo", motDePasseDemo);
        res.put("cinSansCompte", new TreeSet<>(CIN_SANS_COMPTE));
        return res;
    }

    // ── Comptes ──────────────────────────────────────────────────────

    private List<RegistreEtablissement> personnesDemo() {
        return registre.findAll().stream()
                .filter(r -> "DEMO".equals(r.getOrigine()) && r.getCin().startsWith("99"))
                .sorted(Comparator.comparing(RegistreEtablissement::getCin))
                .toList();
    }

    private int[] creerComptes() {
        int etudiants = 0, profs = 0;
        String hash = null;
        for (RegistreEtablissement p : personnesDemo()) {
            if (CIN_SANS_COMPTE.contains(p.getCin()) || users.findByCinIgnoreCase(p.getCin()).isPresent()) continue;
            if (hash == null) hash = encoder.encode(motDePasseDemo);

            User u = new User();
            u.setUsername(nomUtilisateurLibre(p));
            String email = p.getEmail() != null && !p.getEmail().isBlank() ? p.getEmail()
                    : u.getUsername() + "@demo.fpst.tn";
            if (users.existsByEmailIgnoreCase(email)) email = u.getUsername() + "." + p.getCin() + "@demo.fpst.tn";
            u.setEmail(email);
            u.setPassword(hash);
            u.setCin(p.getCin());
            u.setFirstName(p.getPrenom());
            u.setLastName(p.getNom());
            u.setInstitution("Faculté des Sciences - FPST");
            u.setAcademicYear("2026/2027");
            u.setNationality("Tunisienne");

            if (p.getType() == TypePersonne.ETUDIANT) {
                Filiere f = filieres.findByCode(p.getFiliere()).orElse(null);
                if (f == null) continue;
                u.setRole(User.Role.USER);
                u.setFiliere(f);
                u.setNiveau(p.getNiveau());
                u.setFieldOfStudy(f.getNom());
                u.setAcademicLevel(Niveaux.libelle(p.getNiveau()));
                if (p.getMatricule() != null && users.findByStudentId(p.getMatricule()).isEmpty()) {
                    u.setStudentId(p.getMatricule());
                }
                users.save(u);
                etudiants++;
            } else {
                u.setRole(User.Role.PROFESSEUR);
                u.setDepartment(p.getDepartement());
                u.setSpecialite(p.getDepartement());
                for (String code : ENSEIGNEMENTS.getOrDefault(p.getCin(), List.of())) {
                    matieres.findByCode(code).ifPresent(m -> {
                        u.getMatieresEnseignees().add(m);
                        if (m.getFiliere() != null) u.getFilieresProfesseur().add(m.getFiliere());
                    });
                }
                users.save(u);
                profs++;
            }
        }
        return new int[]{etudiants, profs};
    }

    private String nomUtilisateurLibre(RegistreEtablissement p) {
        String base = (TextUtils.normaliserNom(p.getPrenom()) + "." + TextUtils.normaliserNom(p.getNom()))
                .replace(" ", "");
        String candidat = base;
        for (int i = 2; users.existsByUsername(candidat); i++) candidat = base + i;
        return candidat;
    }

    private List<User> comptesDemo(User.Role role) {
        return users.findByRole(role).stream()
                .filter(u -> u.getCin() != null && u.getCin().startsWith("99"))
                .sorted(Comparator.comparing(User::getCin))
                .toList();
    }

    // ── Emplois du temps ─────────────────────────────────────────────

    private int genererEmploisDuTemps() {
        int total = 0;
        for (String code : List.of("INFO", "CHIM", "MATH", "GEST")) {
            Filiere f = filieres.findByCode(code).orElse(null);
            if (f == null) continue;
            Set<String> niveaux = new TreeSet<>();
            matieres.findByAnyFiliereId(f.getId()).forEach(m -> {
                if (m.getNiveau() != null && !m.getNiveau().isBlank()) niveaux.add(m.getNiveau());
            });
            for (String niveau : niveaux) {
                if (!creneaux.findByFiliereIdAndNiveauOrderByJourAscHeureDebutAsc(f.getId(), niveau).isEmpty()) continue;
                List<EmploiDuTemps> crees = planning.genererPlanningAutomatique(f.getId(), niveau, "S1");
                total += crees.size();
            }
        }
        return total;
    }

    // ── Notes ────────────────────────────────────────────────────────

    private int genererNotes() {
        int total = 0;
        for (User etu : comptesDemo(User.Role.USER)) {
            if (etu.getFiliere() == null || !notes.findByEtudiantId(etu.getId()).isEmpty()) continue;
            List<Matiere> sesMatieres = matieres.findByAnyFiliereId(etu.getFiliere().getId()).stream()
                    .filter(m -> etu.getNiveau() != null && etu.getNiveau().equals(m.getNiveau()))
                    .toList();
            // Profil de l'étudiant : certains sont plus à l'aise que d'autres
            double niveauEtudiant = 9 + new Random(etu.getCin().hashCode()).nextDouble() * 6;
            for (Matiere m : sesMatieres) {
                Random r = new Random((etu.getCin() + m.getCode()).hashCode());
                double ds = borne(niveauEtudiant + r.nextGaussian() * 2.5);
                double examen = borne(niveauEtudiant + r.nextGaussian() * 3);
                double moyenne = Math.round((0.3 * ds + 0.7 * examen) * 100) / 100.0;
                User prof = professeurDe(m);
                notes.save(note(etu, m, "DS", ds, moyenne, prof, null));
                notes.save(note(etu, m, "Examen", examen, moyenne, prof,
                        moyenne >= 10 ? "Module validé" : "Rattrapage conseillé"));
                total += 2;
            }
        }
        return total;
    }

    private static double borne(double v) {
        double arrondi = Math.round(v * 4) / 4.0; // au quart de point
        return Math.max(2, Math.min(19.5, arrondi));
    }

    private User professeurDe(Matiere m) {
        return comptesDemo(User.Role.PROFESSEUR).stream()
                .filter(p -> p.getMatieresEnseignees().stream().anyMatch(x -> x.getId().equals(m.getId())))
                .findFirst().orElse(null);
    }

    private static Note note(User etu, Matiere m, String type, double valeur, double moyenne, User prof, String commentaire) {
        Note n = new Note();
        n.setEtudiant(etu);
        n.setMatiere(m);
        n.setNomMatiere(m.getNom());
        n.setTypeEvaluation(type);
        n.setValeur(valeur);
        n.setMoyenne(moyenne);
        n.setProfesseur(prof);
        n.setCommentaire(commentaire);
        n.setSource("DEMO");
        return n;
    }

    // ── Badges et présences ──────────────────────────────────────────

    private int genererPresences() {
        int total = 0;
        LocalDate aujourdhui = LocalDate.now(zone);
        LocalTime maintenant = LocalTime.now(zone);
        List<User> profs = comptesDemo(User.Role.PROFESSEUR);

        for (int i = 0; i < profs.size(); i++) {
            User prof = profs.get(i);
            if (prof.getRfidUid() == null) {
                String uid = "DE40" + prof.getCin().substring(4);
                if (users.findByRfidUid(uid).isEmpty()) {
                    prof.setRfidUid(uid);
                    users.save(prof);
                }
            }
            if (!presences.rechercher(aujourdhui.minusDays(60), aujourdhui, prof.getId()).isEmpty()) continue;

            // Deux semaines passées (dimanche exclu)
            for (int j = 14; j >= 1; j--) {
                LocalDate jour = aujourdhui.minusDays(j);
                if (jour.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                Random r = new Random(prof.getCin().hashCode() * 31L + jour.toEpochDay());
                if (r.nextDouble() < 0.12) continue; // absent ce jour-là
                LocalTime arrivee = LocalTime.of(7, 45).plusMinutes(r.nextInt(90));
                LocalTime depart = jour.getDayOfWeek() == DayOfWeek.SATURDAY
                        ? LocalTime.of(12, 15).plusMinutes(r.nextInt(45))
                        : arrivee.plusMinutes(360 + r.nextInt(150));
                // Un oubli de badge au départ, pour montrer le cas « départ non pointé »
                boolean oubli = i == 1 && j == 3;
                total += presence(prof, jour, arrivee, oubli ? null : depart);
            }

            // Aujourd'hui : une partie des professeurs est déjà arrivée
            if (aujourdhui.getDayOfWeek() != DayOfWeek.SUNDAY && i % 3 != 2
                    && maintenant.isAfter(LocalTime.of(8, 0))) {
                Random r = new Random(prof.getCin().hashCode() + aujourdhui.toEpochDay());
                LocalTime arrivee = LocalTime.of(7, 45).plusMinutes(r.nextInt(75));
                if (arrivee.isAfter(maintenant)) arrivee = maintenant.minusMinutes(5);
                LocalTime depart = i == 0 && maintenant.isAfter(LocalTime.of(13, 0)) ? LocalTime.of(12, 40) : null;
                total += presence(prof, aujourdhui, arrivee, depart);
            }
        }

        // Badge inconnu passé récemment (encart « badges non attribués »)
        if (!profs.isEmpty() && scans.findTop20ByResultatAndHorodatageAfterOrderByHorodatageDesc(
                ScanRfid.Resultat.INCONNU, aujourdhui.minusDays(7).atStartOfDay()).isEmpty()) {
            scan("A1B2C3D4", aujourdhui.minusDays(1).atTime(8, 12), ScanRfid.Resultat.INCONNU, null);
        }
        return total;
    }

    private int presence(User prof, LocalDate jour, LocalTime arrivee, LocalTime depart) {
        if (presences.findByProfesseurIdAndDate(prof.getId(), jour).isPresent()) return 0;
        PresenceProfesseur p = new PresenceProfesseur();
        p.setProfesseur(prof);
        p.setDate(jour);
        p.setHeureArrivee(arrivee);
        p.setHeureDepart(depart);
        p.setLecteur("ENTREE");
        presences.save(p);
        scan(prof.getRfidUid(), jour.atTime(arrivee), ScanRfid.Resultat.ARRIVEE, prof);
        if (depart != null) scan(prof.getRfidUid(), jour.atTime(depart), ScanRfid.Resultat.DEPART, prof);
        return 1;
    }

    private void scan(String uid, java.time.LocalDateTime quand, ScanRfid.Resultat resultat, User prof) {
        ScanRfid s = new ScanRfid();
        s.setUid(uid == null ? "INCONNU" : uid);
        s.setHorodatage(quand);
        s.setLecteur("ENTREE");
        s.setResultat(resultat);
        s.setProfesseur(prof);
        scans.save(s);
    }

    // ── Notifications ────────────────────────────────────────────────

    private int genererNotifications() {
        List<String> titres = new ArrayList<>();
        notifications.findAll().forEach(n -> titres.add(n.getTitle()));
        int total = 0;
        total += notification(titres, "Bienvenue sur SmartDesk",
                "Consultez votre emploi du temps et vos notes, et posez vos questions administratives au chatbot.",
                Notification.NotifTarget.STUDENT, "ALL", Notification.NotifPriority.INFO);
        total += notification(titres, "Dépôt des dossiers de bourse",
                "Date limite : 30 novembre. Les dossiers incomplets ne seront pas traités.",
                Notification.NotifTarget.STUDENT, "ALL", Notification.NotifPriority.URGENT);
        total += notification(titres, "Examens du semestre 1 - Informatique",
                "Les examens de la filière Informatique commencent le lundi 4 janvier. "
                        + "Le calendrier détaillé est affiché au département.",
                Notification.NotifTarget.STUDENT, "Informatique", Notification.NotifPriority.WARNING);
        total += notification(titres, "Réunion pédagogique",
                "Réunion de tous les enseignants jeudi à 14h en salle B12 : préparation des examens.",
                Notification.NotifTarget.PROFESSOR, "ALL", Notification.NotifPriority.INFO);
        return total;
    }

    private int notification(List<String> existants, String titre, String contenu,
                             Notification.NotifTarget cible, String cibles, Notification.NotifPriority priorite) {
        if (existants.contains(titre)) return 0;
        Notification n = new Notification();
        n.setTitle(titre);
        n.setContent(contenu);
        n.setTarget(cible);
        n.setTargetIds(cibles);
        n.setPriority(priorite);
        n.setActive(true);
        n.setCreatedBy("admin");
        notifications.save(n);
        return 1;
    }

    /** Résumé pour les journaux du démarrage. */
    void journaliser(Map<String, Object> res) {
        log.info("Données de démonstration : {}", res);
    }
}
