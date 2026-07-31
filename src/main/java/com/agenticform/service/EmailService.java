package com.agenticform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String DEFAULT_FROM = "no-reply@agenticforms.com";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@agenticforms.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = resolveFrom(fromAddress);
        log.info("EmailService initialized with from={}", this.fromAddress);
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Réinitialisation de votre mot de passe AgenticForms");
        message.setText("""
                Bonjour,

                Vous avez demandé la réinitialisation de votre mot de passe AgenticForms.
                Cliquez sur le lien ci-dessous (valide 30 minutes) :

                %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.

                — AgenticForms
                """.formatted(resetLink));

        send(message, "password-reset", toEmail);
    }

    public void sendEmailVerificationEmail(String toEmail, String verifyLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Confirmez votre adresse e-mail AgenticForms");
        message.setText("""
                Bonjour,

                Merci de vous être inscrit sur AgenticForms.
                Cliquez sur le lien ci-dessous pour confirmer votre adresse e-mail (valide 24 heures) :

                %s

                Si vous n'êtes pas à l'origine de cette inscription, ignorez cet e-mail.

                — AgenticForms
                """.formatted(verifyLink));

        send(message, "email-verification", toEmail);
    }

    private void send(SimpleMailMessage message, String kind, String toEmail) {
        log.info("SMTP send start kind={} to={} from={}", kind, toEmail, fromAddress);
        try {
            mailSender.send(message);
            log.info("SMTP send success kind={} to={}", kind, toEmail);
        } catch (MailException ex) {
            log.error("SMTP send failed kind={} to={} from={}: {}",
                    kind, toEmail, fromAddress, ex.getMessage(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("SMTP send unexpected failure kind={} to={} from={}",
                    kind, toEmail, fromAddress, ex);
            throw ex;
        }
    }

    private static String resolveFrom(String configured) {
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        log.warn("app.mail.from is blank — falling back to {}", DEFAULT_FROM);
        return DEFAULT_FROM;
    }
}
