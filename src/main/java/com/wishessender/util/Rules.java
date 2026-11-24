package com.wishessender.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

/**
 * Allowed values for the categorical columns and small validation helpers.
 * The same lists are used in the CHECK constraints of schema.sql, so the backend
 * gives a friendly 400 message before the database would reject the row.
 */
public class Rules {

    public static final List<String> ROLES = Arrays.asList("USER", "ADMIN");
    public static final List<String> RELATIONS =
            Arrays.asList("Family", "Friend", "Partner", "Colleague", "Client", "Customer");
    public static final List<String> GENDERS = Arrays.asList("Female", "Male", "Other");
    public static final List<String> RELIGIONS = Arrays.asList("Islam", "Hinduism", "Christian", "Buddhism", "Other");
    public static final List<String> FESTIVAL_RELIGIONS = Arrays.asList("All", "Islam", "Hinduism", "Christian", "Buddhism");
    public static final List<String> TONES = Arrays.asList("Formal", "Emotional", "Funny", "Romantic");
    public static final List<String> LANGUAGES = Arrays.asList("English", "Bangla");
    public static final List<String> WISH_SOURCES = Arrays.asList("AI", "TEMPLATE");
    public static final List<String> WISH_STATUSES = Arrays.asList("SAVED", "SENT");
    public static final List<String> GIFT_CATEGORIES = Arrays.asList("Books", "Electronics", "Home", "Food", "Flowers",
            "Clothing", "Experience", "Travel", "Art", "Sports", "Office", "Other");

    /** Highest value for "remind me N days before" (matches chk_users_remind / chk_notif_days). */
    public static final int MAX_REMIND_DAYS = 30;

    /**
     * Returns the allowed spelling of value (any letter case is accepted, "friend" -> "Friend").
     * null stays null (use Db.required first if the field is mandatory). Unknown value -> 400.
     */
    public static String oneOf(String value, List<String> allowed, String field) {
        if (value == null) return null;
        for (String option : allowed) {
            if (option.equalsIgnoreCase(value.trim())) return option;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                field + " must be one of: " + String.join(", ", allowed));
    }

    /** Checks min <= value <= max, or sends 400. */
    public static int range(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be between " + min + " and " + max);
        }
        return value;
    }

    /** A yes/no flag stored as TINYINT(1): only 0 or 1 is allowed. */
    public static int flag(int value, String field) {
        if (value != 0 && value != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be 0 or 1 (true/false)");
        }
        return value;
    }

    /** Turns a list into SQL text for an IN (...) check: 'A', 'B', 'C'. */
    public static String sqlList(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(", ");
            sb.append('\'').append(v.replace("'", "''")).append('\'');
        }
        return sb.toString();
    }
}
