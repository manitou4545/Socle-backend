package com.socle.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(length = 2000)
    private String resume;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String filiere;

    @Column(nullable = false)
    private String auteur;

    private String matricule;

    private Integer annee;

    @Column(nullable = false)
    private Instant datePublication = Instant.now();

    private Integer plagiatScore = 90;
    private Integer consultations = 0;
    private Integer downloads = 0;
    private boolean featured = false;
    private boolean adminPublished = false;
    private String fileUrl;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SectionEntity> sections = new ArrayList<>();

    public DocumentEntity() {}

    public Long getId() { return id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getResume() { return resume; }
    public void setResume(String resume) { this.resume = resume; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFiliere() { return filiere; }
    public void setFiliere(String filiere) { this.filiere = filiere; }
    public String getAuteur() { return auteur; }
    public void setAuteur(String auteur) { this.auteur = auteur; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public Integer getAnnee() { return annee; }
    public void setAnnee(Integer annee) { this.annee = annee; }
    public Instant getDatePublication() { return datePublication; }
    public void setDatePublication(Instant d) { this.datePublication = d; }
    public Integer getPlagiatScore() { return plagiatScore; }
    public void setPlagiatScore(Integer p) { this.plagiatScore = p; }
    public Integer getConsultations() { return consultations; }
    public void setConsultations(Integer c) { this.consultations = c; }
    public Integer getDownloads() { return downloads; }
    public void setDownloads(Integer d) { this.downloads = d; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean f) { this.featured = f; }
    public boolean isAdminPublished() { return adminPublished; }
    public void setAdminPublished(boolean a) { this.adminPublished = a; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public List<SectionEntity> getSections() { return sections; }
    public void setSections(List<SectionEntity> s) { this.sections = s; }
}
