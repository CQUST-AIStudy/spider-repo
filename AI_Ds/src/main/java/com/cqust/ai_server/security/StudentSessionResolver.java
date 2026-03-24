package com.cqust.ai_server.security;

import com.cqust.ai_server.entity.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StudentSessionResolver {

    public UserEntity requireStudent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        }

        Object currentUser = session.getAttribute("currentUser");
        if (!(currentUser instanceof UserEntity user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        }
        if (!"student".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "student role required");
        }

        String studentId = normalizeStudentId(user.getUsernum());
        if (studentId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "student id missing");
        }
        return user;
    }

    public String requireStudentId(HttpServletRequest request) {
        return normalizeStudentId(requireStudent(request).getUsernum());
    }

    public String requireAuthorizedStudentId(String requestedStudentId, HttpServletRequest request) {
        String sessionStudentId = requireStudentId(request);
        if (!sessionStudentId.equals(normalizeStudentId(requestedStudentId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
        return sessionStudentId;
    }

    private String normalizeStudentId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
