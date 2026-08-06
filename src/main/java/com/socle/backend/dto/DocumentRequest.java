package com.socle.backend.dto;

import java.util.List;

public class DocumentRequest {
    public String titre;
    public String resume;
    public String type;
    public String filiere;
    public String auteur;
    public Integer annee;
    public List<SectionRequest> sections;
    public String datePublication;
}
