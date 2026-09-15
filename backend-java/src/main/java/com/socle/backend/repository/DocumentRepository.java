package com.socle.backend.repository;

import com.socle.backend.model.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findAllByOrderByDatePublicationDesc();
    List<DocumentEntity> findByMatricule(String matricule);
}
