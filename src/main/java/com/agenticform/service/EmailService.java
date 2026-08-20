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

    public void sendFormLoginCodeEmail(String toEmail, String code, String subject, String formTitle) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject(subject != null && !subject.isBlank() ? subject.trim() : "Votre code de sécurité");
        String formLabel = formTitle != null && !formTitle.isBlank() ? formTitle.trim() : "ce formulaire";
        message.setText("""
                Bonjour,

                Voici votre code de vérification pour accéder à %s :

                %s

                Ce code expire dans 15 minutes.
                Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.

                — AgenticForms
                """.formatted(formLabel, code));

        send(message, "form-login-code", toEmail);
    }

    public void sendBookingConfirmationEmail(
            String toEmail,
            String guestName,
            String title,
            String startLabel,
            String calendarLink,
            String cancelLink) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(guestName) ? guestName.trim() : "bonjour";
        String link = StringUtils.hasText(calendarLink)
                ? "\nOuvrir dans Google Calendar :\n" + calendarLink + "\n"
                : "";
        String cancel = StringUtils.hasText(cancelLink)
                ? "\nModifier ou annuler votre réservation :\n" + cancelLink + "\n"
                : "";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Réservation confirmée : " + safeTitle(title));
        message.setText("""
                Bonjour %s,

                Votre rendez-vous « %s » est confirmé.

                Date : %s
                %s%s
                À bientôt.

                — AgenticForms
                """.formatted(hello, safeTitle(title), startLabel, link, cancel));
        sendQuietly(message, "booking-confirmation", toEmail);
    }

    public void sendBookingCancellationEmail(String toEmail, String guestName, String title) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(guestName) ? guestName.trim() : "bonjour";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Réservation annulée : " + safeTitle(title));
        message.setText("""
                Bonjour %s,

                Votre rendez-vous « %s » a été annulé avec succès.

                Le créneau a été libéré dans le calendrier de l'organisateur.

                — AgenticForms
                """.formatted(hello, safeTitle(title)));
        sendQuietly(message, "booking-cancellation", toEmail);
    }

    public void sendCancellationNotificationToOrganizer(
            String toEmail, String organizerName, String guestName, String guestEmail, String title) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(organizerName) ? organizerName.trim() : "bonjour";
        String guest = StringUtils.hasText(guestName)
                ? guestName.trim() + " (" + guestEmail.trim() + ")"
                : guestEmail.trim();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Réservation annulée : " + safeTitle(title));
        message.setText("""
                Bonjour %s,

                La réservation « %s » a été annulée par le client.

                Client : %s

                Le créneau est à nouveau disponible.

                — AgenticForms
                """.formatted(hello, safeTitle(title), guest));
        sendQuietly(message, "booking-cancellation-organizer", toEmail);
    }

    public void sendBookingNotificationToOrganizer(
            String toEmail,
            String organizerName,
            String guestName,
            String guestEmail,
            String title,
            String startLabel,
            String calendarLink) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(organizerName) ? organizerName.trim() : "bonjour";
        String link = StringUtils.hasText(calendarLink)
                ? "\nOuvrir dans Google Calendar :\n" + calendarLink + "\n"
                : "";
        String guest = StringUtils.hasText(guestName)
                ? guestName.trim() + " (" + guestEmail.trim() + ")"
                : guestEmail.trim();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Nouvelle réservation : " + safeTitle(title));
        message.setText("""
                Bonjour %s,

                Vous avez une nouvelle réservation « %s ».

                Client : %s
                Date : %s
                %s
                — AgenticForms
                """.formatted(hello, safeTitle(title), guest, startLabel, link));
        sendQuietly(message, "booking-organizer-notification", toEmail);
    }

    public void sendBookingGuestInviteEmail(
            String toEmail,
            String invitedGuestName,
            String title,
            String startLabel,
            String calendarLink,
            String cancelLink,
            String bookedByName,
            String bookedByEmail,
            String inviterName,
            String inviterEmail) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(invitedGuestName) ? invitedGuestName.trim() : "bonjour";
        String bookedBy = StringUtils.hasText(bookedByName)
                ? bookedByName.trim() + " (" + bookedByEmail.trim() + ")"
                : bookedByEmail.trim();
        String invitedBy = StringUtils.hasText(inviterName)
                ? inviterName.trim() + " (" + inviterEmail.trim() + ")"
                : inviterEmail.trim();
        String link = StringUtils.hasText(calendarLink)
                ? "\nOuvrir dans Google Calendar :\n" + calendarLink + "\n"
                : "";
        String manage = StringUtils.hasText(cancelLink)
                ? "\nModifier ou annuler la réservation :\n" + cancelLink + "\n"
                : "";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Invitation à un rendez-vous : " + safeTitle(title));
        message.setText("""
                Bonjour %s,

                Vous avez été ajouté comme invité sur le rendez-vous « %s ».

                Réservé par : %s
                Ajouté en invité par : %s
                Date : %s
                %s%s
                — AgenticForms
                """.formatted(hello, safeTitle(title), bookedBy, invitedBy, startLabel, link, manage));
        sendQuietly(message, "booking-guest-invite", toEmail);
    }

    public void sendBookingReminderEmail(
            String toEmail,
            String guestName,
            String title,
            String startLabel,
            String calendarLink) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(guestName) ? guestName.trim() : "bonjour";
        String link = StringUtils.hasText(calendarLink)
                ? "\nOuvrir dans Google Calendar :\n" + calendarLink + "\n"
                : "";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Rappel : " + safeTitle(title));
        message.setText("""
                Bonjour %s,

                Rappel : votre rendez-vous « %s » approche.

                Date : %s
                %s
                — AgenticForms
                """.formatted(hello, safeTitle(title), startLabel, link));
        sendQuietly(message, "booking-reminder", toEmail);
    }

    public void sendPaymentConfirmationToCustomer(
            String toEmail,
            String customerName,
            String productName,
            String amountLabel,
            String formTitle) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(customerName) ? customerName.trim() : "bonjour";
        String product = StringUtils.hasText(productName) ? productName.trim() : "votre achat";
        String form = StringUtils.hasText(formTitle) ? formTitle.trim() : "le formulaire";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Paiement confirmé : " + product);
        message.setText("""
                Bonjour %s,

                Votre paiement pour « %s » a bien été confirmé.

                %s
                Formulaire : %s

                Merci pour votre confiance.

                — AgenticForms
                """.formatted(hello, product, formatAmountBlock(amountLabel), form));
        sendQuietly(message, "payment-confirmation-customer", toEmail);
    }

    public void sendPaymentNotificationToOwner(
            String toEmail,
            String ownerName,
            String productName,
            String amountLabel,
            String customerEmail,
            String formTitle) {
        if (!StringUtils.hasText(toEmail)) {
            return;
        }
        String hello = StringUtils.hasText(ownerName) ? ownerName.trim() : "bonjour";
        String product = StringUtils.hasText(productName) ? productName.trim() : "Paiement";
        String form = StringUtils.hasText(formTitle) ? formTitle.trim() : "votre formulaire";
        String customer = StringUtils.hasText(customerEmail) ? customerEmail.trim() : "non renseigné";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Paiement reçu : " + product);
        message.setText("""
                Bonjour %s,

                Vous avez reçu un paiement sur « %s ».

                Produit : %s
                %s
                Client : %s

                — AgenticForms
                """.formatted(hello, form, product, formatAmountBlock(amountLabel), customer));
        sendQuietly(message, "payment-notification-owner", toEmail);
    }

    private static String formatAmountBlock(String amountLabel) {
        if (!StringUtils.hasText(amountLabel)) {
            return "Montant : —";
        }
        String trimmed = amountLabel.trim();
        if (trimmed.contains("\n") || trimmed.startsWith("Prix :")) {
            return trimmed;
        }
        return "Montant : " + trimmed;
    }

    private void sendQuietly(SimpleMailMessage message, String kind, String toEmail) {
        try {
            send(message, kind, toEmail);
        } catch (RuntimeException ex) {
            log.warn("E-mail {} non envoyé à {}: {}", kind, toEmail, ex.getMessage());
        }
    }

    private static String safeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : "Rendez-vous";
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
