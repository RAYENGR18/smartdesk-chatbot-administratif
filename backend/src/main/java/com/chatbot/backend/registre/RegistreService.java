package com.chatbot.backend.registre;

import com.chatbot.backend.exception.ApiException;
import com.chatbot.backend.model.User;
import com.chatbot.backend.repository.UserRepository;
import com.chatbot.backend.util.Niveaux;
import com.chatbot.backend.util.TabularFileReader;
import com.chatbot.backend.util.TabularFileReader.Ligne;
import com.chatbot.backend.util.TextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Gestion du registre local : import Excel/CSV, consultation, statistiques.
 *
 * Colonnes reconnues (en-têtes libres, accents et casse ignorés) :
 *   cin* | nom* | prenom* | type* (etudiant/professeur) | filiere | niveau
 *   | email | matricule | departement
 * L'import met à jour une personne déjà présente (même CIN) et ajoute les autres.
 */
@Service
@RequiredArgsConstructor
public class RegistreService {

    private final RegistreEtablissementRepository repository;
    private final UserRepository userRepository;

    public record ResultatImport(int lignes, int ajoutes, int misAJour, int ignores, List<String> erreurs) {
    }

    @Transactional
    public ResultatImport importer(MultipartFile fichier) {
        return importerLignes(TabularFileReader.lire(fichier), "IMPORT");
    }

    @Transactional
    public ResultatImport importerLignes(List<Ligne> lignes, String origine) {
        int ajoutes = 0, misAJour = 0, ignores = 0;
        List<String> erreurs = new ArrayList<>();
        Set<String> vus = new HashSet<>();

        for (Ligne l : lignes) {
            String cin = TextUtils.normaliserCin(l.get("cin", "numerocin", "carteidentite", "cartedidentite", "numcin", "cinpasseport"));
            String nom = l.get("nom", "nomdefamille", "lastname");
            String prenom = l.get("prenom", "firstname");
            String typeBrut = l.get("type", "role", "profil", "categorie", "statut");
            String filiere = l.get("filiere", "codefiliere", "specialite", "section");
            String niveauBrut = l.get("niveau", "niveaudetudes", "annee", "classe");

            String prefixe = "Ligne " + l.numero() + " : ";
            if (cin.isEmpty() || !cin.matches("[A-Z0-9]{5,20}")) {
                erreurs.add(prefixe + "CIN manquant ou invalide.");
                ignores++;
                continue;
            }
            if (nom.isEmpty() || prenom.isEmpty()) {
                erreurs.add(prefixe + "nom ou prénom manquant (CIN " + cin + ").");
                ignores++;
                continue;
            }
            Optional<TypePersonne> type = TypePersonne.depuis(typeBrut);
            if (type.isEmpty()) {
                erreurs.add(prefixe + "type invalide « " + typeBrut + " » (attendu : etudiant ou professeur).");
                ignores++;
                continue;
            }
            String niveau = null;
            if (!niveauBrut.isEmpty()) {
                niveau = Niveaux.normaliser(niveauBrut).orElse(null);
                if (niveau == null) {
                    erreurs.add(prefixe + "niveau non reconnu « " + niveauBrut + " » (ex : L1, L2, L3, M1, M2).");
                    ignores++;
                    continue;
                }
            }
            if (!vus.add(cin)) {
                erreurs.add(prefixe + "CIN " + cin + " en double dans le fichier (ligne ignorée).");
                ignores++;
                continue;
            }

            RegistreEtablissement r = repository.findByCin(cin).orElse(null);
            if (r == null) {
                r = new RegistreEtablissement();
                r.setCin(cin);
                ajoutes++;
            } else {
                misAJour++;
            }
            r.setNom(limiter(nom, 100));
            r.setPrenom(limiter(prenom, 100));
            r.setType(type.get());
            r.setFiliere(vide(limiter(filiere, 100)));
            r.setNiveau(niveau);
            r.setEmail(vide(limiter(l.get("email", "mail", "courriel"), 150)));
            r.setMatricule(vide(limiter(l.get("matricule", "numeroetudiant", "idetudiant"), 50)));
            r.setDepartement(vide(limiter(l.get("departement", "service"), 150)));
            r.setOrigine(origine);
            r.setDateMiseAJour(LocalDateTime.now());
            repository.save(r);
        }
        return new ResultatImport(lignes.size(), ajoutes, misAJour, ignores, erreurs);
    }

    public Page<Map<String, Object>> rechercher(String q, String type, int page, int size) {
        TypePersonne t = type == null || type.isBlank() ? null : TypePersonne.depuis(type).orElse(null);
        String recherche = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Page<RegistreEtablissement> res = repository.rechercher(recherche, t,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));

        Set<String> cinsAvecCompte = new HashSet<>();
        res.getContent().forEach(r -> userRepository.findByCinIgnoreCase(r.getCin())
                .ifPresent(u -> cinsAvecCompte.add(r.getCin())));

        return res.map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("cin", r.getCin());
            m.put("nom", r.getNom());
            m.put("prenom", r.getPrenom());
            m.put("type", r.getType());
            m.put("filiere", r.getFiliere());
            m.put("niveau", r.getNiveau());
            m.put("email", r.getEmail());
            m.put("matricule", r.getMatricule());
            m.put("departement", r.getDepartement());
            m.put("origine", r.getOrigine());
            m.put("compteCree", cinsAvecCompte.contains(r.getCin()));
            return m;
        });
    }

    public Map<String, Object> statistiques(String mode) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("mode", mode);
        s.put("total", repository.count());
        s.put("etudiants", repository.countByType(TypePersonne.ETUDIANT));
        s.put("professeurs", repository.countByType(TypePersonne.PROFESSEUR));
        s.put("comptesEtudiants", userRepository.countByRole(User.Role.USER));
        s.put("comptesProfesseurs", userRepository.countByRole(User.Role.PROFESSEUR));
        return s;
    }

    @Transactional
    public void supprimer(Long id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("Entrée du registre introuvable.");
        }
        repository.deleteById(id);
    }

    private static String limiter(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
