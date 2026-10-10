package cl.duoc.digitalfix.rabbitadmin.exception;

/** RabbitMQ no disponible (ningun nodo responde) -> 503. */
public class BrokerUnavailableException extends RuntimeException {
    public BrokerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
