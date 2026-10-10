package cl.duoc.digitalfix.rabbitadmin.dto;

import java.util.List;
import java.util.Map;

/** Respuestas de la API (independientes del formato de RabbitMQ). */
public final class Views {

    private Views() {}

    public record NodeView(String name, boolean running, long memUsedMb, long uptimeMinutes) {}

    public record ClusterOverview(String clusterName, String rabbitmqVersion, List<NodeView> nodes,
                                  int queues, int exchanges, int connections, int consumers,
                                  long messagesReady, long messagesUnacked) {}

    public record QueueView(String name, boolean durable, boolean autoDelete, String type, String node,
                            long messagesReady, long messagesUnacked, int consumers,
                            String deadLetterExchange, String deadLetterRoutingKey,
                            Long messageTtlMs, Long maxLength, boolean isProtected, boolean deadLetterQueue) {}

    public record ExchangeView(String name, String type, boolean durable, boolean internal, boolean isProtected) {}

    public record BindingView(String exchange, String queue, String routingKey) {}

    public record PeekedMessage(String messageId, String exchange, String routingKey, boolean redelivered,
                                String originalQueue, String deathReason, Integer retryCount, String lastError,
                                Map<String, Object> headers, String payload) {}

    public record PublishResult(String messageId, String exchange, String routingKey, boolean routed, String note) {}

    public record ReplayResult(String deadLetterQueue, int moved, Map<String, Integer> movedTo, int skipped) {}

    public record PurgeResult(String queue, int purged) {}
}
