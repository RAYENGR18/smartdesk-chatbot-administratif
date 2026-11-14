package com.chatbot.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Petites valeurs internes de l'application (clé / valeur). Jamais exposées par l'API. */
@Entity
@Table(name = "parametres_systeme")
public class ParametreSysteme {

    @Id
    @Column(length = 100)
    private String cle;

    @Column(length = 255)
    private String valeur;

    protected ParametreSysteme() {
    }

    public ParametreSysteme(String cle, String valeur) {
        this.cle = cle;
        this.valeur = valeur;
    }

    public String getCle() {
        return cle;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }
}
