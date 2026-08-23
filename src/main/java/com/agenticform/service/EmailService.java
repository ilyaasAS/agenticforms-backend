package com.agenticform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String DEFAULT_FROM = "no-reply@agenticforms.com";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String contactInbox;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@agenticforms.com}") String fromAddress,
            @Value("${app.mail.contact-to:contact@agenticforms.com}") String contactInbox) {
        this.mailSender = mailSender;
        this.fromAddress = resolveFrom(fromAddress);
        this.contactInbox = resolveFrom(contactInbox);
        log.info("EmailService initialized with from={} contactTo={}", this.fromAddress, this.contactInbox);
    }

    /**
     * Notification humaine d'un message Contact (persisté en Mongo côté API).
     * Best-effort — l'échec SMTP ne doit pas faire échouer l'envoi du formulaire.
     */
    public void notifyContactInbox(
            String senderName,
            String senderEmail,
            String subject,
            String body) {
        String safeName = senderName != null ? senderName.trim() : "";
        String safeEmail = senderEmail != null ? senderEmail.trim() : "";
        String safeSubject = subject != null ? subject.trim() : "Sans objet";
        String safeBody = body != null ? body.trim() : "";

        String plainText = """
                Bonjour,

                Vous avez reçu un nouveau message via le formulaire de contact AgenticForms.

                De : %s <%s>
                Objet : %s

                Message :
                %s

                Vous pouvez répondre directement à cet e-mail pour contacter %s.

                — L’équipe AgenticForms
                """.formatted(safeName, safeEmail, safeSubject, safeBody, safeName);

        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.55;color:#111827;">
                  <p style="margin:0 0 16px;">Bonjour,</p>
                  <p style="margin:0 0 20px;">Vous avez reçu un nouveau message via le formulaire de contact <strong>AgenticForms</strong>.</p>
                  <table role="presentation" style="border-collapse:collapse;width:100%%;max-width:560px;margin:0 0 20px;">
                    <tr>
                      <td style="padding:8px 0;color:#6b7280;width:90px;vertical-align:top;">De</td>
                      <td style="padding:8px 0;"><strong>%s</strong><br><a href="mailto:%s" style="color:#2563eb;text-decoration:none;">%s</a></td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0;color:#6b7280;vertical-align:top;">Objet</td>
                      <td style="padding:8px 0;">%s</td>
                    </tr>
                  </table>
                  <div style="padding:16px 18px;border:1px solid #e5e7eb;border-radius:12px;background:#f9fafb;white-space:pre-wrap;">%s</div>
                  <p style="margin:20px 0 0;color:#6b7280;font-size:13px;">Répondez directement à cet e-mail pour contacter %s.</p>
                  <p style="margin:24px 0 0;color:#9ca3af;font-size:12px;">— L’équipe AgenticForms</p>
                </div>
                """.formatted(
                escapeHtml(safeName),
                escapeHtml(safeEmail),
                escapeHtml(safeEmail),
                escapeHtml(safeSubject),
                escapeHtml(safeBody),
                escapeHtml(safeName));

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(contactInbox);
            if (StringUtils.hasText(safeEmail)) {
                helper.setReplyTo(safeEmail);
            }
            helper.setSubject("Nouveau message de contact — " + safeSubject);
            helper.setText(plainText, html);
            sendQuietly(mimeMessage, "contact-inbox", contactInbox);
        } catch (MessagingException ex) {
            log.warn("E-mail contact-inbox non préparé pour {}: {}", contactInbox, ex.getMessage());
        }
    }

    /** Réponse admin vers l'auteur du message Contact (visible dans Mailpit en local). */
    public void sendContactReply(
            String toEmail,
            String recipientName,
            String originalSubject,
            String originalBody,
            String replyBody) {
        if (!StringUtils.hasText(toEmail)) {
            throw new IllegalArgumentException("L'e-mail du destinataire est manquant.");
        }
        String hello = StringUtils.hasText(recipientName) ? recipientName.trim() : "bonjour";
        String subject = StringUtils.hasText(originalSubject) ? originalSubject.trim() : "votre message";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail.trim());
        message.setSubject("Re: " + subject);
        message.setText("""
                Bonjour %s,

                %s

                ---
                Votre message :
                %s

                — L’équipe AgenticForms
                """.formatted(hello, replyBody.trim(), originalBody == null ? "" : originalBody.trim()));
        send(message, "contact-reply", toEmail);
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

    private void sendQuietly(MimeMessage message, String kind, String toEmail) {
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

    private void send(MimeMessage message, String kind, String toEmail) {
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

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String resolveFrom(String configured) {
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        log.warn("app.mail.from is blank — falling back to {}", DEFAULT_FROM);
        return DEFAULT_FROM;
    }
}
