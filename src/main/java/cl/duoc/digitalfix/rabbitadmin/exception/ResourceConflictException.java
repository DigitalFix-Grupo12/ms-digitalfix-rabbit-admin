package cl.duoc.digitalfix.rabbitadmin.exception;

/** Ya existe, esta protegido o el estado no permite la operacion -> 409. */
public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) {
        super(message);
    }
}
