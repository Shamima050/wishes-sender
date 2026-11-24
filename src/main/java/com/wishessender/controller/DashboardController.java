package com.wishessender.controller;

import com.wishessender.util.Auth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/** Home dashboard data and the Insights reports (from SQL views). */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpSession session) {
        int userId = Auth.userId(session);
        Map<String, Object> data = new HashMap<>();
        data.put("today", jdbc.queryForList(
            "SELECT * FROM v_event_next WHERE user_id = ? AND days_left = 0", userId));
        data.put("upcoming", jdbc.queryForList(
            "SELECT * FROM v_event_next WHERE user_id = ? AND days_left BETWEEN 1 AND 30 ORDER BY days_left", userId));
        data.put("totalConnections", jdbc.queryForObject(
            "SELECT COUNT(*) FROM connections WHERE user_id = ?", Integer.class, userId));
        data.put("totalEvents", jdbc.queryForObject(
            "SELECT COUNT(*) FROM v_event_next WHERE user_id = ?", Integer.class, userId));
        data.put("wishesSent", jdbc.queryForObject(
            "SELECT COUNT(*) FROM wishes w JOIN connections c ON c.id = w.connection_id " +
            "WHERE c.user_id = ? AND w.status = 'SENT'", Integer.class, userId));
        data.put("recentWishes", jdbc.queryForList(
            "SELECT w.*, c.user_id, c.name AS connection_name FROM wishes w JOIN connections c ON c.id = w.connection_id " +
            "WHERE c.user_id = ? ORDER BY w.created_at DESC, w.id DESC LIMIT 5", userId));
        return data;
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(HttpSession session) {
        int userId = Auth.userId(session);
        Map<String, Object> data = new HashMap<>();
        data.put("byRelation", jdbc.queryForList(
            "SELECT relation, total FROM v_connections_by_relation WHERE user_id = ? ORDER BY total DESC", userId));
        data.put("perMonth", jdbc.queryForList(
            "SELECT month_no, total FROM v_events_per_month WHERE user_id = ? ORDER BY month_no", userId));
        data.put("wishesByType", jdbc.queryForList(
            "SELECT event_type, total FROM v_wishes_by_event_type WHERE user_id = ? ORDER BY total DESC", userId));
        return data;
    }
}
