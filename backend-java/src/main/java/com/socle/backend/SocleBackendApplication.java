package com.socle.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class SocleBackendApplication {

    public static void main(String[] args) {
        // Outil intégré : `java -jar socle-backend.jar --generate-hash=MonMotDePasse`
        // imprime le hash BCrypt à coller dans application.properties, puis quitte
        // sans démarrer le serveur. Voir README-backend.md.
        for (String arg : args) {
            if (arg.startsWith("--generate-hash=")) {
                String rawPassword = arg.substring("--generate-hash=".length());
                String hash = new BCryptPasswordEncoder().encode(rawPassword);
                System.out.println("\n=== Hash BCrypt généré ===");
                System.out.println(hash);
                System.out.println("Colle cette valeur dans application.properties -> socle.admin.password-hash\n");
                return;
            }
        }
        SpringApplication.run(SocleBackendApplication.class, args);
    }
}
