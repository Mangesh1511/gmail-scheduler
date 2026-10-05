package com.example.scheduler.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Replaces Spring Boot's Whitelabel page with a safe browser page and a stable JSON error body.
 * Detailed exception information stays in server logs rather than being exposed to callers.
 */
@Controller
public class ApplicationErrorController implements ErrorController {
    private static final Logger log = LoggerFactory.getLogger(ApplicationErrorController.class);
    private static final String GENERIC_MESSAGE = "The request could not be completed. Please try again.";

    @RequestMapping("/error")
    @ResponseBody
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = statusAttribute instanceof Integer value ? value : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        Object requestPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object exceptionAttribute = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String path = requestPath instanceof String value ? value : "unknown";
        String exceptionType = exceptionAttribute instanceof Throwable exception
                ? exception.getClass().getSimpleName() : "unknown";
        log.error("Request failed; status={}, path={}, exceptionType={}", status.value(), path, exceptionType);

        String message = messageFor(status);
        if (acceptsHtml(request)) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_HTML)
                    .body(errorPage(status.value(), status.getReasonPhrase(), message));
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message,
                        "path", requestPath instanceof String errorPath ? errorPath : ""
                ));
    }

    private static boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request could not be understood. Please try again.";
            case UNAUTHORIZED -> "Your sign-in session may have expired. Connect Gmail and try again.";
            case FORBIDDEN -> "You do not have permission to complete this request.";
            case NOT_FOUND -> "The requested page or resource could not be found.";
            case TOO_MANY_REQUESTS -> "Too many requests. Please wait a moment and try again.";
            default -> status.is5xxServerError()
                    ? "Mail.ai had a temporary problem. Please try again shortly."
                    : GENERIC_MESSAGE;
        };
    }

    private static String errorPage(int status, String title, String message) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%d — %s</title>
                  <style>
                    body{margin:0;background:#f6f7fb;color:#242735;font:16px/1.5 system-ui,-apple-system,sans-serif}
                    main{box-sizing:border-box;max-width:560px;margin:12vh auto;padding:40px 32px;background:#fff;
                      border:1px solid #e7e8ef;border-radius:18px;box-shadow:0 16px 48px #20243a12}
                    .brand{color:#6659e8;font-size:13px;font-weight:800;letter-spacing:.14em;text-transform:uppercase}
                    h1{margin:22px 0 8px;font-size:28px;line-height:1.2}p{color:#6b7080}
                    .status{margin-top:26px;color:#9a9eaa;font-size:13px}
                    @media(max-width:600px){main{margin:24px 16px;padding:30px 22px}}
                  </style>
                </head>
                <body><main><div class="brand">mail.ai</div><h1>%s</h1><p>%s</p>
                <div class="status">Error %d</div></main></body></html>
                """.formatted(status, escapeHtml(title), escapeHtml(title), escapeHtml(message), status);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
