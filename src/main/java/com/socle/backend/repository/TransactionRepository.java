package com.socle.backend.repository;

import com.socle.backend.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findAllByOrderByDateDesc();
}
