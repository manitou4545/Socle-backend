package com.socle.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "section_unlocks", uniqueConstraints = @UniqueConstraint(columnNames = {"matricule", "section_id"}))
public class SectionUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String matricule;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(nullable = false)
    private Instant date = Instant.now();

    public SectionUnlock() {}
    public SectionUnlock(String matricule, Long sectionId) {
        this.matricule = matricule;
        this.sectionId = sectionId;
    }

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }
}
