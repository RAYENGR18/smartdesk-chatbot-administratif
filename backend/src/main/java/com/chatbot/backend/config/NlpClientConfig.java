package com.chatbot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Client HTTP vers le service NLP (FastAPI).
 * Chaque requête porte l'en-tête X-API-Key : le service NLP refuse
 * toute requête qui ne vient pas du backend.
 */
@Configuration
public class NlpClientConfig {

    public static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    public RestTemplate nlpRestTemplate(@Value("${app.nlp.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(120_000);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .forEach(c -> ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8));

        if (apiKey != null && !apiKey.isBlank()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().set(API_KEY_HEADER, apiKey);
                return execution.execute(request, body);
            });
        }
        return restTemplate;
    }
}
