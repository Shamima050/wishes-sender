package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import com.wishessender.util.Rules;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Add, list/search, view, update and delete connections (people). */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    /**
     * The date of birth is not a column of connections any more: it is the BIRTHDAY event.
     * This SELECT adds it back as "dob", so the API looks the same as in v1.0.
     */
    static final String SELECT_CONNECTION =
        "SELECT c.*, b.event_date AS dob FROM connections c " +
        "LEFT JOIN events b ON b.connection_id = c.id AND b.event_type = 'BIRTHDAY' ";

    /** Days to the person's next event (used in the lists). */
    static final String NEXT_IN_DAYS =
        "(SELECT MIN(v.days_left) FROM v_event_next v WHERE v.connection_id = c.id AND v.days_left >= 0) AS next_in_days";

    /**
     * Search / filter conditions shared with the admin list. Empty value = no filter.
     * religion / gender = "NONE" finds people where it is not set.
     */
    static final String FILTERS =
        " AND c.name LIKE ? AND (? = '' OR c.relation = ?)" +
        " AND (? = '' OR (? = 'NONE' AND c.religion IS NULL) OR c.religion = ?)" +
        " AND (? = '' OR (? = 'NONE' AND c.gender IS NULL) OR c.gender = ?) ";

    static Object[] filterArgs(String q, String relation, String religion, String gender) {
        return new Object[]{"%" + q.trim() + "%", relation, relation, religion, religion, religion, gender, gender, gender};
    }

    private final JdbcTemplate jdbc;

    public ConnectionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The user's connections, with optional search text (q) and relation / religion / gender filters.
     * Also returns days to the next event.
     */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "") String q,
                                          @RequestParam(defaultValue = "") String relation,
                                          @RequestParam(defaultValue = "") String religion,
                                          @RequestParam(defaultValue = "") String gender,
                                          HttpSession session) {
        int userId = Auth.userId(session);
        String sql =
            "SELECT c.*, b.event_date AS dob, " + NEXT_IN_DAYS + " FROM connections c " +
            "LEFT JOIN events b ON b.connection_id = c.id AND b.event_type = 'BIRTHDAY' " +
            "WHERE c.user_id = ?" + FILTERS + "ORDER BY c.name";
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(Arrays.asList(filterArgs(q, relation, religion, gender)));
        return jdbc.queryForList(sql, args.toArray());
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable int id, HttpSession session) {
        return Db.one(jdbc, SELECT_CONNECTION + "WHERE c.id = ? AND c.user_id = ?", id, Auth.userId(session));
    }

    @PostMapping
    @Transactional   // the connection and its birthday event are saved together, or not at all
    public Map<String, Object> create(@RequestBody Map<String, Object> b, HttpSession session) {
        int userId = Auth.userId(session);
        String name = Db.required(b, "name");
        checkNameIsFree(userId, name, 0);
        int[] budget = budget(b);
        int id = Db.insert(jdbc,
            "INSERT INTO connections (user_id, name, relation, phone, email, address, gender, religion, " +
            "favourite_dish, interests, gift_min, gift_max, notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            userId, name, Rules.oneOf(Db.required(b, "relation"), Rules.RELATIONS, "relation"),
            Db.str(b, "phone"), Db.str(b, "email"), Db.str(b, "address"),
            Rules.oneOf(Db.str(b, "gender"), Rules.GENDERS, "gender"),
            Rules.oneOf(Db.str(b, "religion"), Rules.RELIGIONS, "religion"),
            Db.str(b, "favourite_dish"), Db.str(b, "interests"), budget[0], budget[1], Db.str(b, "notes"));

        // If a date of birth is given, create the birthday event automatically
        saveBirthday(id, Db.str(b, "dob"));
        return get(id, session);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable int id, @RequestBody Map<String, Object> b, HttpSession session) {
        int userId = Auth.userId(session);
        get(id, session); // 404 if it is not this user's connection
        String name = Db.required(b, "name");
        checkNameIsFree(userId, name, id);
        int[] budget = budget(b);
        jdbc.update(
            "UPDATE connections SET name=?, relation=?, phone=?, email=?, address=?, gender=?, religion=?, " +
            "favourite_dish=?, interests=?, gift_min=?, gift_max=?, notes=? WHERE id=?",
            name, Rules.oneOf(Db.required(b, "relation"), Rules.RELATIONS, "relation"),
            Db.str(b, "phone"), Db.str(b, "email"), Db.str(b, "address"),
            Rules.oneOf(Db.str(b, "gender"), Rules.GENDERS, "gender"),
            Rules.oneOf(Db.str(b, "religion"), Rules.RELIGIONS, "religion"),
            Db.str(b, "favourite_dish"), Db.str(b, "interests"), budget[0], budget[1], Db.str(b, "notes"), id);

        // Editing the date of birth now also moves the birthday event (they can never disagree)
        saveBirthday(id, Db.str(b, "dob"));
        return get(id, session);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id, HttpSession session) {
        // Events, wishes and notifications are removed too (ON DELETE CASCADE)
        jdbc.update("DELETE FROM connections WHERE id = ? AND user_id = ?", id, Auth.userId(session));
    }

    /**
     * The BIRTHDAY event is the only place the date of birth is stored.
     * dob given -> create or move the birthday; dob empty -> remove the birthday.
     */
    private void saveBirthday(int connectionId, String dob) {
        if (dob == null) {
            jdbc.update("DELETE FROM events WHERE connection_id = ? AND event_type = 'BIRTHDAY'", connectionId);
            return;
        }
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM events WHERE connection_id = ? AND event_type = 'BIRTHDAY'", Integer.class, connectionId);
        if (exists > 0) {
            jdbc.update("UPDATE events SET event_date = ?, yearly = 1 WHERE connection_id = ? AND event_type = 'BIRTHDAY'",
                        dob, connectionId);
        } else {
            jdbc.update("INSERT INTO events (connection_id, title, event_type, event_date, yearly) " +
                        "VALUES (?, 'Birthday', 'BIRTHDAY', ?, 1)", connectionId, dob);
        }
    }

    /** One user cannot have two connections with the same name (uq_conn_user_name). */
    private void checkNameIsFree(int userId, String name, int exceptId) {
        Integer taken = jdbc.queryForObject(
            "SELECT COUNT(*) FROM connections WHERE user_id = ? AND name = ? AND id <> ?",
            Integer.class, userId, name, exceptId);
        if (taken > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "You already have a connection named " + name + ". Add something to tell them apart, e.g. " +
                name + " (Office)");
        }
    }

    /** Reads gift_min / gift_max and checks 0 <= min <= max. Returns {min, max}. */
    private int[] budget(Map<String, Object> b) {
        int min = Db.num(b, "gift_min", 0);
        int max = Db.num(b, "gift_max", 5000);
        if (min < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gift budget min cannot be negative");
        }
        if (max < min) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Gift budget max must be greater than or equal to min");
        }
        return new int[]{min, max};
    }
}
