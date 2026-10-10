package cl.duoc.digitalfix.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** POST /api/rabbit/exchanges  { "name": "...", "type": "direct|topic|fanout|headers", "durable": true } */
public record CreateExchangeRequest(
    @NotBlank(message = "es obligatorio")
    @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
    @NotNull(message = "es obligatorio (direct, topic, fanout o headers)") ExchangeType type,
    Boolean durable
) {
    public boolean isDurable() {
        return durable == null || durable;
    }
}
