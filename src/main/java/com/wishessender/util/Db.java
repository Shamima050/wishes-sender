package com.wishessender.util;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/** Small helpers so the controllers stay short. */
public class Db {

    /** Runs an INSERT and returns the new AUTO_INCREMENT id. */
    public static int insert(JdbcTemplate jdbc, String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    /** Returns the first row, or sends 404 if there is none. */
    public static Map<String, Object> one(JdbcTemplate jdbc, String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        return rows.get(0);
    }

    /** Reads a text value from a JSON body. Empty text becomes null. */
    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            return null;
        }
        return value.toString().trim();
    }

    /** Reads a number from a JSON body, or returns the default. */
    public static int num(Map<String, Object> body, String key, int defaultValue) {
        String value = str(body, key);
        if (value == null) return defaultValue;
        if (value.equals("true")) return 1;
        if (value.equals("false")) return 0;
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a number");
        }
    }

    /** Reads a required text value, or sends 400. */
    public static String required(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return value;
    }
}
