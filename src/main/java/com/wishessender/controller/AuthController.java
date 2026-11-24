package com.wishessender.controller;

import com.wishessender.util.Auth;
import com.wishessender.util.Db;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/** Register, login, logout and "who am I". */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body, HttpSession session) {
        String name = Db.required(body, "name");
        String email = Db.required(body, "email").toLowerCase();
        String password = Db.required(body, "password");
        if (password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        if (exists > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }
        int id = Db.insert(jdbc, "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                name, email, encoder.encode(password));
        session.setAttribute("userId", id);
        session.setAttribute("role", "USER");
        return me(session);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpSession session) {
        String email = Db.required(body, "email").toLowerCase();
        String password = Db.required(body, "password");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, role, password_hash FROM users WHERE email = ?", email);
        if (rows.isEmpty() || !encoder.matches(password, (String) rows.get(0).get("password_hash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password");
        }
        session.setAttribute("userId", rows.get(0).get("id"));
        session.setAttribute("role", rows.get(0).get("role"));
        return me(session);
    }

    @PostMapping("/logout")
    public void logout(HttpSession session) {
        session.invalidate();
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        return Db.one(jdbc, "SELECT id, name, email, role FROM users WHERE id = ?", Auth.userId(session));
    }
}
