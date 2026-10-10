package cl.duoc.digitalfix.rabbitadmin.exception;

/** Combinacion de parametros invalida que no cubre Bean Validation -> 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
