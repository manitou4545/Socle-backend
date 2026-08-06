package com.socle.backend.controller;

import com.socle.backend.dto.AdminLoginRequest;
import com.socle.backend.dto.RegisterRequest;
import com.socle.backend.model.Student;
import com.socle.backend.repository.StudentRepository;
import com.socle.backend.security.AdminLoginGuard;
import com.socle.backend.security.Session;
import com.socle.backend.security.TokenStore;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StudentRepository studentRepository;
    private final TokenStore tokenStore;
    private final AdminLoginGuard adminLoginGuard;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${socle.admin.password-hash}")
    private String adminPasswordHash;

    public AuthController(StudentRepository studentRepository, TokenStore tokenStore, AdminLoginGuard adminLoginGuard) {
        this.studentRepository = studentRepository;
        this.tokenStore = tokenStore;
        this.adminLoginGuard = adminLoginGuard;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String matricule = req.matricule.toUpperCase();
        if (studentRepository.existsByMatricule(matricule)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce matricule est déjà associé à un profil SOCLE.");
        }
        Student s = new Student();
        s.setName(req.name);
        s.setMatricule(matricule);
        s.setFiliere(req.filiere);
        s.setNiveau(req.niveau != null ? req.niveau : 1);
        s.setCredits(20);
        studentRepository.save(s);

        String token = tokenStore.issue(new Session(Session.Role.STUDENT, matricule));
        return ResponseEntity.ok(Map.of("token", token, "student", s));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String matricule = body.getOrDefault("matricule", "").toUpperCase();
        Student s = studentRepository.findByMatricule(matricule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Matricule inconnu. Inscris-toi d'abord."));
        String token = tokenStore.issue(new Session(Session.Role.STUDENT, matricule));
        return ResponseEntity.ok(Map.of("token", token, "student", s));
    }

    @PostMapping("/admin-login")
    public ResponseEntity<?> adminLogin(@Valid @RequestBody AdminLoginRequest req) {
        if (adminLoginGuard.isLocked()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Accès verrouillé. Réessaie dans " + adminLoginGuard.remainingLockSeconds() + "s.");
        }
        if ("CHANGE_ME_GENERE_TON_PROPRE_HASH_BCRYPT".equals(adminPasswordHash)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Aucun mot de passe admin n'a été configuré côté serveur.");
        }
        if (!encoder.matches(req.password, adminPasswordHash)) {
            adminLoginGuard.recordFailure();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mot de passe incorrect");
        }
        adminLoginGuard.recordSuccess();
        String token = tokenStore.issue(new Session(Session.Role.ADMIN, null));
        return ResponseEntity.ok(Map.of("token", token));
    }
}
