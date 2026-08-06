package com.socle.backend.repository;

import com.socle.backend.model.SectionUnlock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SectionUnlockRepository extends JpaRepository<SectionUnlock, Long> {
    Optional<SectionUnlock> findByMatriculeAndSectionId(String matricule, Long sectionId);
    List<SectionUnlock> findByMatricule(String matricule);
}
