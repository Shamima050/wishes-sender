package com.wishessender.config;

import com.wishessender.util.Rules;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Upgrades a database created by v1.0 / v1.1 to the current design
 * (see docs/CHANGE_REQUEST_CR-001.md and CHANGELOG.md).
 *
 * schema.sql only uses CREATE TABLE IF NOT EXISTS, so it never changes a table that already
 * exists. This class runs right after schema.sql + data.sql (and before the web server and the
 * reminder scheduler start). Every step first checks information_schema, so:
 *   - on a NEW database it does nothing (schema.sql already created everything), and
 *   - on an OLD database it upgrades once; the next start does nothing.
 */
@Component
@DependsOnDatabaseInitialization
public class SchemaMigrator implements InitializingBean {

    private final JdbcTemplate jdbc;

    public SchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterPropertiesSet() {
        widenPasswordHash();       // change 5a
        dropRedundantUserIds();    // change 1
        linkEventTypes();          // change 3
        keepBirthdayOnlyInEvents();// change 6
        makeConnectionsUnique();   // change 5b
        addNotificationCleared();  // v1.2: clear reminders
        addCheckConstraints();     // changes 2 and 4
    }

    // ---------------------------------------------------------------- v1.2
    private void addNotificationCleared() {
        if (!columnExists("notifications", "is_cleared")) {
            jdbc.execute("ALTER TABLE notifications ADD COLUMN is_cleared TINYINT(1) NOT NULL DEFAULT 0 AFTER is_read");
            log("notifications.is_cleared added");
        }
    }

    // ---------------------------------------------------------------- change 5a
    private void widenPasswordHash() {
        Integer length = jdbc.queryForObject(
            "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'password_hash'", Integer.class);
        if (length != null && length < 255) {
            jdbc.execute("ALTER TABLE users MODIFY password_hash VARCHAR(255) NOT NULL");
            log("users.password_hash widened to VARCHAR(255)");
        }
    }

    // ---------------------------------------------------------------- change 1
    private void dropRedundantUserIds() {
        for (String table : new String[]{"wishes", "notifications"}) {
            if (!columnExists(table, "user_id")) continue;
            List<String> foreignKeys = jdbc.queryForList(
                "SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = 'user_id' " +
                "AND REFERENCED_TABLE_NAME IS NOT NULL", String.class, table);
            for (String fk : foreignKeys) {
                jdbc.execute("ALTER TABLE " + table + " DROP FOREIGN KEY " + fk);
            }
            jdbc.execute("ALTER TABLE " + table + " DROP COLUMN user_id");
            log(table + ".user_id dropped (owner now comes from connections)");
        }
    }

    // ---------------------------------------------------------------- change 3
    private void linkEventTypes() {
        String codes = "SELECT code FROM event_types";

        if (!constraintExists("events", "fk_events_type")) {
            jdbc.update("UPDATE events SET event_type = UPPER(TRIM(event_type))");
            report(jdbc.update("UPDATE events SET event_type = 'CUSTOM' WHERE event_type NOT IN (" + codes + ")"),
                   "events with an unknown type set to CUSTOM");
            jdbc.execute("ALTER TABLE events ADD CONSTRAINT fk_events_type FOREIGN KEY (event_type) REFERENCES event_types(code)");
            log("events.event_type now references event_types");
        }

        if (!constraintExists("wish_templates", "fk_tpl_type")) {
            jdbc.update("UPDATE wish_templates SET event_type = UPPER(TRIM(event_type))");
            report(jdbc.update("UPDATE wish_templates SET event_type = 'CUSTOM' WHERE event_type NOT IN (" + codes + ")"),
                   "wish templates with an unknown type set to CUSTOM");
            jdbc.execute("ALTER TABLE wish_templates ADD CONSTRAINT fk_tpl_type FOREIGN KEY (event_type) REFERENCES event_types(code)");
            log("wish_templates.event_type now references event_types");
        }

        if (!constraintExists("gifts", "fk_gifts_type")) {
            // 'ANY' is not an event type: it becomes NULL (= suits any event)
            jdbc.execute("ALTER TABLE gifts MODIFY event_type VARCHAR(20) NULL");
            jdbc.update("UPDATE gifts SET event_type = UPPER(TRIM(event_type))");
            jdbc.update("UPDATE gifts SET event_type = NULL WHERE event_type NOT IN (" + codes + ")");
            jdbc.execute("ALTER TABLE gifts ADD CONSTRAINT fk_gifts_type FOREIGN KEY (event_type) REFERENCES event_types(code)");
            log("gifts.event_type now references event_types ('ANY' stored as NULL)");
        }
    }

    // ---------------------------------------------------------------- change 6
    private void keepBirthdayOnlyInEvents() {
        // The oldest BIRTHDAY event of every connection is the one we keep
        String keep = "(SELECT connection_id, MIN(id) AS keep_id FROM events " +
                      " WHERE event_type = 'BIRTHDAY' GROUP BY connection_id) k";

        if (columnExists("connections", "dob")) {
            // dob is what the user last edited on the profile, so it wins over the event
            report(jdbc.update(
                "UPDATE events e JOIN " + keep + " ON k.keep_id = e.id " +
                "JOIN connections c ON c.id = e.connection_id " +
                "SET e.event_date = c.dob WHERE c.dob IS NOT NULL AND e.event_date <> c.dob"),
                "birthday events updated from connections.dob");
            report(jdbc.update(
                "INSERT INTO events (connection_id, title, event_type, event_date, yearly) " +
                "SELECT c.id, 'Birthday', 'BIRTHDAY', c.dob, 1 FROM connections c " +
                "WHERE c.dob IS NOT NULL AND NOT EXISTS " +
                "  (SELECT 1 FROM events e WHERE e.connection_id = c.id AND e.event_type = 'BIRTHDAY')"),
                "missing birthday events created from connections.dob");
        }

        if (!columnExists("events", "birthday_of")) {
            // Extra birthdays of the same person are kept, but as CUSTOM events
            report(jdbc.update(
                "UPDATE events e JOIN " + keep + " ON k.connection_id = e.connection_id " +
                "SET e.event_type = 'CUSTOM' WHERE e.event_type = 'BIRTHDAY' AND e.id <> k.keep_id"),
                "duplicate birthday events changed to CUSTOM");
            jdbc.update("UPDATE events SET yearly = 1 WHERE event_type = 'BIRTHDAY'");
            jdbc.execute("ALTER TABLE events " +
                "ADD COLUMN birthday_of INT AS (IF(event_type = 'BIRTHDAY', connection_id, NULL)) VIRTUAL, " +
                "ADD UNIQUE KEY uq_one_birthday (birthday_of)");
            log("events: only one birthday per connection (uq_one_birthday)");
        }

        if (columnExists("connections", "dob")) {
            jdbc.execute("ALTER TABLE connections DROP COLUMN dob");
            log("connections.dob dropped (birthday lives in events)");
        }
    }

    // ---------------------------------------------------------------- change 5b
    private void makeConnectionsUnique() {
        if (indexExists("connections", "uq_conn_user_name")) return;

        // Rows whose name is already used by an older row of the same user get " (2)", " (3)", ...
        List<Map<String, Object>> duplicates = jdbc.queryForList(
            "SELECT DISTINCT c.id, c.user_id, c.name FROM connections c " +
            "JOIN connections d ON d.user_id = c.user_id AND d.name = c.name AND d.id < c.id ORDER BY c.id");
        for (Map<String, Object> row : duplicates) {
            int n = 2;
            String newName;
            do {
                newName = row.get("name") + " (" + n++ + ")";
            } while (jdbc.queryForObject("SELECT COUNT(*) FROM connections WHERE user_id = ? AND name = ?",
                                         Integer.class, row.get("user_id"), newName) > 0);
            jdbc.update("UPDATE connections SET name = ? WHERE id = ?", newName, row.get("id"));
            log("duplicate connection #" + row.get("id") + " renamed to '" + newName + "'");
        }
        jdbc.execute("ALTER TABLE connections ADD UNIQUE KEY uq_conn_user_name (user_id, name)");
        log("connections: unique (user_id, name) added");
    }

    // ---------------------------------------------------------------- changes 2 and 4
    private void addCheckConstraints() {
        // ----- users
        if (!constraintExists("users", "chk_users_role")) {
            fixValues("users", "role", Rules.ROLES, "'USER'");
            addCheck("users", "chk_users_role", "role IN (" + Rules.sqlList(Rules.ROLES) + ")");
        }
        if (!constraintExists("users", "chk_users_ai")) {
            jdbc.update("UPDATE users SET ai_enabled = 1 WHERE ai_enabled NOT IN (0, 1)");
            addCheck("users", "chk_users_ai", "ai_enabled IN (0, 1)");
        }
        if (!constraintExists("users", "chk_users_share")) {
            jdbc.update("UPDATE users SET share_details = 1 WHERE share_details NOT IN (0, 1)");
            addCheck("users", "chk_users_share", "share_details IN (0, 1)");
        }
        if (!constraintExists("users", "chk_users_remind")) {
            jdbc.update("UPDATE users SET remind_days_before = LEAST(GREATEST(remind_days_before, 0), " + Rules.MAX_REMIND_DAYS + ")");
            addCheck("users", "chk_users_remind", "remind_days_before BETWEEN 0 AND " + Rules.MAX_REMIND_DAYS);
        }

        // ----- connections
        if (!constraintExists("connections", "chk_conn_relation")) {
            fixValues("connections", "relation", Rules.RELATIONS, "'Friend'");
            addCheck("connections", "chk_conn_relation", "relation IN (" + Rules.sqlList(Rules.RELATIONS) + ")");
        }
        if (!constraintExists("connections", "chk_conn_gender")) {
            fixValues("connections", "gender", Rules.GENDERS, "NULL");
            addCheck("connections", "chk_conn_gender", "gender IS NULL OR gender IN (" + Rules.sqlList(Rules.GENDERS) + ")");
        }
        if (!constraintExists("connections", "chk_conn_religion")) {
            fixValues("connections", "religion", Rules.RELIGIONS, "NULL");
            addCheck("connections", "chk_conn_religion", "religion IS NULL OR religion IN (" + Rules.sqlList(Rules.RELIGIONS) + ")");
        }
        if (!constraintExists("connections", "chk_conn_gift_min")) {
            report(jdbc.update("UPDATE connections SET gift_min = 0 WHERE gift_min < 0"), "negative gift_min set to 0");
            addCheck("connections", "chk_conn_gift_min", "gift_min >= 0");
        }
        if (!constraintExists("connections", "chk_conn_gift_range")) {
            // Swap min and max where they are the wrong way round.
            // (MySQL applies SET from left to right, so a plain swap would copy one value twice.)
            report(jdbc.update("UPDATE connections SET gift_max = gift_max + gift_min, " +
                               "gift_min = gift_max - gift_min, gift_max = gift_max - gift_min " +
                               "WHERE gift_max < gift_min"), "gift budgets with max < min swapped");
            addCheck("connections", "chk_conn_gift_range", "gift_max >= gift_min");
        }

        // ----- events, festivals, notifications
        if (!constraintExists("events", "chk_events_yearly")) {
            jdbc.update("UPDATE events SET yearly = 1 WHERE yearly NOT IN (0, 1)");
            addCheck("events", "chk_events_yearly", "yearly IN (0, 1)");
        }
        if (!constraintExists("festivals", "chk_fest_religion")) {
            fixValues("festivals", "religion", Rules.FESTIVAL_RELIGIONS, "'All'");
            addCheck("festivals", "chk_fest_religion", "religion IN (" + Rules.sqlList(Rules.FESTIVAL_RELIGIONS) + ")");
        }
        if (!constraintExists("notifications", "chk_notif_days")) {
            report(jdbc.update("DELETE FROM notifications WHERE days_before NOT BETWEEN 0 AND " + Rules.MAX_REMIND_DAYS),
                   "out-of-range notifications removed");
            addCheck("notifications", "chk_notif_days", "days_before BETWEEN 0 AND " + Rules.MAX_REMIND_DAYS);
        }
        if (!constraintExists("notifications", "chk_notif_read")) {
            jdbc.update("UPDATE notifications SET is_read = 1 WHERE is_read NOT IN (0, 1)");
            addCheck("notifications", "chk_notif_read", "is_read IN (0, 1)");
        }
        if (!constraintExists("notifications", "chk_notif_cleared")) {
            addCheck("notifications", "chk_notif_cleared", "is_cleared IN (0, 1)");
        }

        // ----- wish templates and wishes
        if (!constraintExists("wish_templates", "chk_tpl_tone")) {
            fixValues("wish_templates", "tone", Rules.TONES, "'Emotional'");
            addCheck("wish_templates", "chk_tpl_tone", "tone IN (" + Rules.sqlList(Rules.TONES) + ")");
        }
        if (!constraintExists("wishes", "chk_wish_tone")) {
            fixValues("wishes", "tone", Rules.TONES, "NULL");
            addCheck("wishes", "chk_wish_tone", "tone IS NULL OR tone IN (" + Rules.sqlList(Rules.TONES) + ")");
        }
        if (!constraintExists("wishes", "chk_wish_language")) {
            fixValues("wishes", "language", Rules.LANGUAGES, "NULL");
            addCheck("wishes", "chk_wish_language", "language IS NULL OR language IN (" + Rules.sqlList(Rules.LANGUAGES) + ")");
        }
        if (!constraintExists("wishes", "chk_wish_source")) {
            fixValues("wishes", "source", Rules.WISH_SOURCES, "'TEMPLATE'");
            addCheck("wishes", "chk_wish_source", "source IN (" + Rules.sqlList(Rules.WISH_SOURCES) + ")");
        }
        if (!constraintExists("wishes", "chk_wish_status")) {
            fixValues("wishes", "status", Rules.WISH_STATUSES, "'SAVED'");
            addCheck("wishes", "chk_wish_status", "status IN (" + Rules.sqlList(Rules.WISH_STATUSES) + ")");
        }

        // ----- gifts
        if (!constraintExists("gifts", "chk_gift_price")) {
            report(jdbc.update("UPDATE gifts SET price = 1 WHERE price <= 0"), "gifts with price <= 0 set to 1");
            addCheck("gifts", "chk_gift_price", "price > 0");
        }
        if (!constraintExists("gifts", "chk_gift_category")) {
            fixValues("gifts", "category", Rules.GIFT_CATEGORIES, "'Other'");
            addCheck("gifts", "chk_gift_category", "category IN (" + Rules.sqlList(Rules.GIFT_CATEGORIES) + ")");
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Trims, fixes letter case ("friend" -> "Friend") and replaces unknown values with the fallback. */
    private void fixValues(String table, String column, List<String> allowed, String fallback) {
        jdbc.update("UPDATE " + table + " SET " + column + " = TRIM(" + column + ")");   // '' then counts as unknown
        for (String value : allowed) {
            // the column collation is case-insensitive, so this matches 'friend', 'FRIEND', ...
            jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?", value, value);
        }
        report(jdbc.update("UPDATE " + table + " SET " + column + " = " + fallback +
                           " WHERE " + column + " NOT IN (" + Rules.sqlList(allowed) + ")"),
               table + "." + column + " values not in the allowed list set to " + fallback);
    }

    private void addCheck(String table, String name, String expression) {
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " CHECK (" + expression + ")");
        log("CHECK " + name + " added on " + table);
    }

    private boolean columnExists(String table, String column) {
        return count("SELECT COUNT(*) FROM information_schema.COLUMNS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, column);
    }

    private boolean constraintExists(String table, String name) {
        return count("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?", table, name);
    }

    private boolean indexExists(String table, String name) {
        return count("SELECT COUNT(*) FROM information_schema.STATISTICS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?", table, name);
    }

    private boolean count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n != null && n > 0;
    }

    private void report(int rows, String what) {
        if (rows > 0) log(rows + " " + what);
    }

    private void log(String message) {
        System.out.println("[migration] " + message);
    }
}
