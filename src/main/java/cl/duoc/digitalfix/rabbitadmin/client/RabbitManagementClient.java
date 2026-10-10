package cl.duoc.digitalfix.rabbitadmin.client;

import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtBinding;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtExchange;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtMessage;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtNode;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtOverview;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtPublishResult;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtQueue;
import cl.duoc.digitalfix.rabbitadmin.config.RabbitAdminProperties;
import cl.duoc.digitalfix.rabbitadmin.exception.BrokerUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cliente de la Management HTTP API (consultas, lectura de mensajes y
 * publicacion con indicador "routed"). Con cluster de 2 nodos se configura
 * la URL de ambos: si el primero no responde se usa el siguiente.
 * Las operaciones de creacion/eliminacion usan AMQP (AmqpAdmin), no esta API.
 */
@Component
public class RabbitManagementClient {

    private static final Logger log = LoggerFactory.getLogger(RabbitManagementClient.class);

    private final List<RestClient> clients;
    private final String vhost;

    public RabbitManagementClient(RabbitAdminProperties props) {
        this.vhost = props.vhost();
        this.clients = props.managementUrls().stream().map(url -> build(url.trim(), props)).toList();
    }

    private static RestClient build(String baseUrl, RabbitAdminProperties props) {
        // VALUES_ONLY codifica el vhost "/" como %2F, como exige la API
        DefaultUriBuilderFactory uris = new DefaultUriBuilderFactory(baseUrl);
        uris.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(2000);
        rf.setReadTimeout(5000);
        return RestClient.builder()
            .uriBuilderFactory(uris)
            .requestFactory(rf)
            .defaultHeaders(h -> h.setBasicAuth(props.managementUsername(), props.managementPassword()))
            .build();
    }

    public MgmtOverview overview() {
        return call(c -> c.get().uri("/api/overview").retrieve().body(MgmtOverview.class));
    }

    public List<MgmtNode> nodes() {
        return ManagementModels.orEmpty(call(c -> c.get().uri("/api/nodes").retrieve()
            .body(new ParameterizedTypeReference<List<MgmtNode>>() {})));
    }

    public List<MgmtQueue> queues() {
        return ManagementModels.orEmpty(call(c -> c.get().uri("/api/queues/{vhost}", vhost).retrieve()
            .body(new ParameterizedTypeReference<List<MgmtQueue>>() {})));
    }

    public Optional<MgmtQueue> queue(String name) {
        return optional(c -> c.get().uri("/api/queues/{vhost}/{name}", vhost, name).retrieve().body(MgmtQueue.class));
    }

    public List<MgmtExchange> exchanges() {
        return ManagementModels.orEmpty(call(c -> c.get().uri("/api/exchanges/{vhost}", vhost).retrieve()
            .body(new ParameterizedTypeReference<List<MgmtExchange>>() {})));
    }

    public Optional<MgmtExchange> exchange(String name) {
        return optional(c -> c.get().uri("/api/exchanges/{vhost}/{name}", vhost, name).retrieve().body(MgmtExchange.class));
    }

    public List<MgmtBinding> bindings() {
        return ManagementModels.orEmpty(call(c -> c.get().uri("/api/bindings/{vhost}", vhost).retrieve()
            .body(new ParameterizedTypeReference<List<MgmtBinding>>() {})));
    }

    public List<MgmtBinding> bindings(String exchange, String queue) {
        return ManagementModels.orEmpty(call(c -> c.get()
            .uri("/api/bindings/{vhost}/e/{exchange}/q/{queue}", vhost, exchange, queue).retrieve()
            .body(new ParameterizedTypeReference<List<MgmtBinding>>() {})));
    }

    /** Lee mensajes SIN consumirlos (ackmode ack_requeue_true: vuelven a la cola). */
    public List<MgmtMessage> peek(String queue, int count) {
        Map<String, Object> body = Map.of("count", count, "ackmode", "ack_requeue_true",
            "encoding", "auto", "truncate", 20000);
        return ManagementModels.orEmpty(call(c -> c.post().uri("/api/queues/{vhost}/{name}/get", vhost, queue)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
            .body(new ParameterizedTypeReference<List<MgmtMessage>>() {})));
    }

    /** Publica via API para obtener "routed" (si alguna cola recibio el mensaje). */
    public boolean publish(String exchange, String routingKey, String payload, Map<String, Object> properties) {
        String target = exchange.isEmpty() ? "amq.default" : exchange;
        Map<String, Object> body = Map.of("properties", properties, "routing_key", routingKey,
            "payload", payload, "payload_encoding", "string");
        MgmtPublishResult result = call(c -> c.post().uri("/api/exchanges/{vhost}/{name}/publish", vhost, target)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(MgmtPublishResult.class));
        return result != null && result.routed();
    }

    private <T> Optional<T> optional(Function<RestClient, T> fn) {
        try {
            return Optional.ofNullable(call(fn));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }

    private <T> T call(Function<RestClient, T> fn) {
        ResourceAccessException last = null;
        for (RestClient client : clients) {
            try {
                return fn.apply(client);
            } catch (ResourceAccessException ex) {
                last = ex;
                log.warn("Management API no responde ({}), probando el siguiente nodo", ex.getMessage());
            }
        }
        throw new BrokerUnavailableException("Ningun nodo de RabbitMQ responde a la Management API", last);
    }
}
