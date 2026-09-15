package com.socle.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String matricule; // Format ENSMIP : 22A0199EM — conditionne l'accès

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String filiere; // IMTM, XMPE, RPC, EGLM, GMPG, SQE

    @Column(nullable = false)
    private Integer niveau;

    @Column(nullable = false)
    private Integer credits = 20;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    public Student() {}

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFiliere() { return filiere; }
    public void setFiliere(String filiere) { this.filiere = filiere; }
    public Integer getNiveau() { return niveau; }
    public void setNiveau(Integer niveau) { this.niveau = niveau; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
