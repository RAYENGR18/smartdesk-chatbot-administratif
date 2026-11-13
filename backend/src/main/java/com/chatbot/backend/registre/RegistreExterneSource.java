package com.chatbot.backend.registre;

import com.chatbot.backend.exception.ApiException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Mode "externe" : vérifie les inscriptions directement dans la base de données
 * de la faculté, en LECTURE SEULE, avec un compte dédié.
 *
 * Configuration (.env) :
 *   REGISTRE_MODE=externe
 *   REGISTRE_DB_URL=jdbc:postgresql://serveur-fac:5432/scolarite
 *   REGISTRE_DB_USERNAME=lecture_smartdesk
 *   REGISTRE_DB_PASSWORD=...
 *   REGISTRE_DB_REQUETE=SELECT cin, nom, prenom, type, filiere, niveau, email, matricule, departement
 *                       FROM vue_personnes WHERE cin = ?
 *
 * La requête doit contenir UN paramètre "?" (le CIN) et renvoyer des colonnes
 * portant ces noms (utiliser des alias "AS" si besoin). Seules cin, nom, prenom
 * et type sont obligatoires. Le pilote JDBC PostgreSQL est inclus ; pour MySQL,
 * Oracle ou SQL Server, ajouter le pilote correspondant dans pom.xml.
 */
@Component
@ConditionalOnProperty(name = "app.registre.mode", havingValue = "externe")
public class RegistreExterneSource implements RegistreSource {

    private static final Logger log = LoggerFactory.getLogger(RegistreExterneSource.class);

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final String requete;

    public RegistreExterneSource(@Value("${app.registre.externe.url:}") String url,
                                 @Value("${app.registre.externe.username:}") String username,
                                 @Value("${app.registre.externe.password:}") String password,
                                 @Value("${app.registre.externe.requete:}") String requete) {
        if (url.isBlank() || requete.isBlank()) {
            throw new IllegalStateException(
                    "REGISTRE_MODE=externe : REGISTRE_DB_URL et REGISTRE_DB_REQUETE sont obligatoires dans .env");
        }
        if (countParams(requete) != 1 || !requete.trim().toLowerCase(Locale.ROOT).startsWith("select")) {
            throw new IllegalStateException("REGISTRE_DB_REQUETE doit être un SELECT avec un seul paramètre '?' (le CIN)");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(5_000);
        config.setPoolName("registre-externe");
        // Pas de connexion au démarrage : le backend démarre même si la base de la fac est injoignable
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(5);
        this.requete = requete;
        log.info("Registre de l'établissement : base externe ({})", url.replaceAll("password=[^&;]*", "password=***"));
    }

    @Override
    public Optional<PersonneEtablissement> trouverParCin(String cin) {
        try {
            List<PersonneEtablissement> res = jdbc.query(requete, (rs, i) -> lire(rs), cin);
            return res.stream().findFirst();
        } catch (DataAccessException e) {
            log.error("Base de l'établissement injoignable : {}", e.getMostSpecificCause().getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La base de données de l'établissement est momentanément injoignable. Réessayez plus tard.");
        }
    }

    @Override
    public String mode() {
        return "externe";
    }

    @PreDestroy
    void fermer() {
        dataSource.close();
    }

    private static PersonneEtablissement lire(ResultSet rs) throws SQLException {
        Set<String> colonnes = new HashSet<>();
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            colonnes.add(md.getColumnLabel(i).toLowerCase(Locale.ROOT));
        }
        TypePersonne type = TypePersonne.depuis(col(rs, colonnes, "type"))
                .orElseThrow(() -> new SQLException("Colonne 'type' absente ou invalide (ETUDIANT / PROFESSEUR)"));
        return new PersonneEtablissement(
                col(rs, colonnes, "cin"), col(rs, colonnes, "nom"), col(rs, colonnes, "prenom"), type,
                col(rs, colonnes, "filiere"), col(rs, colonnes, "niveau"), col(rs, colonnes, "email"),
                col(rs, colonnes, "matricule"), col(rs, colonnes, "departement"));
    }

    private static String col(ResultSet rs, Set<String> colonnes, String nom) throws SQLException {
        if (!colonnes.contains(nom)) return null;
        String v = rs.getString(nom);
        return v == null ? null : v.trim();
    }

    private static int countParams(String sql) {
        int n = 0;
        for (char c : sql.toCharArray()) if (c == '?') n++;
        return n;
    }
}
