package com.socle.backend.repository;

import com.socle.backend.model.BadgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BadgeRepository extends JpaRepository<BadgeEntity, Long> {
    List<BadgeEntity> findByMatricule(String matricule);
    boolean existsByMatriculeAndBadgeKey(String matricule, String badgeKey);
    List<BadgeEntity> findAll();
}
