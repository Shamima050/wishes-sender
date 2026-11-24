package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import com.wishessender.util.Rules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Map;

/** Profile, privacy (AI usage) and reminder preferences. */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final JdbcTemplate jdbc;

    public SettingsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> get(HttpSession session) {
        return Db.one(jdbc,
            "SELECT id, name, email, role, ai_enabled, share_details, remind_days_before FROM users WHERE id = ?",
            Auth.userId(session));
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> b, HttpSession session) {
        jdbc.update("UPDATE users SET name = ?, ai_enabled = ?, share_details = ?, remind_days_before = ? WHERE id = ?",
            Db.required(b, "name"),
            Rules.flag(Db.num(b, "ai_enabled", 1), "ai_enabled"),
            Rules.flag(Db.num(b, "share_details", 1), "share_details"),
            Rules.range(Db.num(b, "remind_days_before", 1), 0, Rules.MAX_REMIND_DAYS, "remind_days_before"),
            Auth.userId(session));
        return get(session);
    }
}
