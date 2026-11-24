package com.wishessender.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background job: every minute it looks for events that are today, or
 * "remind_days_before" days away, and creates in-app notifications.
 * The UNIQUE key on notifications + INSERT IGNORE makes sure we never remind twice.
 */
@Service
public class ReminderScheduler {

    private final JdbcTemplate jdbc;

    public ReminderScheduler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 5000, fixedRate = 60000)   // 5s after start, then every 60s
    public int checkReminders() {
        String sql =
            // no user_id: the owner of a notification is found through event -> connection
            "INSERT IGNORE INTO notifications (event_id, for_date, days_before, message) " +
            "SELECT v.id,v.next_date, v.days_left, " +
            "       CASE WHEN v.days_left = 0 " +
            "            THEN CONCAT('Today: ', v.title, ' of ', v.connection_name, '!') " +
            "            ELSE CONCAT('In ', v.days_left, ' day(s): ', v.title, ' of ', v.connection_name) END " +
            "FROM v_event_next v " +
            "JOIN users u ON u.id = v.user_id " +
            "WHERE v.days_left = 0 OR v.days_left = u.remind_days_before";
        int created = jdbc.update(sql);
        if (created > 0) {
            System.out.println("Reminder scheduler created " + created + " notification(s)");
        }
        return created;
    }

    /**
     * A wish was sent for this event: its reminders are done, so they are marked read and
     * removed from the user's reminders list. (Rows are kept, so the scheduler will not
     * create them again; next year's occurrence has a new for_date and is reminded as usual.)
     * Returns how many reminders were cleared.
     */
    public int clearForEvent(int eventId, int userId) {
        return jdbc.update(
            "UPDATE notifications n JOIN events e ON e.id = n.event_id JOIN connections c ON c.id = e.connection_id " +
            "SET n.is_read = 1, n.is_cleared = 1 WHERE n.event_id = ? AND c.user_id = ? AND n.is_cleared = 0",
            eventId, userId);
    }
}
