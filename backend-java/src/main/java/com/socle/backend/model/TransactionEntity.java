package com.socle.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String matricule;

    private String documentTitre;
    private String sectionTitre;

    @Column(nullable = false)
    private Integer montant;

    @Column(nullable = false)
    private Instant date = Instant.now();

    public TransactionEntity() {}

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getDocumentTitre() { return documentTitre; }
    public void setDocumentTitre(String d) { this.documentTitre = d; }
    public String getSectionTitre() { return sectionTitre; }
    public void setSectionTitre(String s) { this.sectionTitre = s; }
    public Integer getMontant() { return montant; }
    public void setMontant(Integer montant) { this.montant = montant; }
    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }
}
