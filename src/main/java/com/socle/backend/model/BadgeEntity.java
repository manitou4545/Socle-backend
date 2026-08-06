package com.socle.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "badges")
public class BadgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String matricule;

    @Column(nullable = false)
    private String badgeKey;

    private String docTitre;

    @Column(nullable = false)
    private Instant date = Instant.now();

    public BadgeEntity() {}

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getBadgeKey() { return badgeKey; }
    public void setBadgeKey(String badgeKey) { this.badgeKey = badgeKey; }
    public String getDocTitre() { return docTitre; }
    public void setDocTitre(String d) { this.docTitre = d; }
    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }
}
