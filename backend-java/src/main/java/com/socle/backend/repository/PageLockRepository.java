package com.socle.backend.repository;

import com.socle.backend.model.PageLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageLockRepository extends JpaRepository<PageLockEntity, Long> {
}
