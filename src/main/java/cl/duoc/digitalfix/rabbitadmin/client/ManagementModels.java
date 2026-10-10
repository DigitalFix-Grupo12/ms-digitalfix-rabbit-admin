package cl.duoc.digitalfix.rabbitadmin.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/** Subconjunto de las respuestas JSON de la Management HTTP API de RabbitMQ. */
public final class ManagementModels {

    private ManagementModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MgmtQueue(String name, boolean durable, boolean autoDelete, Map<String, Object> arguments,
                            String node, String type, String state, long messages, long messagesReady,
                            long messagesUnacknowledged, int consumers) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MgmtExchange(String name, String type, boolean durable, boolean autoDelete, boolean internal) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MgmtBinding(String source, String destination, String destinationType, String routingKey,
                              String propertiesKey) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MgmtOverview(String clusterName, String rabbitmqVersion, Totals queueTotals, Objects objectTotals) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record Totals(long messages, long messagesReady, long messagesUnacknowledged) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Objects(int queues, int exchanges, int connections, int consumers, int channels) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MgmtNode(String name, boolean running, long memUsed, long memLimit, long uptime, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MgmtMessage(String payload, String payloadEncoding, boolean redelivered, String exchange,
                              String routingKey, long messageCount, Map<String, Object> properties) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MgmtPublishResult(boolean routed) {}

    /** Utilidad: lista vacia si la API devolvio null. */
    static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
