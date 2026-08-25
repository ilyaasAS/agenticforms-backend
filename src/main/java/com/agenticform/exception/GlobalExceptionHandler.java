package com.agenticform.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String GENERIC_AUTH_FAILURE = "E-mail ou mot de passe invalide.";

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        // Filet de sécurité : ne plus confirmer l'existence d'un e-mail (anti-énumération).
        return ResponseEntity.accepted().body(neutralRegisterBody());
    }

    @ExceptionHandler(InvalidOAuthCodeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOAuthCode(InvalidOAuthCodeException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Session OAuth invalide ou expirée.");
    }

    @ExceptionHandler(OAuthEmailNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleOAuthEmailNotVerified(OAuthEmailNotVerifiedException ex) {
        return error(HttpStatus.UNAUTHORIZED, "L’adresse e-mail n’est pas vérifiée auprès du fournisseur.");
    }

    @ExceptionHandler(OAuthIdentityConflictException.class)
    public ResponseEntity<Map<String, Object>> handleOAuthIdentityConflict(OAuthIdentityConflictException ex) {
        return error(HttpStatus.CONFLICT, "Ce compte social est déjà lié à une autre identité.");
    }

    @ExceptionHandler(LastAuthMethodException.class)
    public ResponseEntity<Map<String, Object>> handleLastAuthMethod(LastAuthMethodException ex) {
        return error(HttpStatus.CONFLICT, "Impossible de dissocier votre unique moyen de connexion.");
    }

    @ExceptionHandler(OAuthLinkNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOAuthLinkNotFound(OAuthLinkNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Aucun compte social associé pour ce fournisseur.");
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPasswordResetToken(
            InvalidPasswordResetTokenException ex) {
        return error(HttpStatus.BAD_REQUEST, "Lien de réinitialisation invalide ou expiré.");
    }

    @ExceptionHandler(SamePasswordException.class)
    public ResponseEntity<Map<String, Object>> handleSamePassword(SamePasswordException ex) {
        return error(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe doit être différent de l'ancien.");
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCurrentPassword(InvalidCurrentPasswordException ex) {
        return error(HttpStatus.BAD_REQUEST, "Mot de passe actuel incorrect.");
    }

    @ExceptionHandler(AccountDeleteConfirmationException.class)
    public ResponseEntity<Map<String, Object>> handleAccountDeleteConfirmation(
            AccountDeleteConfirmationException ex) {
        return error(HttpStatus.BAD_REQUEST, "L'e-mail de confirmation ne correspond pas à votre compte.");
    }

    @ExceptionHandler({EmailNotVerifiedException.class, PasswordLoginDisabledException.class})
    public ResponseEntity<Map<String, Object>> handleLoginStateEnumeration(RuntimeException ex) {
        // Anti-énumération : même 401 générique que mauvais mot de passe / e-mail inconnu.
        return error(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_FAILURE);
    }

    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidEmailVerificationToken(
            InvalidEmailVerificationTokenException ex) {
        return error(HttpStatus.BAD_REQUEST, "Lien de vérification invalide ou expiré.");
    }

    @ExceptionHandler(OAuthLinkRequiresVerifiedEmailException.class)
    public ResponseEntity<Map<String, Object>> handleOAuthLinkRequiresVerified(
            OAuthLinkRequiresVerifiedEmailException ex) {
        return error(HttpStatus.CONFLICT,
                "Vérifiez d’abord votre e-mail avant de lier un compte social.");
    }

    @ExceptionHandler(AdminCommandForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleAdminCommandForbidden(AdminCommandForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AdminEmailTakenException.class)
    public ResponseEntity<Map<String, Object>> handleAdminEmailTaken(AdminEmailTakenException ex) {
        return error(HttpStatus.CONFLICT, "Cet e-mail est déjà utilisé.");
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceNotFound(WorkspaceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Espace de travail introuvable.");
    }

    @ExceptionHandler(WorkspaceAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceAccessDenied(WorkspaceAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Accès refusé à cet espace de travail.");
    }

    @ExceptionHandler(WorkspaceSlugConflictException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceSlugConflict(WorkspaceSlugConflictException ex) {
        return error(HttpStatus.CONFLICT, "Ce slug d’espace de travail est déjà utilisé.");
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceMemberNotFound(WorkspaceMemberNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Membre introuvable dans cet espace de travail.");
    }

    @ExceptionHandler(WorkspaceMemberAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceMemberAlreadyExists(
            WorkspaceMemberAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "Cet utilisateur est déjà membre de cet espace de travail.");
    }

    @ExceptionHandler(WorkspaceLastOwnerException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceLastOwner(WorkspaceLastOwnerException ex) {
        return error(HttpStatus.CONFLICT,
                "Impossible de retirer ou rétrograder le dernier propriétaire de l’espace.");
    }

    @ExceptionHandler(WorkspaceInvalidRoleChangeException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceInvalidRoleChange(
            WorkspaceInvalidRoleChangeException ex) {
        return error(HttpStatus.FORBIDDEN,
                "Modification de rôle non autorisée. Utilisez le transfert de propriété si nécessaire.");
    }

    @ExceptionHandler(UserNotFoundByEmailException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFoundByEmail(UserNotFoundByEmailException ex) {
        return error(HttpStatus.NOT_FOUND, "Aucun utilisateur trouvé avec cet e-mail.");
    }

    @ExceptionHandler(UserAccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserAccountNotFound(UserAccountNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Utilisateur introuvable.");
    }

    @ExceptionHandler(FormNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFormNotFound(FormNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Formulaire introuvable.");
    }

    @ExceptionHandler(ContactMessageNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleContactMessageNotFound(ContactMessageNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Message de contact introuvable.");
    }

    @ExceptionHandler(FormNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleFormNotAvailable(FormNotAvailableException ex) {
        return error(HttpStatus.NOT_FOUND, "Ce formulaire n’est pas publié ou n’est plus disponible.");
    }

    @ExceptionHandler(InvalidSubmissionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSubmission(InvalidSubmissionException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CalendlyNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleCalendlyNotConfigured(
            CalendlyNotConfiguredException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(CalendlyIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleCalendlyIntegration(CalendlyIntegrationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(GoogleCalendarNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleGoogleCalendarNotConfigured(
            GoogleCalendarNotConfiguredException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Google Calendar n’est pas configuré.");
    }

    @ExceptionHandler(GoogleCalendarIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleGoogleCalendarIntegration(
            GoogleCalendarIntegrationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(StripeNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleStripeNotConfigured(
            StripeNotConfiguredException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(StripeIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleStripeIntegration(StripeIntegrationException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(FormFieldNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleFormFieldNotFound(FormFieldNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Champ de formulaire introuvable.");
    }

    @ExceptionHandler(InvalidFormFieldException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFormField(InvalidFormFieldException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(Exception ex) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Image trop volumineuse (max 5 Mo). Compressez-la ou choisissez une autre image."
        );
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex
    ) {
        String detail = Optional.ofNullable(ex.getMostSpecificCause())
                .map(Throwable::getMessage)
                .orElse("")
                .toLowerCase();
        if (detail.contains("data too long") || detail.contains("data truncation")) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "Données trop volumineuses pour être enregistrées. Réimportez l’image via Importations (upload serveur)."
            );
        }
        log.error("Data integrity violation", ex);
        return error(HttpStatus.CONFLICT, "Conflit avec l’état actuel des données.");
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleAuthFailure(RuntimeException ex) {
        return error(HttpStatus.UNAUTHORIZED, GENERIC_AUTH_FAILURE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), safeValidationMessage(fieldError));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Les données saisies sont invalides.");
        body.put("message", "Les données saisies sont invalides.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Ressource introuvable.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur serveur est survenue. Réessayez plus tard.");
    }

    private static Map<String, Object> neutralRegisterBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.ACCEPTED.value());
        String message = "Si cet e-mail est disponible, un message de confirmation vous a été envoyé.";
        body.put("error", message);
        body.put("message", message);
        return body;
    }

    private static String safeValidationMessage(FieldError fieldError) {
        String field = fieldError.getField();
        if ("password".equals(field) || "newPassword".equals(field)) {
            return "Le mot de passe doit contenir au moins 16 caractères, une majuscule et un chiffre ou caractère spécial.";
        }
        if ("email".equals(field) || "guestEmail".equals(field) || field.endsWith(".guestEmail")) {
            return "Adresse e-mail invalide.";
        }
        if ("code".equals(field)) {
            return "Code OAuth invalide.";
        }
        if ("token".equals(field)) {
            return "Jeton invalide.";
        }
        return "Valeur invalide.";
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", message);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
