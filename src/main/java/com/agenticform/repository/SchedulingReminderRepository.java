package com.agenticform.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agenticform.model.entity.SchedulingReminder;

public interface SchedulingReminderRepository extends JpaRepository<SchedulingReminder, Long> {

    List<SchedulingReminder> findTop50BySentAtIsNullAndRemindAtLessThanEqualOrderByRemindAtAsc(Instant now);
}
