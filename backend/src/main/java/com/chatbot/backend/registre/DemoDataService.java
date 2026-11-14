package com.chatbot.backend.registre;

import com.chatbot.backend.model.Filiere;
import com.chatbot.backend.model.Matiere;
import com.chatbot.backend.model.Salle;
import com.chatbot.backend.repository.FiliereRepository;
import com.chatbot.backend.repository.MatiereRepository;
import com.chatbot.backend.repository.SalleRepository;
import com.chatbot.backend.util.TabularFileReader;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Données FICTIVES pour la démonstration : 4 filières, 16 étudiants et
 * 9 professeurs dans le registre (CIN commençant par 99), des matières
 * par niveau et des salles, puis l'activité associée (comptes, emplois du temps,
 * notes, présences, notifications : voir {@link DemoActiviteService}).
 * N'écrase rien : seuls les éléments absents sont créés.
 */
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private final RegistreService registreService;
    private final FiliereRepository filiereRepository;
    private final MatiereRepository matiereRepository;
    private final SalleRepository salleRepository;
    private final DemoActiviteService demoActiviteService;

    @Transactional
    public Map<String, Object> charger() throws IOException {
        int filieres = 0, matieres = 0, salles = 0;

        filieres += filiere("INFO", "Informatique", "Informatique");
        filieres += filiere("CHIM", "Chimie", "Sciences de la matière");
        filieres += filiere("MATH", "Mathématiques appliquées", "Mathématiques");
        filieres += filiere("GEST", "Gestion", "Économie et gestion");

        matieres += matiere("INFO-L1-ALGO", "Algorithmique et programmation", "INFO", "L1", 60);
        matieres += matiere("INFO-L1-ARCHI", "Architecture des ordinateurs", "INFO", "L1", 45);
        matieres += matiere("INFO-L1-MATH", "Mathématiques pour l'informatique", "INFO", "L1", 45);
        matieres += matiere("INFO-L2-BD", "Bases de données", "INFO", "L2", 45);
        matieres += matiere("INFO-L2-POO", "Programmation orientée objet", "INFO", "L2", 60);
        matieres += matiere("INFO-L3-WEB", "Développement web", "INFO", "L3", 45);
        matieres += matiere("INFO-L3-IA", "Introduction à l'intelligence artificielle", "INFO", "L3", 45);
        matieres += matiere("INFO-M1-ML", "Apprentissage automatique", "INFO", "M1", 45);
        matieres += matiere("INFO-M1-CLOUD", "Cloud computing", "INFO", "M1", 45);
        matieres += matiere("CHIM-L1-GEN", "Chimie générale", "CHIM", "L1", 60);
        matieres += matiere("CHIM-L1-PHYS", "Physique générale", "CHIM", "L1", 45);
        matieres += matiere("CHIM-L2-ORG", "Chimie organique", "CHIM", "L2", 60);
        matieres += matiere("CHIM-L3-ANA", "Chimie analytique", "CHIM", "L3", 45);
        matieres += matiere("MATH-L1-ANA", "Analyse 1", "MATH", "L1", 60);
        matieres += matiere("MATH-L1-ALG", "Algèbre 1", "MATH", "L1", 60);
        matieres += matiere("MATH-L2-ANA2", "Analyse 2", "MATH", "L2", 60);
        matieres += matiere("MATH-L2-PROBA", "Probabilités et statistiques", "MATH", "L2", 45);
        matieres += matiere("MATH-M1-OPT", "Optimisation", "MATH", "M1", 45);
        matieres += matiere("GEST-L1-COMPTA", "Comptabilité générale", "GEST", "L1", 45);
        matieres += matiere("GEST-L1-ECO", "Microéconomie", "GEST", "L1", 45);
        matieres += matiere("GEST-L2-MKT", "Marketing", "GEST", "L2", 45);
        matieres += matiere("GEST-L3-FIN", "Finance d'entreprise", "GEST", "L3", 45);

        salles += salle("Amphi A", Salle.TypeSalle.AMPHI, 150);
        salles += salle("Salle B12", Salle.TypeSalle.SALLE_TD, 40);
        salles += salle("Labo Info 1", Salle.TypeSalle.SALLE_INFO, 30);
        salles += salle("Labo Chimie", Salle.TypeSalle.LABO, 25);
        salles += salle("Amphi B", Salle.TypeSalle.AMPHI, 120);
        salles += salle("Salle C04", Salle.TypeSalle.SALLE_TD, 35);
        salles += salle("Salle C05", Salle.TypeSalle.SALLE_TD, 35);
        salles += salle("Labo Info 2", Salle.TypeSalle.SALLE_INFO, 30);

        RegistreService.ResultatImport registre;
        ClassPathResource csv = new ClassPathResource("demo/registre_demo.csv");
        try (InputStream in = csv.getInputStream()) {
            registre = registreService.importerLignes(
                    TabularFileReader.lire("registre_demo.csv", in.readAllBytes()), "DEMO");
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("filieresCreees", filieres);
        res.put("matieresCreees", matieres);
        res.put("sallesCreees", salles);
        res.put("registre", registre);
        res.putAll(demoActiviteService.remplir());
        return res;
    }

    private int filiere(String code, String nom, String departement) {
        if (filiereRepository.existsByCode(code)) return 0;
        Filiere f = new Filiere();
        f.setCode(code);
        f.setNom(nom);
        f.setDepartement(departement);
        f.setNiveau("Licence / Master");
        f.setCapaciteMax(120);
        f.setDescription("Filière de démonstration");
        filiereRepository.save(f);
        return 1;
    }

    private int matiere(String code, String nom, String filiereCode, String niveau, int volume) {
        if (matiereRepository.existsByCode(code)) return 0;
        Filiere f = filiereRepository.findByCode(filiereCode).orElseThrow();
        Matiere m = new Matiere();
        m.setCode(code);
        m.setNom(nom);
        m.setNiveau(niveau);
        m.setCoefficient(2.0);
        m.setVolumeHoraire(volume);
        m.setFiliere(f);
        m.getFilieres().add(f);
        matiereRepository.save(m);
        return 1;
    }

    private int salle(String nom, Salle.TypeSalle type, int capacite) {
        if (salleRepository.existsByNom(nom)) return 0;
        Salle s = new Salle();
        s.setNom(nom);
        s.setType(type);
        s.setCapacite(capacite);
        s.setDisponible(true);
        s.setLocalisation("Bâtiment principal");
        salleRepository.save(s);
        return 1;
    }
}
