package com.agenticform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Cette annotation active l'auto-configuration, le scan des composants
// (controllers, services, repositories) et la configuration Spring Boot.
@SpringBootApplication
public class AgenticFormApplication {

    /**
     * Point d'entrée principal de l'application.
     * Démarre le contexte Spring et le serveur embarqué (Tomcat).
     */
    public static void main(String[] args) {
        SpringApplication.run(AgenticFormApplication.class, args);
    }
}