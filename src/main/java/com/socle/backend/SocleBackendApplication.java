package com.socle.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class SocleBackendApplication {

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--generate-hash=")) {
                String rawPassword = arg.substring("--generate-hash=".length());
                String hash = new BCryptPasswordEncoder().encode(rawPassword);
                System.out.println("\n=== Hash BCrypt généré ===");
                System.out.println(hash);
                return;
            }
        }
        SpringApplication.run(SocleBackendApplication.class, args);
    }
}
