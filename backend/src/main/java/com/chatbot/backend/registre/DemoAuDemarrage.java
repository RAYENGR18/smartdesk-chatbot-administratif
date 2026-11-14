package com.chatbot.backend.registre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DEMO_DATA=true dans .env : charge les données fictives de démonstration au démarrage
 * (sans rien dupliquer ni modifier). À ne jamais activer sur un serveur réel.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.demo.au-demarrage", havingValue = "true")
public class DemoAuDemarrage implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAuDemarrage.class);

    private final DemoDataService demo;

    public DemoAuDemarrage(DemoDataService demo) {
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, Object> res = demo.charger();
            log.warn("""

                    ==============================================================
                     DONNÉES DE DÉMONSTRATION (DEMO_DATA=true)
                       {}
                     Comptes de démo : identifiant prenom.nom (ex. yasmine.trabelsi,
                     karim.haddad), mot de passe commun : DEMO_PASSWORD
                     Mettez DEMO_DATA=false sur un serveur réel.
                    ==============================================================""", res);
        } catch (Exception e) {
            log.error("Chargement des données de démonstration impossible", e);
        }
    }
}
