package com.example.platform.web;

import com.example.platform.shared.web.CommonErrorCode;
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

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlatformException.class)
    public ProblemDetail handlePlatform(PlatformException ex, HttpServletRequest request) {
        var code = ex.getErrorCode();
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.status()), ex.getMessage());
        problem.setTitle(code.title());
        problem.setType(URI.create("https://example.com/problems/" + code.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.code());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", OffsetDateTime.now().toString());
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
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception ex, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle(CommonErrorCode.INTERNAL_ERROR.title());
        problem.setType(URI.create("https://example.com/problems/" + CommonErrorCode.INTERNAL_ERROR.code()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", CommonErrorCode.INTERNAL_ERROR.code());
        problem.setProperty("traceId", MDC.get("traceId"));
        return problem;
    }
}
