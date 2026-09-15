package com.socle.backend.dto;

import java.util.List;

public class DocumentRequest {
    public String titre;
    public String resume;
    public String type;
    public String filiere;
    public String auteur;
    public Integer annee;
    public List<PageLockRequest> pageLocks;
    // Uniquement pris en compte si la requête vient de l'admin (date libre) :
    public String datePublication; // format ISO "2025-03-14"
}
