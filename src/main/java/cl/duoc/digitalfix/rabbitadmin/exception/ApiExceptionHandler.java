package cl.duoc.digitalfix.rabbitadmin.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Errores en formato RFC 7807 (application/problem+json) con mensajes claros.
 * Validacion -> 400 con el detalle por campo en "errors".
 * Orden maximo: tiene prioridad sobre el manejador generico de Spring.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.merge(fe.getField(), fe.getDefaultMessage(), (a, b) -> a + "; " + b);
        }
        return validation(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail invalidParams(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
            errors.merge(r.getMethodParameter().getParameterName(), e.getDefaultMessage(), (a, b) -> a + "; " + b)));
        return validation(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidParams(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            errors.merge(path.substring(path.lastIndexOf('.') + 1), v.getMessage(), (a, b) -> a + "; " + b);
        });
        return validation(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Cuerpo invalido",
            "JSON mal formado o con valores no permitidos (type acepta: direct, topic, fanout, headers)");
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail badParam(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Parametro invalido", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail invalid(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud invalida", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail conflict(ResourceConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    @ExceptionHandler({BrokerUnavailableException.class, AmqpConnectException.class})
    public ProblemDetail unavailable(RuntimeException ex) {
        log.warn("RabbitMQ no disponible: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no disponible",
            "No hay conexion con el cluster RabbitMQ. Reintenta en unos segundos.");
    }

    @ExceptionHandler(AmqpException.class)
    public ProblemDetail amqp(AmqpException ex) {
        log.error("Error AMQP", ex);
        return problem(HttpStatus.BAD_GATEWAY, "Error de RabbitMQ", ex.getMessage());
    }

    private static ProblemDetail validation(Map<String, String> errors) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Datos invalidos", "Revisa los campos indicados en 'errors'");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
