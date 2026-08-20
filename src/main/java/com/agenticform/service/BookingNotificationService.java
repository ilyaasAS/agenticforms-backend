package com.agenticform.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.GuestInvite;
import com.agenticform.model.entity.SchedulingReminder;
import com.agenticform.repository.SchedulingReminderRepository;

@Service
public class BookingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationService.class);

    private final EmailService emailService;
    private final SchedulingReminderRepository reminderRepository;

    public BookingNotificationService(
            EmailService emailService,
            SchedulingReminderRepository reminderRepository) {
        this.emailService = emailService;
        this.reminderRepository = reminderRepository;
    }

    public void notifyBooking(
            String guestEmail,
            String guestName,
            String title,
            String startLabel,
            String htmlLink,
            Instant eventStart,
            int reminderMinutes,
            String organizerEmail,
            String organizerName,
            String cancelLink,
            List<GuestInvite> invitedGuests,
            String bookedByName,
            String bookedByEmail) {
        emailService.sendBookingConfirmationEmail(guestEmail, guestName, title, startLabel, htmlLink, cancelLink);
        emailService.sendBookingNotificationToOrganizer(
                organizerEmail, organizerName, guestName, guestEmail, title, startLabel, htmlLink);
        if (invitedGuests != null) {
            for (GuestInvite invited : invitedGuests) {
                emailService.sendBookingGuestInviteEmail(
                        invited.email(),
                        invited.name(),
                        title,
                        startLabel,
                        htmlLink,
                        cancelLink,
                        bookedByName,
                        bookedByEmail,
                        guestName,
                        guestEmail);
            }
        }
        if (reminderMinutes <= 0 || eventStart == null) {
            return;
        }
        Instant remindAt = eventStart.minusSeconds(reminderMinutes * 60L);
        if (!eventStart.isAfter(Instant.now())) {
            return;
        }
        if (remindAt.isBefore(Instant.now())) {
            remindAt = Instant.now();
        }
        reminderRepository.save(SchedulingReminder.builder()
                .guestEmail(guestEmail)
                .guestName(guestName)
                .title(title)
                .startLabel(startLabel)
                .htmlLink(htmlLink)
                .remindAt(remindAt)
                .build());
    }

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void sendDueReminders() {
        List<SchedulingReminder> due =
                reminderRepository.findTop50BySentAtIsNullAndRemindAtLessThanEqualOrderByRemindAtAsc(Instant.now());
        for (SchedulingReminder reminder : due) {
            try {
                emailService.sendBookingReminderEmail(
                        reminder.getGuestEmail(),
                        reminder.getGuestName(),
                        reminder.getTitle(),
                        reminder.getStartLabel(),
                        reminder.getHtmlLink());
                reminder.setSentAt(Instant.now());
            } catch (RuntimeException ex) {
                log.warn("Rappel e-mail non envoyé à {}: {}", reminder.getGuestEmail(), ex.getMessage());
            }
        }
    }
}
