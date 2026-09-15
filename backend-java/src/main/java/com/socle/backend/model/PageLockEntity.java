package com.socle.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** Une plage de pages du PDF original, verrouillée par l'auteur et déverrouillable
 *  individuellement par les autres étudiants moyennant des crédits. */
@Entity
@Table(name = "page_locks")
public class PageLockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore // évite la récursion infinie PageLock -> Document -> PageLock -> ...
    @ManyToOne
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    @Column(nullable = false)
    private Integer pageDebut; // 1-indexé, inclus

    @Column(nullable = false)
    private Integer pageFin; // 1-indexé, inclus

    @Column(nullable = false)
    private Integer prix = 10; // prix en crédits pour déverrouiller cette plage

    @Column(length = 200)
    private String titre; // nom optionnel de la plage (ex: "Résultats sensibles") — utilisé par l'assistant IA et l'affichage

    public PageLockEntity() {}

    public Long getId() { return id; }
    public DocumentEntity getDocument() { return document; }
    public void setDocument(DocumentEntity document) { this.document = document; }
    public Integer getPageDebut() { return pageDebut; }
    public void setPageDebut(Integer pageDebut) { this.pageDebut = pageDebut; }
    public Integer getPageFin() { return pageFin; }
    public void setPageFin(Integer pageFin) { this.pageFin = pageFin; }
    public Integer getPrix() { return prix; }
    public void setPrix(Integer prix) { this.prix = prix; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
}
