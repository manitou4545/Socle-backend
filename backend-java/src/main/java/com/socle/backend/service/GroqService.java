package com.socle.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Appelle l'API Groq (https://groq.com), gratuite et compatible avec le format
 * "chat completions" d'OpenAI — utilisée ici à la place de l'API Anthropic
 * payante pour l'Assistant Minier de SOCLE.
 */
@Service
public class GroqService {

    @Value("${socle.groq.api-key:}")
    private String apiKey;

    @Value("${socle.groq.model:llama-3.3-70b-versatile}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public record ChatMessage(String role, String content) {}

    /** Envoie le prompt système + l'historique de conversation à Groq et retourne la réponse textuelle. */
    public String ask(String systemPrompt, List<ChatMessage> conversation) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "L'assistant IA n'est pas configuré sur ce serveur (clé Groq manquante).");
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            for (ChatMessage m : conversation) {
                messages.add(Map.of("role", m.role(), "content", m.content()));
            }

            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.4,
                    "max_tokens", 900
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "L'assistant IA (Groq) a renvoyé une erreur : " + response.body());
            }

            JsonNode json = mapper.readTree(response.body());
            JsonNode choices = json.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Réponse vide de l'assistant IA.");
            }
            return choices.get(0).get("message").get("content").asText();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Échec de l'appel à l'assistant IA : " + e.getMessage());
        }
    }
}
