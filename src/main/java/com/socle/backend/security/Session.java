package com.socle.backend.security;

public class Session {
    public enum Role { STUDENT, ADMIN }

    private final Role role;
    private final String matricule;

    public Session(Role role, String matricule) {
        this.role = role;
        this.matricule = matricule;
    }
    public Role getRole() { return role; }
    public String getMatricule() { return matricule; }
}
