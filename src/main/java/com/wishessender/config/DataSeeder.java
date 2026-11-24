package com.wishessender.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs once at startup (after schema.sql and data.sql).
 * Creates a default admin account if it does not exist yet.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public DataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'admin@wishes.com'", Integer.class);
        if (count == 0) {
            String hash = new BCryptPasswordEncoder().encode("admin123");
            jdbc.update("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'ADMIN')",
                    "Admin", "admin@wishes.com", hash);
            System.out.println(">>> Default admin created: admin@wishes.com / admin123");
        }
    }
}
