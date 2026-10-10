package cl.duoc.digitalfix.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * POST /api/rabbit/messages: publica un mensaje de prueba.
 * body es texto libre (normalmente el JSON de un envelope); puede ser un JSON
 * invalido a proposito para demostrar el envio a la DLQ.
 */
public record PublishMessageRequest(
    @NotNull(message = "es obligatorio (\"\" = exchange por defecto)")
    @Pattern(regexp = RabbitNames.PUBLISH_EXCHANGE_REGEX, message = "nombre de exchange invalido") String exchange,
    @NotNull(message = "es obligatorio")
    @Pattern(regexp = RabbitNames.PUBLISH_KEY_REGEX, message = RabbitNames.PUBLISH_KEY_MESSAGE) String routingKey,
    @NotBlank(message = "es obligatorio") @Size(max = 65_536, message = "no puede superar 64 KB") String body,
    @Size(max = 10, message = "maximo 10 headers")
    Map<@Pattern(regexp = "^[A-Za-z0-9-]{1,64}$", message = "nombre de header invalido") String,
        @Size(max = 200, message = "valor de header de maximo 200 caracteres") String> headers
) {
    public Map<String, String> headersOrEmpty() {
        return headers == null ? Map.of() : headers;
    }
}
