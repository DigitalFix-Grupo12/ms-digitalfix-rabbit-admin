package cl.duoc.digitalfix.rabbitadmin.service;

import cl.duoc.digitalfix.rabbitadmin.dto.Views.ReplayResult;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mueve mensajes de una DLQ a su cola de origen (header x-first-death-queue
 * o x-death[0].queue). Cada mensaje se confirma en la DLQ SOLO despues de que
 * el broker confirma la republicacion (publisher confirms en el mismo canal):
 * si algo falla, el mensaje sigue en la DLQ.
 */
@Component
public class DeadLetterReplayer {

    private static final List<String> RETRY_HEADERS = List.of(
        "x-death", "x-first-death-queue", "x-first-death-reason", "x-first-death-exchange",
        "x-last-death-queue", "x-last-death-reason", "x-last-death-exchange",
        "x-retry-count", "x-last-error");

    private final RabbitTemplate rabbitTemplate;

    public DeadLetterReplayer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public ReplayResult replay(String dlq, int max) {
        return rabbitTemplate.execute(channel -> {
            channel.confirmSelect();
            Map<String, Integer> movedTo = new LinkedHashMap<>();
            int moved = 0;
            int skipped = 0;
            // Solo los mensajes presentes al inicio: si uno reprocesado vuelve a
            // fallar y regresa a la DLQ, no se toma de nuevo en esta misma pasada.
            long available = channel.messageCount(dlq);
            int limit = (int) Math.min(max, available);
            for (int i = 0; i < limit; i++) {
                GetResponse response = channel.basicGet(dlq, false);
                if (response == null) break;
                long tag = response.getEnvelope().getDeliveryTag();
                AMQP.BasicProperties props = response.getProps();
                Map<String, Object> headers = props.getHeaders() == null ? new HashMap<>() : new HashMap<>(props.getHeaders());
                String target = originalQueue(headers);
                if (target == null) {
                    // Sin origen conocido no se puede reprocesar: queda en la DLQ
                    channel.basicNack(tag, false, true);
                    skipped++;
                    break;
                }
                RETRY_HEADERS.forEach(headers::remove);
                headers.put("x-replayed-from", dlq);
                channel.basicPublish("", target, true, props.builder().headers(headers).build(), response.getBody());
                channel.waitForConfirmsOrDie(5_000);
                channel.basicAck(tag, false);
                movedTo.merge(target, 1, Integer::sum);
                moved++;
            }
            return new ReplayResult(dlq, moved, movedTo, skipped);
        });
    }

    static String originalQueue(Map<String, Object> headers) {
        Object first = headers.get("x-first-death-queue");
        if (first != null) return first.toString();
        if (headers.get("x-death") instanceof List<?> deaths && !deaths.isEmpty()
                && deaths.get(0) instanceof Map<?, ?> death && death.get("queue") != null) {
            return death.get("queue").toString();
        }
        return null;
    }
}
