package com.socle.backend.controller;

import com.socle.backend.dto.DocumentRequest;
import com.socle.backend.dto.SectionRequest;
import com.socle.backend.model.*;
import com.socle.backend.repository.*;
import com.socle.backend.security.AuthUtil;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final SectionRepository sectionRepository;
    private final SectionUnlockRepository unlockRepository;
    private final TransactionRepository transactionRepository;
    private final StudentRepository studentRepository;
    private final AuthUtil authUtil;

    public DocumentController(DocumentRepository documentRepository, SectionRepository sectionRepository,
                               SectionUnlockRepository unlockRepository, TransactionRepository transactionRepository,
                               StudentRepository studentRepository, AuthUtil authUtil) {
        this.documentRepository = documentRepository;
        this.sectionRepository = sectionRepository;
        this.unlockRepository = unlockRepository;
        this.transactionRepository = transactionRepository;
        this.studentRepository = studentRepository;
        this.authUtil = authUtil;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String matricule = authUtil.optionalStudent(authHeader);
        return documentRepository.findAllByOrderByDatePublicationDesc().stream()
                .map(d -> toPublicMap(d, matricule))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id,
                                    @RequestHeader(value = "Authorization", required = false) String authHeader) {
        DocumentEntity d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        String matricule = authUtil.optionalStudent(authHeader);
        return toPublicMap(d, matricule);
    }

    @PostMapping("/{id}/consult")
    public ResponseEntity<?> consult(@PathVariable Long id) {
        DocumentEntity d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        d.setConsultations(d.getConsultations() + 1);
        documentRepository.save(d);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        DocumentEntity d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        d.setDownloads(d.getDownloads() + 1);
        documentRepository.save(d);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> publish(@RequestBody DocumentRequest req,
                                      @RequestHeader("Authorization") String authHeader) {
        String matricule = authUtil.requireStudent(authHeader);
        Student student = studentRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étudiant introuvable"));

        DocumentEntity d = new DocumentEntity();
        d.setTitre(req.titre);
        d.setResume(req.resume);
        d.setType(req.type);
        d.setFiliere(req.filiere);
        d.setAuteur(req.auteur != null ? req.auteur : student.getName());
        d.setMatricule(matricule);
        d.setAnnee(req.annee);
        d.setDatePublication(Instant.now());
        d.setAdminPublished(false);

        if (req.sections != null) {
            for (SectionRequest sr : req.sections) {
                SectionEntity se = new SectionEntity();
                se.setDocument(d);
                se.setTitre(sr.titre);
                se.setContenu(sr.contenu);
                se.setLocked(sr.locked);
                se.setPrix(sr.prix != null ? sr.prix : 0);
                d.getSections().add(se);
            }
        }
        documentRepository.save(d);

        student.setCredits(student.getCredits() + 10);
        studentRepository.save(student);

        return ResponseEntity.ok(toPublicMap(d, matricule));
    }

    @PostMapping("/sections/{sectionId}/unlock")
    @Transactional
    public ResponseEntity<?> unlockSection(@PathVariable Long sectionId,
                                            @RequestHeader("Authorization") String authHeader) {
        String matricule = authUtil.requireStudent(authHeader);
        SectionEntity section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section introuvable"));
        Student student = studentRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étudiant introuvable"));

        if (!section.isLocked()) {
            return ResponseEntity.ok(Map.of("message", "Cette section est déjà en accès libre."));
        }
        if (unlockRepository.findByMatriculeAndSectionId(matricule, sectionId).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Tu as déjà déverrouillé cette section."));
        }
        int prix = section.getPrix() != null ? section.getPrix() : 0;
        if (student.getCredits() < prix) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Crédits insuffisants.");
        }

        student.setCredits(student.getCredits() - prix);
        studentRepository.save(student);
        unlockRepository.save(new SectionUnlock(matricule, sectionId));

        TransactionEntity tx = new TransactionEntity();
        tx.setMatricule(matricule);
        tx.setDocumentTitre(section.getDocument().getTitre());
        tx.setSectionTitre(section.getTitre());
        tx.setMontant(prix);
        transactionRepository.save(tx);

        return ResponseEntity.ok(Map.of("message", "Section déverrouillée", "creditsRestants", student.getCredits()));
    }

    private Map<String, Object> toPublicMap(DocumentEntity d, String requestingMatricule) {
        boolean isAuthor = requestingMatricule != null && requestingMatricule.equalsIgnoreCase(d.getMatricule());
        List<Map<String, Object>> sections = d.getSections().stream().map(s -> {
            boolean accessible = !s.isLocked() || isAuthor ||
                    (requestingMatricule != null && unlockRepository.findByMatriculeAndSectionId(requestingMatricule, s.getId()).isPresent());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("titre", s.getTitre());
            m.put("locked", s.isLocked());
            m.put("prix", s.getPrix());
            m.put("contenu", accessible ? s.getContenu() : null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("titre", d.getTitre());
        m.put("resume", d.getResume());
        m.put("type", d.getType());
        m.put("filiere", d.getFiliere());
        m.put("auteur", d.getAuteur());
        m.put("matricule", d.getMatricule());
        m.put("annee", d.getAnnee());
        m.put("datePublication", d.getDatePublication());
        m.put("plagiatScore", d.getPlagiatScore());
        m.put("consultations", d.getConsultations());
        m.put("downloads", d.getDownloads());
        m.put("featured", d.isFeatured());
        m.put("adminPublished", d.isAdminPublished());
        m.put("sections", sections);
        return m;
    }
}
