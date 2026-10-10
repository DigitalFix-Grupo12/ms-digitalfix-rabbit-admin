package cl.duoc.digitalfix.rabbitadmin.exception;

/** Cola, exchange o binding inexistente -> 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
