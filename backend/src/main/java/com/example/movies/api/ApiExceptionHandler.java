package com.example.movies.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.example.movies.generated.model.FieldErrorDto;
import com.example.movies.service.DuplicateMovieException;
import com.example.movies.service.MovieNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 Problem Details responses ({@code application/problem+json}),
 * matching the {@code Problem} schema of the API contract.
 * <p>
 * Standard Spring MVC errors (malformed JSON, wrong parameter types, unsupported media type, ...)
 * are handled by the {@link ResponseEntityExceptionHandler} base class. Validation errors
 * additionally get an {@code errors} list with one entry per invalid field.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String VALIDATION_FAILED = "Request validation failed.";

    @ExceptionHandler(MovieNotFoundException.class)
    ProblemDetail handleNotFound(MovieNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateMovieException.class)
    ProblemDetail handleDuplicate(DuplicateMovieException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Validation done in the service layer (e.g. values that are blank after trimming). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    /**
     * Safety net for database constraint violations, e.g. two requests adding the same movie at the
     * same time. The details are logged but not returned, as they may reveal internals.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with existing data.");
    }

    /** Invalid request body ({@code @Valid @RequestBody}). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toFieldError)
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    /** Invalid query or path parameters (constraints such as {@code @Max} on controller method parameters). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(error -> errors.add(toFieldError(error)));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    errors.add(new FieldErrorDto(parameter, error.getDefaultMessage()));
                }
            }
        });
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    private static ProblemDetail validationProblem(List<FieldErrorDto> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        problem.setProperty("errors", errors.stream()
                .sorted(Comparator.comparing(FieldErrorDto::getField).thenComparing(FieldErrorDto::getMessage))
                .toList());
        return problem;
    }

    private static FieldErrorDto toFieldError(FieldError error) {
        return new FieldErrorDto(error.getField(), error.getDefaultMessage());
    }
}
