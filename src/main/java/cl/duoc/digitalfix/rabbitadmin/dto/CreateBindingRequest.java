package cl.duoc.digitalfix.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** POST /api/rabbit/bindings  { "exchange": "...", "queue": "...", "routingKey": "..." } */
public record CreateBindingRequest(
    @NotBlank(message = "es obligatorio")
    @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String exchange,
    @NotBlank(message = "es obligatorio")
    @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String queue,
    @NotNull(message = "es obligatorio (puede ser vacio solo en exchanges fanout)")
    @Pattern(regexp = RabbitNames.BINDING_KEY_REGEX, message = RabbitNames.BINDING_KEY_MESSAGE) String routingKey
) {}
