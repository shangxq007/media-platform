package com.example.platform.web;

import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler returning ProblemDetail with errorCode + message + details.
 * Supports configurable error codes from error-codes.json and i18n messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorCodeRegistry errorCodeRegistry;

    public GlobalExceptionHandler(ErrorCodeRegistry errorCodeRegistry) {
        this.errorCodeRegistry = errorCodeRegistry;
    }

    @ExceptionHandler(PlatformException.class)
    public ProblemDetail handlePlatform(PlatformException ex, HttpServletRequest request) {
        var code = ex.getErrorCode();
        String message = ex.getLocalizedMessage();

        // Try to get i18n message from registry
        if (errorCodeRegistry != null) {
            var configurableCode = errorCodeRegistry.getErrorCode(code.code());
            if (configurableCode.isPresent()) {
                message = configurableCode.get().message(getLocale(request));
            }
        }
        // Fallback to ConfigurableErrorCode built-in messages
        if (code instanceof ConfigurableErrorCode cce && message.equals(ex.getMessage())) {
            message = cce.message(getLocale(request));
        }

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.status()), message);
        problem.setTitle(code.title());
        problem.setType(URI.create("https://example.com/problems/" + code.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.code());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", OffsetDateTime.now().toString());

        // Add details if present
        if (ex.getDetails() != null && !ex.getDetails().isEmpty()) {
            problem.setProperty("details", ex.getDetails());
        }

        return problem;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ProblemDetail handleValidation(Exception ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle(CommonErrorCode.INVALID_REQUEST.title());
        problem.setType(URI.create("https://example.com/problems/" + CommonErrorCode.INVALID_REQUEST.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", CommonErrorCode.INVALID_REQUEST.code());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", OffsetDateTime.now().toString());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle(CommonErrorCode.INVALID_REQUEST.title());
        problem.setType(URI.create("https://example.com/problems/" + CommonErrorCode.INVALID_REQUEST.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", CommonErrorCode.INVALID_REQUEST.code());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", OffsetDateTime.now().toString());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        problem.setTitle(CommonErrorCode.INTERNAL_ERROR.title());
        problem.setType(URI.create("https://example.com/problems/" + CommonErrorCode.INTERNAL_ERROR.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", CommonErrorCode.INTERNAL_ERROR.code());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", OffsetDateTime.now().toString());
        return problem;
    }

    private String getLocale(HttpServletRequest request) {
        String lang = request.getHeader("Accept-Language");
        if (lang != null && lang.startsWith("zh")) return "zh";
        return "en";
    }
}
