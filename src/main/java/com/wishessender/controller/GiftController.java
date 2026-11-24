package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/** Gift suggestions matched by event type, price range and interests. */
@RestController
@RequestMapping("/api/gifts")
public class GiftController {

    /** event_type NULL means "any event"; the API keeps showing it as "ANY" like in v1.0. */
    public static final String GIFT_COLUMNS =
        "g.id, g.name, g.category, g.price, COALESCE(g.event_type, 'ANY') AS event_type, g.tag, g.reason";

    private final JdbcTemplate jdbc;

    public GiftController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GET /api/gifts/suggest?connectionId=1&eventType=BIRTHDAY
     * Gifts in the person's budget, for this event type (or ANY),
     * with gifts that match their interests shown first.
     */
    @GetMapping("/suggest")
    public List<Map<String, Object>> suggest(@RequestParam int connectionId,
                                             @RequestParam(defaultValue = "ANY") String eventType,
                                             HttpSession session) {
        Map<String, Object> person = Db.one(jdbc,
                "SELECT * FROM connections WHERE id = ? AND user_id = ?", connectionId, Auth.userId(session));
        String interests = person.get("interests") == null ? "" : person.get("interests").toString().toLowerCase();

        return jdbc.queryForList(
            "SELECT " + GIFT_COLUMNS + ", (? LIKE CONCAT('%', g.tag, '%')) AS matches_interest " +
            "FROM gifts g " +
            "WHERE g.price BETWEEN ? AND ? AND (g.event_type IS NULL OR g.event_type = ?) " +
            "ORDER BY matches_interest DESC, g.price " +
            "LIMIT 6",
            interests, person.get("gift_min"), person.get("gift_max"), eventType);
    }
}
