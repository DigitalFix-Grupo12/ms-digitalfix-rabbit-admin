package cl.duoc.digitalfix.rabbitadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ms-digitalfix-rabbit-admin: administracion de RabbitMQ por API REST
 * (colas, exchanges, bindings, DLQ). Solo lo invoca el BFF con rol Admin.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RabbitAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(RabbitAdminApplication.class, args);
    }
}
