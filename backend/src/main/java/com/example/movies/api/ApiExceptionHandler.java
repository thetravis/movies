package com.example.movies.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.example.movies.generated.model.FieldErrorDto;
import com.example.movies.generated.model.ProblemDto;
import com.example.movies.service.DuplicateMovieException;
import com.example.movies.service.MovieNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
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
 * <p>
 * Every error is built as Spring's {@link ProblemDetail} and then converted in
 * {@link #handleExceptionInternal} to the generated {@link ProblemDto}. Spring leaves empty fields
 * out of a {@code ProblemDetail}; the contract requires every field to be present ({@code null}
 * when empty), which the generated DTO guarantees.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String VALIDATION_FAILED = "Request validation failed.";
    private static final String ERRORS_PROPERTY = "errors";
    /** RFC 9457: when no problem type is given, the type is "about:blank". */
    private static final URI DEFAULT_TYPE = URI.create("about:blank");

    @ExceptionHandler(MovieNotFoundException.class)
    ResponseEntity<Object> handleNotFound(MovieNotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateMovieException.class)
    ResponseEntity<Object> handleDuplicate(DuplicateMovieException ex, WebRequest request) {
        return problem(ex, HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Validation done in the service layer (e.g. values that are blank after trimming). */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Safety net for database constraint violations, e.g. two requests adding the same movie at the
     * same time. The details are logged but not returned, as they may reveal internals.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(ex, HttpStatus.CONFLICT, "The request conflicts with existing data.", request);
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

    /**
     * All error responses pass through here: the {@link ProblemDetail} body built by this class or
     * by the base class is replaced with the contract's {@link ProblemDto}.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null || !(response.getBody() instanceof ProblemDetail problem)) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(toProblemDto(problem, request));
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    private static ProblemDetail validationProblem(List<FieldErrorDto> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        problem.setProperty(ERRORS_PROPERTY, errors.stream()
                .sorted(Comparator.comparing(FieldErrorDto::getField).thenComparing(FieldErrorDto::getMessage))
                .toList());
        return problem;
    }

    private static ProblemDto toProblemDto(ProblemDetail problem, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(problem.getStatus());
        String title = problem.getTitle() != null ? problem.getTitle()
                : status != null ? status.getReasonPhrase() : "Error";
        URI instance = problem.getInstance() != null ? problem.getInstance() : requestUri(request);
        return new ProblemDto()
                .type(problem.getType() != null ? problem.getType() : DEFAULT_TYPE)
                .title(title)
                .status(problem.getStatus())
                .detail(problem.getDetail())
                .instance(instance)
                .errors(fieldErrors(problem));
    }

    /** The {@code errors} list of a validation problem, or {@code null} for other problems. */
    @SuppressWarnings("unchecked")
    private static @Nullable List<FieldErrorDto> fieldErrors(ProblemDetail problem) {
        Object errors = problem.getProperties() == null ? null : problem.getProperties().get(ERRORS_PROPERTY);
        return errors instanceof List<?> list ? (List<FieldErrorDto>) list : null;
    }

    private static URI requestUri(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? URI.create(servletRequest.getRequest().getRequestURI())
                : URI.create("");
    }

    private static FieldErrorDto toFieldError(FieldError error) {
        return new FieldErrorDto(error.getField(), error.getDefaultMessage());
    }
}
