package com.chatbot.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Données envoyées par le frontend lors de la connexion. */
@Data
public class LoginRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
    @Size(max = 100)
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire.")
    @Size(max = 200)
    private String password;
}
