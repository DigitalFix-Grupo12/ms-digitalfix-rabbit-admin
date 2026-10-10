package cl.duoc.digitalfix.rabbitadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/rabbit/queues
 *
 * @param name                cola a crear
 * @param durable             sobrevive a reinicios del broker (default true)
 * @param autoDelete          se borra al irse el ultimo consumidor (default false)
 * @param messageTtlMs        TTL de los mensajes en ms (opcional, maximo 7 dias)
 * @param maxLength           maximo de mensajes en cola (opcional)
 * @param withDeadLetterQueue crea ademas &lt;name&gt;.dlq enlazada al dead-letter exchange (default false)
 */
public record CreateQueueRequest(
    @NotBlank(message = "es obligatorio")
    @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
    Boolean durable,
    Boolean autoDelete,
    @Positive(message = "debe ser mayor que 0") @Max(value = 604_800_000, message = "no puede superar 7 dias") Long messageTtlMs,
    @Positive(message = "debe ser mayor que 0") @Max(value = 1_000_000, message = "no puede superar 1.000.000") Integer maxLength,
    Boolean withDeadLetterQueue
) {
    public boolean isDurable() {
        return durable == null || durable;
    }

    public boolean isAutoDelete() {
        return autoDelete != null && autoDelete;
    }

    public boolean wantsDeadLetterQueue() {
        return withDeadLetterQueue != null && withDeadLetterQueue;
    }
}
