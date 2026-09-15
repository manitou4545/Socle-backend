package com.socle.backend.dto;

import java.util.List;

public class AiAskRequest {
    public Long documentId;
    public String question;
    public List<AiMessage> history; // historique court de la conversation en cours (optionnel)

    public static class AiMessage {
        public String role;  // "user" ou "assistant"
        public String text;
    }
}
