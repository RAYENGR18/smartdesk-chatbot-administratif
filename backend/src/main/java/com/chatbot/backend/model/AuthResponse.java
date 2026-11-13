package com.chatbot.backend.model;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data

@AllArgsConstructor

public class AuthResponse {

    private String token;
    private String username;
    private String role;
    private Long userId;
}



