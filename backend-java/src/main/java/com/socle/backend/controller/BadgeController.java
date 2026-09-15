package com.socle.backend.controller;

import com.socle.backend.model.BadgeEntity;
import com.socle.backend.repository.BadgeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lecture seule et publique : voir les badges d'un étudiant ne nécessite pas d'authentification
 *  (c'est une information de valorisation académique, pas une donnée sensible). L'ATTRIBUTION d'un
 *  badge, elle, reste exclusivement dans AdminController, protégée par authUtil.requireAdmin(). */
@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeRepository badgeRepository;

    public BadgeController(BadgeRepository badgeRepository) {
        this.badgeRepository = badgeRepository;
    }

    @GetMapping("/{matricule}")
    public List<BadgeEntity> forStudent(@PathVariable String matricule) {
        return badgeRepository.findByMatricule(matricule.toUpperCase());
    }
}
