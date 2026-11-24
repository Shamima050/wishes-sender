package com.wishessender.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpSession;

/**
 * Login is kept simple: after a successful login we store the user's id and role
 * in the HTTP session (the browser keeps a JSESSIONID cookie).
 */
public class Auth {

    /** Returns the logged-in user's id, or sends 401 if nobody is logged in. */
    public static int userId(HttpSession session) {
        Object id = session.getAttribute("userId");
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first");
        }
        return (Integer) id;
    }

    /** Sends 403 if the logged-in user is not an admin. */
    public static void requireAdmin(HttpSession session) {
        userId(session);
        if (!"ADMIN".equals(session.getAttribute("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
        }
    }
}
