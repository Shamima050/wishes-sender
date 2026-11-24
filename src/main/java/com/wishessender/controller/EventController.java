package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import com.wishessender.util.Rules;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/** Events of a connection (birthday, anniversary, festival, custom). */
@RestController
@RequestMapping("/api")
public class EventController {

    private final JdbcTemplate jdbc;

    public EventController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All events of one connection, with next date and days left (from the view). */
    @GetMapping("/connections/{connectionId}/events")
    public List<Map<String, Object>> list(@PathVariable int connectionId, HttpSession session) {
        return jdbc.queryForList(
            "SELECT * FROM v_event_next WHERE connection_id = ? AND user_id = ? ORDER BY days_left",
            connectionId, Auth.userId(session));
    }

    @PostMapping("/connections/{connectionId}/events")
    public Map<String, Object> create(@PathVariable int connectionId, @RequestBody Map<String, Object> b,
                                      HttpSession session) {
        // Make sure the connection belongs to the logged-in user
        Map<String, Object> person = Db.one(jdbc, "SELECT id, name FROM connections WHERE id = ? AND user_id = ?",
                connectionId, Auth.userId(session));

        // The type must exist in the event_types lookup table
        String type = Db.required(b, "event_type").toUpperCase();
        Integer known = jdbc.queryForObject("SELECT COUNT(*) FROM event_types WHERE code = ?", Integer.class, type);
        if (known == 0) {
            List<String> codes = jdbc.queryForList("SELECT code FROM event_types ORDER BY sort_order", String.class);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "event_type must be one of: " + String.join(", ", codes));
        }
        int yearly = Rules.flag(Db.num(b, "yearly", 1), "yearly");

        // Only one birthday per person (it is also their date of birth)
        if (type.equals("BIRTHDAY")) {
            Integer hasBirthday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE connection_id = ? AND event_type = 'BIRTHDAY'", Integer.class, connectionId);
            if (hasBirthday > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, person.get("name") +
                    " already has a birthday. Edit the date of birth on their profile instead.");
            }
            yearly = 1;   // birthdays always repeat
        }

        int id = Db.insert(jdbc,
            "INSERT INTO events (connection_id, title, event_type, event_date, yearly, description) VALUES (?,?,?,?,?,?)",
            connectionId, Db.required(b, "title"), type, Db.required(b, "event_date"), yearly, Db.str(b, "description"));
        return Db.one(jdbc, "SELECT * FROM v_event_next WHERE id = ?", id);
    }

    /** Deleting the BIRTHDAY event also clears the person's date of birth (it is the same data). */
    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable int id, HttpSession session) {
        jdbc.update("DELETE e FROM events e JOIN connections c ON c.id = e.connection_id " +
                    "WHERE e.id = ? AND c.user_id = ?", id, Auth.userId(session));
    }

    /** Events in the next N days, filtered by type (Search & Filter / Calendar list). */
    @GetMapping("/events/upcoming")
    public List<Map<String, Object>> upcoming(@RequestParam(defaultValue = "30") int days,
                                              @RequestParam(defaultValue = "") String type,
                                              HttpSession session) {
        return jdbc.queryForList(
            "SELECT * FROM v_event_next WHERE user_id = ? AND days_left BETWEEN 0 AND ? " +
            "AND (? = '' OR event_type = ?) ORDER BY days_left",
            Auth.userId(session), days, type, type);
    }

    /** Festival master list. With ?religion=Islam it shows festivals for that religion + 'All'. */
    @GetMapping("/festivals")
    public List<Map<String, Object>> festivals(@RequestParam(defaultValue = "") String religion) {
        return jdbc.queryForList(
            "SELECT * FROM festivals WHERE festival_date >= CURDATE() " +
            "AND (? = '' OR religion = 'All' OR religion = ?) ORDER BY festival_date",
            religion, religion);
    }
}
