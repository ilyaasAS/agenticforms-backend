package com.agenticform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgenticFormApplication {

    /**
     * Point d'entrée principal de l'application.
     * Démarre le contexte Spring et le serveur embarqué (Tomcat).
     */
    public static void main(String[] args) {
        SpringApplication.run(AgenticFormApplication.class, args);
    }
}