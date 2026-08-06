package com.socle.backend.controller;

import com.socle.backend.dto.BadgeRequest;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final DocumentRepository documentRepository;
    private final TransactionRepository transactionRepository;
    private final BadgeRepository badgeRepository;
    private final StudentRepository studentRepository;
    private final AuthUtil authUtil;

    public AdminController(DocumentRepository documentRepository, TransactionRepository transactionRepository,
                            BadgeRepository badgeRepository, StudentRepository studentRepository, AuthUtil authUtil) {
        this.documentRepository = documentRepository;
        this.transactionRepository = transactionRepository;
        this.badgeRepository = badgeRepository;
        this.studentRepository = studentRepository;
        this.authUtil = authUtil;
    }

    @GetMapping("/documents")
    public List<DocumentEntity> allDocuments(@RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        return documentRepository.findAllByOrderByDatePublicationDesc();
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        if (!documentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        documentRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/documents/{id}/feature")
    public ResponseEntity<?> toggleFeature(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        DocumentEntity d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        d.setFeatured(!d.isFeatured());
        documentRepository.save(d);
        return ResponseEntity.ok(d);
    }

    @PostMapping("/documents")
    @Transactional
    public ResponseEntity<?> publishAsAdmin(@RequestBody DocumentRequest req, @RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);

        DocumentEntity d = new DocumentEntity();
        d.setTitre(req.titre);
        d.setResume(req.resume);
        d.setType(req.type);
        d.setFiliere(req.filiere);
        d.setAuteur(req.auteur);
        d.setAnnee(req.annee);
        d.setAdminPublished(true);

        if (req.datePublication != null && !req.datePublication.isBlank()) {
            LocalDate date = LocalDate.parse(req.datePublication);
            d.setDatePublication(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        } else {
            d.setDatePublication(Instant.now());
        }

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
        return ResponseEntity.ok(d);
    }

    @GetMapping("/transactions")
    public List<TransactionEntity> transactions(@RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        return transactionRepository.findAllByOrderByDateDesc();
    }

    @GetMapping("/badges")
    public List<BadgeEntity> allBadges(@RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        return badgeRepository.findAll();
    }

    @PostMapping("/badges")
    public ResponseEntity<?> awardBadge(@RequestBody BadgeRequest req, @RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        if (!studentRepository.existsByMatricule(req.matricule.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Matricule inconnu");
        }
        if (badgeRepository.existsByMatriculeAndBadgeKey(req.matricule.toUpperCase(), req.badgeKey)) {
            return ResponseEntity.ok(Map.of("message", "Ce badge a déjà été attribué à cet étudiant."));
        }
        BadgeEntity b = new BadgeEntity();
        b.setMatricule(req.matricule.toUpperCase());
        b.setBadgeKey(req.badgeKey);
        b.setDocTitre(req.docTitre);
        badgeRepository.save(b);
        return ResponseEntity.ok(b);
    }

    @GetMapping("/students")
    public List<Student> allStudents(@RequestHeader("Authorization") String authHeader) {
        authUtil.requireAdmin(authHeader);
        return studentRepository.findAll();
    }
}
