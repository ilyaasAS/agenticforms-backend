CREATE TABLE scheduling_reminders (
    id_scheduling_reminder BIGINT NOT NULL AUTO_INCREMENT,
    guest_email VARCHAR(255) NOT NULL,
    guest_name VARCHAR(255) NULL,
    title VARCHAR(255) NOT NULL,
    start_label VARCHAR(255) NOT NULL,
    html_link VARCHAR(1024) NULL,
    remind_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_scheduling_reminder),
    INDEX idx_scheduling_reminders_due (sent_at, remind_at)
);
