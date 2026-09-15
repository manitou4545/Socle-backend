package com.socle.backend.repository;

import com.socle.backend.model.PageUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageUnlockRepository extends JpaRepository<PageUnlock, Long> {
    Optional<PageUnlock> findByMatriculeAndPageLockId(String matricule, Long pageLockId);
}
