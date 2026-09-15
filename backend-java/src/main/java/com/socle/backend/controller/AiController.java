package com.socle.backend.controller;

import com.socle.backend.dto.AiAskRequest;
import com.socle.backend.model.DocumentEntity;
import com.socle.backend.model.PageLockEntity;
import com.socle.backend.repository.DocumentRepository;
import com.socle.backend.repository.PageUnlockRepository;
import com.socle.backend.security.AuthUtil;
import com.socle.backend.service.CloudinaryService;
import com.socle.backend.service.GroqService;
import com.socle.backend.service.PdfService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assistant Minier de SOCLE — tuteur académique/technique basé sur Groq.
 * Toute la logique de contexte (plan du document, texte accessible, catalogue
 * des autres mémoires) est construite ICI côté serveur : la clé API Groq
 * n'est jamais exposée au navigateur.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final DocumentRepository documentRepository;
    private final PageUnlockRepository pageUnlockRepository;
    private final AuthUtil authUtil;
    private final CloudinaryService cloudinaryService;
    private final PdfService pdfService;
    private final GroqService groqService;

    public AiController(DocumentRepository documentRepository, PageUnlockRepository pageUnlockRepository,
                         AuthUtil authUtil, CloudinaryService cloudinaryService,
                         PdfService pdfService, GroqService groqService) {
        this.documentRepository = documentRepository;
        this.pageUnlockRepository = pageUnlockRepository;
        this.authUtil = authUtil;
        this.cloudinaryService = cloudinaryService;
        this.pdfService = pdfService;
        this.groqService = groqService;
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AiAskRequest req,
                                  @RequestHeader("Authorization") String authHeader) {
        String matricule = authUtil.requireStudent(authHeader);
        if (req.question == null || req.question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La question ne peut pas être vide.");
        }
        DocumentEntity doc = documentRepository.findById(req.documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        String systemPrompt = buildSystemPrompt(doc, matricule);

        List<GroqService.ChatMessage> conversation = new ArrayList<>();
        if (req.history != null) {
            for (AiAskRequest.AiMessage m : req.history) {
                String role = "assistant".equalsIgnoreCase(m.role) ? "assistant" : "user";
                conversation.add(new GroqService.ChatMessage(role, m.text));
            }
        }
        conversation.add(new GroqService.ChatMessage("user", req.question));

        String reply = groqService.ask(systemPrompt, conversation);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /** Construit le prompt système complet : identité, règles de comportement,
     *  plan du document courant (avec statut verrouillé/déverrouillé), texte
     *  accessible, et catalogue condensé des autres mémoires de la bibliothèque. */
    private String buildSystemPrompt(DocumentEntity doc, String matricule) {
        boolean isAuthor = matricule.equalsIgnoreCase(doc.getMatricule());

        StringBuilder plan = new StringBuilder();
        for (PageLockEntity pl : doc.getPageLocks()) {
            boolean unlocked = isAuthor || pageUnlockRepository.findByMatriculeAndPageLockId(matricule, pl.getId()).isPresent();
            String nom = (pl.getTitre() != null && !pl.getTitre().isBlank()) ? pl.getTitre() : "Section sans titre";
            plan.append("- ").append(nom).append(" (pages ").append(pl.getPageDebut()).append("-").append(pl.getPageFin())
                    .append(") : ").append(unlocked ? "ACCESSIBLE" : "VERROUILLÉE").append("\n");
        }
        if (doc.getPageLocks().isEmpty()) {
            plan.append("(Aucune page verrouillée sur ce document : il est intégralement accessible.)\n");
        }

        String texteAccessible = "(Résumé uniquement — le PDF n'est pas encore disponible ou n'a pas pu être lu.)";
        if (doc.getFileUrl() != null && !doc.getFileUrl().isBlank()) {
            try {
                byte[] publicPdfBytes = cloudinaryService.downloadPublicBytes(doc.getFileUrl());
                texteAccessible = pdfService.extractText(publicPdfBytes);
                if (texteAccessible.length() > 12000) {
                    texteAccessible = texteAccessible.substring(0, 12000) + "\n[...texte tronqué...]";
                }
            } catch (Exception e) {
                // Le PDF n'a pas pu être récupéré (service en veille, etc.) : on retombe sur le résumé seul.
            }
        }

        StringBuilder catalogue = new StringBuilder();
        List<DocumentEntity> autres = documentRepository.findAll();
        for (DocumentEntity autre : autres) {
            if (autre.getId().equals(doc.getId())) continue;
            boolean autreEstAuteur = matricule.equalsIgnoreCase(autre.getMatricule());
            catalogue.append("• \"").append(autre.getTitre()).append("\" — ")
                    .append(autre.getType()).append(", ").append(autre.getFiliere())
                    .append(", ").append(autre.getAnnee()).append(". Résumé : ")
                    .append(autre.getResume() != null ? autre.getResume() : "(aucun)").append(". ");
            if (autre.getPageLocks().isEmpty()) {
                catalogue.append("Intégralement accessible.\n");
            } else {
                List<String> plages = new ArrayList<>();
                for (PageLockEntity pl : autre.getPageLocks()) {
                    boolean unlocked = autreEstAuteur || pageUnlockRepository.findByMatriculeAndPageLockId(matricule, pl.getId()).isPresent();
                    String nom = (pl.getTitre() != null && !pl.getTitre().isBlank()) ? pl.getTitre() : ("pages " + pl.getPageDebut() + "-" + pl.getPageFin());
                    plages.add(nom + " [" + (unlocked ? "accessible" : "verrouillée") + "]");
                }
                catalogue.append("Sections : ").append(String.join(", ", plages)).append(".\n");
            }
        }
        if (autres.size() <= 1) {
            catalogue.append("(Aucun autre mémoire dans la bibliothèque pour l'instant.)\n");
        }

        return """
            Tu es l'Assistant Minier officiel de la plateforme SOCLE (ENSMIP). Ton rôle est d'agir comme un tuteur académique et technique pour les élèves ingénieurs.

            Règles de comportement :
            1. ANALYSE DU FOND ET DE LA FORME : Tu aides l'étudiant à analyser la structure du document (la forme, le plan, la rigueur académique) ainsi que les concepts techniques abordés (le fond), en te basant sur le texte réellement fourni ci-dessous.
            2. POSTURE DE TUTEUR : Ne fais jamais le travail à la place de l'étudiant. Guide-le, explique les notions et pose des questions pour stimuler sa réflexion. Ne rédige jamais un paragraphe, un calcul complet ou une conclusion prêts à copier-coller — décompose plutôt en étapes et laisse l'étudiant faire le travail lui-même à chaque étape, même s'il insiste, reformule sa demande, ou prétend que c'est urgent.
            3. GESTION DES SECTIONS VERROUILLÉES :
               - Tu as connaissance du plan complet du document (voir ci-dessous), y compris les titres et plages de pages des sections verrouillées — mais jamais leur contenu.
               - Si la question de l'étudiant concerne une section accessible, réponds en te basant sur le texte fourni.
               - Si la question porte explicitement sur une section verrouillée, réponds exactement sur ce modèle : "La réponse à cette question se trouve dans la section [Nom de la section], qui est actuellement verrouillée. Débloquez le document pour y accéder." N'invente JAMAIS le contenu d'une section verrouillée, même partiellement.
            4. RIGUEUR TECHNIQUE : Utilise un vocabulaire d'ingénieur précis et professionnel, en français.
            5. RECOMMANDATIONS CROISÉES : Tu as connaissance du catalogue résumé des autres mémoires de la bibliothèque SOCLE (voir ci-dessous). Si pertinent pour la question de l'étudiant, recommande-lui d'autres mémoires en indiquant leur titre — et précise si la section pertinente y est verrouillée ou non, sans jamais en inventer le contenu.

            ============================================================
            DOCUMENT EN COURS DE LECTURE
            ============================================================
            Titre : %s
            Type : %s — Filière %s — %s
            Auteur : %s
            Résumé : %s

            Plan du document (titres et statut d'accès) :
            %s

            Texte accessible du document (pages non verrouillées) :
            %s

            ============================================================
            CATALOGUE DES AUTRES MÉMOIRES DE LA BIBLIOTHÈQUE SOCLE
            ============================================================
            %s
            """.formatted(
                doc.getTitre(), doc.getType(), doc.getFiliere(), doc.getAnnee(), doc.getAuteur(),
                doc.getResume() != null ? doc.getResume() : "(aucun résumé fourni)",
                plan.toString(), texteAccessible, catalogue.toString()
        );
    }
}
