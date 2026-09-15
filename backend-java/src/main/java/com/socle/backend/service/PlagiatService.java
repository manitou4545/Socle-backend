package com.socle.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Appelle le microservice Python anti-plagiat (TF-IDF + similarité cosinus)
 * lors de la publication d'un document, pour remplacer le score fixe (90)
 * par un vrai score d'originalité calculé sur le texte soumis.
 *
 * Si le microservice est injoignable (pas encore déployé, en veille sur
 * Render, etc.), on retombe silencieusement sur un score par défaut plutôt
 * que de bloquer la publication de l'étudiant.
 */
@Service
public class PlagiatService {

    private static final int SCORE_PAR_DEFAUT = 90;

    @Value("${socle.plagiat.service-url:}")
    private String serviceUrl;

    @Value("${socle.plagiat.timeout-seconds:8}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /** Calcule le score d'originalité (0-100) du texte fourni via le microservice Python. */
    public int computeOriginalityScore(String fullText) {
        if (serviceUrl == null || serviceUrl.isBlank()) {
            return SCORE_PAR_DEFAUT; // microservice non configuré
        }
        try {
            String body = mapper.writeValueAsString(Map.of("text", fullText == null ? "" : fullText));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl.replaceAll("/+$", "") + "/analyze"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return SCORE_PAR_DEFAUT;
            }
            JsonNode json = mapper.readTree(response.body());
            if (json.has("originality_score")) {
                return json.get("originality_score").asInt(SCORE_PAR_DEFAUT);
            }
            return SCORE_PAR_DEFAUT;
        } catch (Exception e) {
            // Microservice en veille (Render free tier) ou indisponible : on ne bloque pas l'étudiant.
            return SCORE_PAR_DEFAUT;
        }
    }
}
