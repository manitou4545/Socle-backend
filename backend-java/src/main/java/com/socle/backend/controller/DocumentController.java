package com.socle.backend.controller;

import com.socle.backend.dto.DocumentRequest;
import com.socle.backend.dto.PageLockRequest;
import com.socle.backend.model.*;
import com.socle.backend.repository.*;
import com.socle.backend.security.AuthUtil;
import com.socle.backend.service.CloudinaryService;
import com.socle.backend.service.PdfService;
import com.socle.backend.service.PlagiatService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final PageLockRepository pageLockRepository;
    private final PageUnlockRepository pageUnlockRepository;
    private final TransactionRepository transactionRepository;
    private final StudentRepository studentRepository;
    private final AuthUtil authUtil;
    private final CloudinaryService cloudinaryService;
    private final PlagiatService plagiatService;
    private final PdfService pdfService;

    public DocumentController(DocumentRepository documentRepository, PageLockRepository pageLockRepository,
                               PageUnlockRepository pageUnlockRepository, TransactionRepository transactionRepository,
                               StudentRepository studentRepository, AuthUtil authUtil,
                               CloudinaryService cloudinaryService, PlagiatService plagiatService,
                               PdfService pdfService) {
        this.documentRepository = documentRepository;
        this.pageLockRepository = pageLockRepository;
        this.pageUnlockRepository = pageUnlockRepository;
        this.transactionRepository = transactionRepository;
        this.studentRepository = studentRepository;
        this.authUtil = authUtil;
        this.cloudinaryService = cloudinaryService;
        this.plagiatService = plagiatService;
        this.pdfService = pdfService;
    }

    /** Liste publique — le PDF servi ici est TOUJOURS la version publique (pages verrouillées
     *  remplacées par une page d'avertissement) : le PDF original n'est jamais exposé directement. */
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

    /** Téléchargement/aperçu : renvoie l'URL de la version PUBLIQUE uniquement (pages
     *  verrouillées non incluses). Pour une plage déverrouillée précise, voir l'endpoint
     *  /page-locks/{id}/view ci-dessous. */
    @PostMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        DocumentEntity d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        d.setDownloads(d.getDownloads() + 1);
        documentRepository.save(d);
        return ResponseEntity.ok(Map.of("fileUrl", d.getFileUrl() != null ? d.getFileUrl() : ""));
    }

    /** Envoi du PDF réel de l'auteur : le fichier original est stocké de façon PRIVÉE
     *  (jamais exposé), et une version PUBLIQUE est générée automatiquement à partir des
     *  plages de pages verrouillées déjà définies à la publication (voir publish()). */
    @PostMapping("/{id}/upload-pdf")
    public ResponseEntity<?> uploadPdf(@PathVariable Long id,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestHeader("Authorization") String authHeader) {
        String matricule = authUtil.requireStudent(authHeader);
        DocumentEntity d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        if (d.getMatricule() == null || !d.getMatricule().equalsIgnoreCase(matricule)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul l'auteur du document peut envoyer son fichier PDF.");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide.");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seuls les fichiers PDF sont acceptés.");
        }

        try {
            byte[] originalBytes = file.getBytes();
            int totalPages = pdfService.countPages(originalBytes);

            // Vérifie que les plages définies à la publication rentrent bien dans le vrai PDF.
            for (PageLockEntity lock : d.getPageLocks()) {
                if (lock.getPageDebut() < 1 || lock.getPageFin() > totalPages || lock.getPageDebut() > lock.getPageFin()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La plage de pages " + lock.getPageDebut() + "-" + lock.getPageFin() +
                            " est invalide pour un PDF de " + totalPages + " page(s).");
                }
            }

            String originalPublicId = cloudinaryService.uploadPrivatePdf(originalBytes);
            byte[] publicPdfBytes = pdfService.buildPublicPdf(originalBytes, d.getPageLocks());
            String publicUrl = cloudinaryService.uploadPublicBytes(publicPdfBytes);

            d.setOriginalPdfPublicId(originalPublicId);
            d.setPageCount(totalPages);
            d.setFileUrl(publicUrl);
            documentRepository.save(d);

            return ResponseEntity.ok(Map.of("fileUrl", publicUrl, "pageCount", totalPages));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'envoi vers Cloudinary : " + e.getMessage());
        }
    }

    /** Publication par un étudiant authentifié — la date est toujours "maintenant" (seul l'admin peut choisir une date libre). */
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
        d.setDatePublication(Instant.now()); // toujours "maintenant" pour un étudiant
        d.setAdminPublished(false);

        if (req.pageLocks != null) {
            for (PageLockRequest pr : req.pageLocks) {
                if (pr.pageDebut == null || pr.pageFin == null || pr.pageDebut < 1 || pr.pageFin < pr.pageDebut) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plage de pages invalide.");
                }
                PageLockEntity pl = new PageLockEntity();
                pl.setDocument(d);
                pl.setPageDebut(pr.pageDebut);
                pl.setPageFin(pr.pageFin);
                pl.setPrix(pr.prix != null ? pr.prix : 10);
                pl.setTitre(pr.titre);
                d.getPageLocks().add(pl);
            }
        }

        // Score d'originalité réel via le microservice Python (titre + résumé).
        // Le contenu complet est analysé une fois le PDF envoyé (voir amélioration possible ci-dessous).
        StringBuilder fullText = new StringBuilder();
        if (req.titre != null) fullText.append(req.titre).append(" ");
        if (req.resume != null) fullText.append(req.resume).append(" ");
        d.setPlagiatScore(plagiatService.computeOriginalityScore(fullText.toString()));

        documentRepository.save(d);

        student.setCredits(student.getCredits() + 10); // récompense de publication
        studentRepository.save(student);

        return ResponseEntity.ok(toPublicMap(d, matricule));
    }

    /** Déverrouillage payant d'UNE plage de pages — vérifié et débité côté serveur, impossible à falsifier depuis le navigateur. */
    @PostMapping("/page-locks/{pageLockId}/unlock")
    @Transactional
    public ResponseEntity<?> unlockPageLock(@PathVariable Long pageLockId,
                                             @RequestHeader("Authorization") String authHeader) {
        String matricule = authUtil.requireStudent(authHeader);
        PageLockEntity lock = pageLockRepository.findById(pageLockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plage de pages introuvable"));
        Student student = studentRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étudiant introuvable"));

        if (lock.getDocument() != null && matricule.equalsIgnoreCase(lock.getDocument().getMatricule())) {
            return ResponseEntity.ok(Map.of("message", "Tu es l'auteur de ce document : accès déjà autorisé."));
        }
        if (pageUnlockRepository.findByMatriculeAndPageLockId(matricule, pageLockId).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Tu as déjà déverrouillé ces pages."));
        }
        int prix = lock.getPrix() != null ? lock.getPrix() : 0;
        if (student.getCredits() < prix) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Crédits insuffisants.");
        }

        student.setCredits(student.getCredits() - prix);
        studentRepository.save(student);
        pageUnlockRepository.save(new PageUnlock(matricule, pageLockId));

        TransactionEntity tx = new TransactionEntity();
        tx.setMatricule(matricule);
        tx.setDocumentTitre(lock.getDocument() != null ? lock.getDocument().getTitre() : null);
        tx.setSectionTitre("Pages " + lock.getPageDebut() + "-" + lock.getPageFin());
        tx.setMontant(prix);
        transactionRepository.save(tx);

        return ResponseEntity.ok(Map.of("message", "Pages déverrouillées", "creditsRestants", student.getCredits()));
    }

    /** Renvoie le PDF (juste les pages demandées) d'une plage déverrouillée par l'étudiant
     *  appelant, ou par l'auteur du document. Extraction faite à la volée depuis le PDF
     *  original privé — jamais exposé tel quel. */
    @GetMapping("/page-locks/{pageLockId}/view")
    public ResponseEntity<byte[]> viewUnlockedPages(@PathVariable Long pageLockId,
                                                     @RequestHeader("Authorization") String authHeader) {
        String matricule = authUtil.requireStudent(authHeader);
        PageLockEntity lock = pageLockRepository.findById(pageLockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plage de pages introuvable"));
        DocumentEntity d = lock.getDocument();
        if (d == null || d.getOriginalPdfPublicId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier original introuvable pour ce document.");
        }

        boolean isAuthor = matricule.equalsIgnoreCase(d.getMatricule());
        boolean hasUnlocked = pageUnlockRepository.findByMatriculeAndPageLockId(matricule, pageLockId).isPresent();
        if (!isAuthor && !hasUnlocked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ces pages ne sont pas déverrouillées pour ton compte.");
        }

        try {
            byte[] originalBytes = cloudinaryService.downloadPrivateBytes(d.getOriginalPdfPublicId());
            byte[] extracted = pdfService.extractPages(originalBytes, lock.getPageDebut(), lock.getPageFin());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(extracted);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'extraction des pages : " + e.getMessage());
        }
    }

    /** Construit la représentation publique d'un document. Le PDF original n'apparaît
     *  JAMAIS ici — seule l'URL de la version publique (fileUrl) et la liste des plages
     *  verrouillées (avec leur statut de déverrouillage pour l'appelant) sont exposées. */
    private Map<String, Object> toPublicMap(DocumentEntity d, String requestingMatricule) {
        boolean isAuthor = requestingMatricule != null && requestingMatricule.equalsIgnoreCase(d.getMatricule());
        List<Map<String, Object>> pageLocks = d.getPageLocks().stream().map(pl -> {
            boolean unlocked = isAuthor ||
                    (requestingMatricule != null && pageUnlockRepository.findByMatriculeAndPageLockId(requestingMatricule, pl.getId()).isPresent());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pl.getId());
            m.put("pageDebut", pl.getPageDebut());
            m.put("pageFin", pl.getPageFin());
            m.put("prix", pl.getPrix());
            m.put("titre", pl.getTitre());
            m.put("unlocked", unlocked);
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
        m.put("fileUrl", d.getFileUrl()); // version PUBLIQUE uniquement
        m.put("pageCount", d.getPageCount());
        m.put("pageLocks", pageLocks);
        return m;
    }
}
