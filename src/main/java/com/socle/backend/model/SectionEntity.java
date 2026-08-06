package com.socle.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "sections")
public class SectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    @Column(nullable = false)
    private String titre;

    @Column(length = 8000)
    private String contenu;

    @Column(nullable = false)
    private boolean locked = false;

    private Integer prix = 0;

    public SectionEntity() {}

    public Long getId() { return id; }
    public DocumentEntity getDocument() { return document; }
    public void setDocument(DocumentEntity document) { this.document = document; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public Integer getPrix() { return prix; }
    public void setPrix(Integer prix) { this.prix = prix; }
}
