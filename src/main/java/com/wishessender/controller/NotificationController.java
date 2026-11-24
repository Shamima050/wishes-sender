package com.wishessender.controller;

import com.wishessender.service.ReminderScheduler;
import com.wishessender.util.Auth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** In-app notifications created by the reminder scheduler. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /** A notification has no user_id column: its owner is found through event -> connection. */
    private static final String OWNER =
        " JOIN events e ON e.id = n.event_id JOIN connections c ON c.id = e.connection_id ";

    private final JdbcTemplate jdbc;
    private final ReminderScheduler scheduler;

    public NotificationController(JdbcTemplate jdbc, ReminderScheduler scheduler) {
        this.jdbc = jdbc;
        this.scheduler = scheduler;
    }

    /**
     * The reminders list. Cleared reminders ("Mark all read", or a wish was sent for the event)
     * are hidden; ?includeCleared=true shows them too (history).
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "false") boolean includeCleared,
                                          HttpSession session) {
        return jdbc.queryForList(
            "SELECT n.*, c.user_id, e.connection_id, e.event_type FROM notifications n" + OWNER +
            "WHERE c.user_id = ? AND (? OR n.is_cleared = 0) ORDER BY n.created_at DESC, n.id DESC LIMIT 50",
            Auth.userId(session), includeCleared);
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(HttpSession session) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications n" + OWNER + "WHERE c.user_id = ? AND n.is_read = 0",
            Integer.class, Auth.userId(session));
        return Collections.singletonMap("count", count);
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable int id, HttpSession session) {
        jdbc.update("UPDATE notifications n" + OWNER + "SET n.is_read = 1 WHERE n.id = ? AND c.user_id = ?",
                    id, Auth.userId(session));
    }

    /** "Mark all read": marks every reminder read AND removes them from the reminders list. */
    @PostMapping("/read-all")
    public Map<String, Object> markAllRead(HttpSession session) {
        int cleared = jdbc.update("UPDATE notifications n" + OWNER + "SET n.is_read = 1, n.is_cleared = 1 " +
                                  "WHERE c.user_id = ? AND n.is_cleared = 0", Auth.userId(session));
        return Collections.singletonMap("cleared", cleared);
    }

    /** Runs the scheduler right now (handy for the demo instead of waiting 1 minute). */
    @PostMapping("/check-now")
    public Map<String, Object> checkNow(HttpSession session) {
        Auth.userId(session);
        return Collections.singletonMap("created", scheduler.checkReminders());
    }
}
