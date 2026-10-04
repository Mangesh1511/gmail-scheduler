package com.example.scheduler.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {
    public String requireOwnerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("ownerId") instanceof String ownerId) return ownerId;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connect a Google account to continue");
    }
}
