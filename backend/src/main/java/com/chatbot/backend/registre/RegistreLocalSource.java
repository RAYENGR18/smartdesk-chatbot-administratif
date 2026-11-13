package com.chatbot.backend.registre;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Mode par défaut : registre stocké dans la base SmartDesk (import Excel/CSV ou démo). */
@Component
@ConditionalOnProperty(name = "app.registre.mode", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class RegistreLocalSource implements RegistreSource {

    private final RegistreEtablissementRepository repository;

    @Override
    public Optional<PersonneEtablissement> trouverParCin(String cin) {
        return repository.findByCin(cin).map(RegistreEtablissement::versPersonne);
    }

    @Override
    public String mode() {
        return "local";
    }
}
