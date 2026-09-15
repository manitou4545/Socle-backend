package com.socle.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Chaque étudiant paie pour SON PROPRE accès à une plage de pages ; le
 *  document reste verrouillé pour tous les autres tant qu'ils n'ont pas
 *  payé à leur tour (même principe que l'ancien SectionUnlock). */
@Entity
@Table(name = "page_unlocks", uniqueConstraints = @UniqueConstraint(columnNames = {"matricule", "page_lock_id"}))
public class PageUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String matricule;

    @Column(name = "page_lock_id", nullable = false)
    private Long pageLockId;

    @Column(nullable = false)
    private Instant date = Instant.now();

    public PageUnlock() {}
    public PageUnlock(String matricule, Long pageLockId) {
        this.matricule = matricule;
        this.pageLockId = pageLockId;
    }

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public Long getPageLockId() { return pageLockId; }
    public void setPageLockId(Long pageLockId) { this.pageLockId = pageLockId; }
    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }
}
