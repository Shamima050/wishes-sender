package com.wishessender.controller;

import com.wishessender.service.GeminiService;
import com.wishessender.service.ReminderScheduler;
import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import com.wishessender.util.Rules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Wish Studio: generate a wish (Gemini or template), save it, and wish history. */
@RestController
@RequestMapping("/api/wishes")
public class WishController {

    private final JdbcTemplate jdbc;
    private final GeminiService gemini;
    private final ReminderScheduler reminders;

    public WishController(JdbcTemplate jdbc, GeminiService gemini, ReminderScheduler reminders) {
        this.jdbc = jdbc;
        this.gemini = gemini;
        this.reminders = reminders;
    }

    /** Body: { connectionId, eventId (optional), tone, language } */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> b, HttpSession session) {
        int userId = Auth.userId(session);
        Map<String, Object> person = Db.one(jdbc,
                "SELECT * FROM connections WHERE id = ? AND user_id = ?", Db.num(b, "connectionId", 0), userId);
        Map<String, Object> user = Db.one(jdbc, "SELECT * FROM users WHERE id = ?", userId);

        String tone = b.getOrDefault("tone", "Emotional").toString();
        String language = b.getOrDefault("language", "English").toString();
        String eventType = "CUSTOM";
        String eventTitle = "a special day";
        if (Db.str(b, "eventId") != null) {
            Map<String, Object> event = Db.one(jdbc, "SELECT * FROM events WHERE id = ? AND connection_id = ?",
                    Db.num(b, "eventId", 0), person.get("id"));
            eventType = event.get("event_type").toString();
            eventTitle = event.get("title").toString();
        }

        String text = null;
        String source = "TEMPLATE";

        // 1) Try Gemini, only if the user allows AI in Settings
        if (Boolean.TRUE.equals(user.get("ai_enabled"))) {
            String prompt = buildPrompt(person, user, eventTitle, tone, language);
            text = gemini.generate(prompt);
            if (text != null) source = "AI";
        }

        // 2) Fallback: a random template from the database
        if (text == null) {
            List<String> templates = jdbc.queryForList(
                "SELECT text FROM wish_templates WHERE event_type = ? ORDER BY (tone = ?) DESC, RAND() LIMIT 1",
                String.class, eventType, tone);
            text = templates.isEmpty() ? "Dear {name}, wishing you a wonderful day!" : templates.get(0);
            text = text.replace("{name}", person.get("name").toString());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("source", source);
        result.put("aiConfigured", gemini.isConfigured());
        return result;
    }

    private String buildPrompt(Map<String, Object> person, Map<String, Object> user,
                               String eventTitle, String tone, String language) {
        StringBuilder p = new StringBuilder();
        p.append("Write a short, warm ").append(tone).append(" wish in ").append(language)
         .append(" for my ").append(person.get("relation")).append(" named ").append(person.get("name"))
         .append(" for their ").append(eventTitle).append(". ");

        // Privacy: personal details are only sent if the user allowed it
        if (Boolean.TRUE.equals(user.get("share_details"))) {
            if (person.get("interests") != null) p.append("They like: ").append(person.get("interests")).append(". ");
            if (person.get("favourite_dish") != null) p.append("Favourite dish: ").append(person.get("favourite_dish")).append(". ");
        }

        // Avoid repeating the last wish sent to this person
        List<String> last = jdbc.queryForList(
            "SELECT text FROM wishes WHERE connection_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
            String.class, person.get("id"));
        if (!last.isEmpty()) p.append("Do not repeat this earlier wish: \"").append(last.get(0)).append("\". ");

        p.append("Maximum 60 words. No hashtags. Reply with only the wish text.");
        return p.toString();
    }

    /** Save a wish to history. Body: { connectionId, eventId, tone, language, text, source, status } */
    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> b, HttpSession session) {
        int userId = Auth.userId(session);
        int connectionId = Db.num(b, "connectionId", 0);
        Db.one(jdbc, "SELECT id FROM connections WHERE id = ? AND user_id = ?", connectionId, userId);

        // The event (optional) must belong to the same person, otherwise 404
        Integer eventId = Db.str(b, "eventId") == null ? null : Db.num(b, "eventId", 0);
        if (eventId != null) {
            Db.one(jdbc, "SELECT id FROM events WHERE id = ? AND connection_id = ?", eventId, connectionId);
        }

        // No user_id column: the owner of a wish is the owner of its connection
        String status = Rules.oneOf(orDefault(b, "status", "SAVED"), Rules.WISH_STATUSES, "status");
        int id = Db.insert(jdbc,
            "INSERT INTO wishes (connection_id, event_id, tone, language, text, source, status) VALUES (?,?,?,?,?,?,?)",
            connectionId, eventId,
            Rules.oneOf(Db.str(b, "tone"), Rules.TONES, "tone"),
            Rules.oneOf(Db.str(b, "language"), Rules.LANGUAGES, "language"),
            Db.required(b, "text"),
            Rules.oneOf(orDefault(b, "source", "TEMPLATE"), Rules.WISH_SOURCES, "source"),
            status);

        // A wish sent for an event clears that event's reminders from the reminders list
        int cleared = (eventId != null && status.equals("SENT")) ? reminders.clearForEvent(eventId, userId) : 0;

        Map<String, Object> wish = new HashMap<>(Db.one(jdbc,
            "SELECT w.*, c.user_id FROM wishes w JOIN connections c ON c.id = w.connection_id WHERE w.id = ?", id));
        wish.put("remindersCleared", cleared);
        return wish;
    }

    /** Wish history. Optional ?connectionId= to show one person's wishes. */
    @GetMapping
    public List<Map<String, Object>> history(@RequestParam(defaultValue = "0") int connectionId, HttpSession session) {
        return jdbc.queryForList(
            "SELECT w.*, c.user_id, c.name AS connection_name, e.title AS event_title " +
            "FROM wishes w JOIN connections c ON c.id = w.connection_id " +
            "LEFT JOIN events e ON e.id = w.event_id " +
            "WHERE c.user_id = ? AND (? = 0 OR w.connection_id = ?) ORDER BY w.created_at DESC, w.id DESC",
            Auth.userId(session), connectionId, connectionId);
    }

    /** Mark a saved wish as sent (also clears the reminders of its event, if it has one). */
    @PostMapping("/{id}/sent")
    public void markSent(@PathVariable int id, HttpSession session) {
        int userId = Auth.userId(session);
        int updated = jdbc.update("UPDATE wishes w JOIN connections c ON c.id = w.connection_id SET w.status = 'SENT' " +
                                  "WHERE w.id = ? AND c.user_id = ?", id, userId);
        if (updated > 0) {
            List<Integer> eventIds = jdbc.queryForList(
                "SELECT event_id FROM wishes WHERE id = ? AND event_id IS NOT NULL", Integer.class, id);
            if (!eventIds.isEmpty()) reminders.clearForEvent(eventIds.get(0), userId);
        }
    }

    /** Text value from the body, or the default when it is missing/empty. */
    private static String orDefault(Map<String, Object> b, String key, String defaultValue) {
        String value = Db.str(b, key);
        return value == null ? defaultValue : value;
    }
}
