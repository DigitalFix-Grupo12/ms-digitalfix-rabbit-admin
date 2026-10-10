package cl.duoc.digitalfix.rabbitadmin.service;

import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtExchange;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtMessage;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtOverview;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtQueue;
import cl.duoc.digitalfix.rabbitadmin.client.RabbitManagementClient;
import cl.duoc.digitalfix.rabbitadmin.config.RabbitAdminProperties;
import cl.duoc.digitalfix.rabbitadmin.dto.CreateBindingRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.CreateExchangeRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.CreateQueueRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.ExchangeType;
import cl.duoc.digitalfix.rabbitadmin.dto.PublishMessageRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.BindingView;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.ClusterOverview;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.ExchangeView;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.NodeView;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.PeekedMessage;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.PublishResult;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.PurgeResult;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.QueueView;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.ReplayResult;
import cl.duoc.digitalfix.rabbitadmin.exception.InvalidRequestException;
import cl.duoc.digitalfix.rabbitadmin.exception.ResourceConflictException;
import cl.duoc.digitalfix.rabbitadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Toda la logica de administracion de RabbitMQ vive aqui. Los controladores
 * solo invocan estos metodos de alto nivel y no conocen AmqpAdmin, la
 * Management API ni detalles del protocolo.
 *
 * - Crear / eliminar colas, exchanges y bindings: AMQP (AmqpAdmin).
 * - Consultas, lectura sin consumir y publicacion de prueba: Management API.
 * - Reproceso de DLQ: DeadLetterReplayer.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitManagementClient management;
    private final DeadLetterReplayer replayer;
    private final RabbitAdminProperties props;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitManagementClient management,
                              DeadLetterReplayer replayer, RabbitAdminProperties props) {
        this.amqpAdmin = amqpAdmin;
        this.management = management;
        this.replayer = replayer;
        this.props = props;
    }

    // ------------------------------------------------------------------ cluster

    public ClusterOverview overview() {
        MgmtOverview o = management.overview();
        List<NodeView> nodes = management.nodes().stream()
            .map(n -> new NodeView(n.name(), n.running(), n.memUsed() / (1024 * 1024), n.uptime() / 60_000))
            .sorted(Comparator.comparing(NodeView::name))
            .toList();
        var totals = o.queueTotals();
        var objects = o.objectTotals();
        return new ClusterOverview(o.clusterName(), o.rabbitmqVersion(), nodes,
            objects == null ? 0 : objects.queues(), objects == null ? 0 : objects.exchanges(),
            objects == null ? 0 : objects.connections(), objects == null ? 0 : objects.consumers(),
            totals == null ? 0 : totals.messagesReady(), totals == null ? 0 : totals.messagesUnacknowledged());
    }

    // ------------------------------------------------------------------ colas

    public List<QueueView> listQueues() {
        return management.queues().stream().map(this::toView).sorted(Comparator.comparing(QueueView::name)).toList();
    }

    public QueueView getQueue(String name) {
        return toView(requireQueue(name));
    }

    public QueueView createQueue(CreateQueueRequest req, String user) {
        String name = req.name();
        boolean isDlqName = name.endsWith(props.deadLetterSuffix());
        if (req.wantsDeadLetterQueue() && isDlqName) {
            throw new InvalidRequestException("Una DLQ ('" + name + "') no puede tener su propia DLQ");
        }
        if (management.queue(name).isPresent()) {
            throw new ResourceConflictException("La cola '" + name + "' ya existe");
        }
        QueueBuilder builder = req.isDurable() ? QueueBuilder.durable(name) : QueueBuilder.nonDurable(name);
        if (req.isAutoDelete()) builder.autoDelete();
        if (req.messageTtlMs() != null) builder.ttl(Math.toIntExact(req.messageTtlMs()));
        if (req.maxLength() != null) builder.maxLength(req.maxLength());

        if (req.wantsDeadLetterQueue()) {
            String dlqName = name + props.deadLetterSuffix();
            if (management.queue(dlqName).isPresent()) {
                throw new ResourceConflictException("Ya existe la DLQ '" + dlqName + "'");
            }
            DirectExchange dlx = ExchangeBuilder.directExchange(props.deadLetterExchange()).durable(true).build();
            Queue dlq = QueueBuilder.durable(dlqName).build();
            amqpAdmin.declareExchange(dlx);
            amqpAdmin.declareQueue(dlq);
            amqpAdmin.declareBinding(BindingBuilder.bind(dlq).to(dlx).with(dlqName));
            builder.deadLetterExchange(props.deadLetterExchange()).deadLetterRoutingKey(dlqName);
        }
        amqpAdmin.declareQueue(builder.build());
        log.info("[ADMIN] {} creo la cola {} (durable={}, dlq={})", user, name, req.isDurable(), req.wantsDeadLetterQueue());
        return management.queue(name).map(this::toView).orElseThrow(
            () -> new ResourceNotFoundException("La cola '" + name + "' no quedo visible en el broker"));
    }

    public void deleteQueue(String name, boolean ifUnused, boolean ifEmpty, String user) {
        rejectProtected(name, "la cola");
        MgmtQueue queue = requireQueue(name);
        if (ifUnused && queue.consumers() > 0) {
            throw new ResourceConflictException("La cola '" + name + "' tiene " + queue.consumers() + " consumidor(es) activo(s)");
        }
        if (ifEmpty && queue.messages() > 0) {
            throw new ResourceConflictException("La cola '" + name + "' tiene " + queue.messages() + " mensaje(s)");
        }
        try {
            amqpAdmin.deleteQueue(name, ifUnused, ifEmpty);
        } catch (AmqpException ex) {
            throw new ResourceConflictException("RabbitMQ rechazo la eliminacion de '" + name + "': " + ex.getMessage());
        }
        log.info("[ADMIN] {} elimino la cola {}", user, name);
    }

    public PurgeResult purgeQueue(String name, String user) {
        requireQueue(name);
        int purged = amqpAdmin.purgeQueue(name);
        log.info("[ADMIN] {} vacio la cola {} ({} mensajes)", user, name, purged);
        return new PurgeResult(name, purged);
    }

    /** Lee hasta 'count' mensajes sin consumirlos (vuelven a la cola). */
    public List<PeekedMessage> peekMessages(String name, int count) {
        requireQueue(name);
        int n = Math.max(1, Math.min(count, props.maxBatch()));
        return management.peek(name, n).stream().map(RabbitAdminService::toPeeked).toList();
    }

    /** Devuelve mensajes de una DLQ a su cola de origen (tras corregir la causa del error). */
    public ReplayResult replayDeadLetters(String dlqName, int max, String user) {
        if (!dlqName.endsWith(props.deadLetterSuffix())) {
            throw new InvalidRequestException("Solo se pueden reprocesar DLQ (sufijo '" + props.deadLetterSuffix() + "')");
        }
        requireQueue(dlqName);
        ReplayResult result = replayer.replay(dlqName, Math.max(1, Math.min(max, props.maxBatch())));
        log.info("[ADMIN] {} reproceso {} mensaje(s) de {} -> {}", user, result.moved(), dlqName, result.movedTo());
        return result;
    }

    // ------------------------------------------------------------------ exchanges

    public List<ExchangeView> listExchanges(boolean includeSystem) {
        return management.exchanges().stream()
            .filter(e -> includeSystem || !(e.name().isEmpty() || e.name().startsWith("amq.")))
            .map(e -> new ExchangeView(e.name(), e.type(), e.durable(), e.internal(), props.isProtected(e.name())))
            .sorted(Comparator.comparing(ExchangeView::name))
            .toList();
    }

    public ExchangeView createExchange(CreateExchangeRequest req, String user) {
        management.exchange(req.name()).ifPresent(e -> {
            throw new ResourceConflictException("El exchange '" + req.name() + "' ya existe (tipo " + e.type() + ")");
        });
        amqpAdmin.declareExchange(new ExchangeBuilder(req.name(), req.type().value()).durable(req.isDurable()).build());
        log.info("[ADMIN] {} creo el exchange {} ({})", user, req.name(), req.type().value());
        return new ExchangeView(req.name(), req.type().value(), req.isDurable(), false, props.isProtected(req.name()));
    }

    public void deleteExchange(String name, String user) {
        rejectProtected(name, "el exchange");
        requireExchange(name);
        amqpAdmin.deleteExchange(name);
        log.info("[ADMIN] {} elimino el exchange {}", user, name);
    }

    // ------------------------------------------------------------------ bindings

    public List<BindingView> listBindings() {
        return management.bindings().stream()
            .filter(b -> !b.source().isEmpty() && "queue".equals(b.destinationType()))
            .map(b -> new BindingView(b.source(), b.destination(), b.routingKey()))
            .sorted(Comparator.comparing(BindingView::exchange).thenComparing(BindingView::queue))
            .toList();
    }

    public BindingView createBinding(CreateBindingRequest req, String user) {
        MgmtExchange exchange = requireExchange(req.exchange());
        requireQueue(req.queue());
        ExchangeType type;
        try {
            type = ExchangeType.from(exchange.type());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Tipo de exchange no soportado por la API: " + exchange.type());
        }
        String key = req.routingKey();
        if (type == ExchangeType.HEADERS) {
            throw new InvalidRequestException("Los exchanges headers se enlazan por argumentos, no por routing key");
        }
        if (!type.acceptsWildcards() && (key.contains("*") || key.contains("#"))) {
            throw new InvalidRequestException("Los comodines * y # solo funcionan en exchanges topic; '"
                + req.exchange() + "' es " + type.value());
        }
        if (type != ExchangeType.FANOUT && key.isEmpty()) {
            throw new InvalidRequestException("routingKey vacia: solo es valida en exchanges fanout");
        }
        if (bindingExists(req.exchange(), req.queue(), key)) {
            throw new ResourceConflictException("El binding " + req.exchange() + " -[" + key + "]-> " + req.queue() + " ya existe");
        }
        amqpAdmin.declareBinding(new Binding(req.queue(), Binding.DestinationType.QUEUE, req.exchange(), key, null));
        log.info("[ADMIN] {} creo el binding {} -[{}]-> {}", user, req.exchange(), key, req.queue());
        return new BindingView(req.exchange(), req.queue(), key);
    }

    public void deleteBinding(String exchange, String queue, String routingKey, String user) {
        if (props.isProtected(exchange) && props.isProtected(queue)) {
            throw new ResourceConflictException("El binding " + exchange + " -> " + queue
                + " es parte de los flujos del sistema y no se puede eliminar");
        }
        if (!bindingExists(exchange, queue, routingKey)) {
            throw new ResourceNotFoundException("No existe el binding " + exchange + " -[" + routingKey + "]-> " + queue);
        }
        amqpAdmin.removeBinding(new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null));
        log.info("[ADMIN] {} elimino el binding {} -[{}]-> {}", user, exchange, routingKey, queue);
    }

    // ------------------------------------------------------------------ mensajes

    public PublishResult publish(PublishMessageRequest req, String user) {
        if (!req.exchange().isEmpty()) requireExchange(req.exchange());
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> headers = new java.util.HashMap<>(req.headersOrEmpty());
        headers.put("x-published-by", user);
        Map<String, Object> properties = Map.of(
            "message_id", messageId,
            "content_type", "application/json",
            "delivery_mode", 2,
            "headers", headers);
        boolean routed = management.publish(req.exchange(), req.routingKey(), req.body(), properties);
        log.info("[ADMIN] {} publico mensaje {} en {} con key {} (routed={})",
            user, messageId, req.exchange().isEmpty() ? "(default)" : req.exchange(), req.routingKey(), routed);
        return new PublishResult(messageId, req.exchange(), req.routingKey(), routed,
            routed ? "Entregado al menos a una cola" : "Ninguna cola coincide con la routing key: el mensaje se descarto");
    }

    // ------------------------------------------------------------------ apoyo

    private MgmtQueue requireQueue(String name) {
        return management.queue(name).orElseThrow(() -> new ResourceNotFoundException("La cola '" + name + "' no existe"));
    }

    private MgmtExchange requireExchange(String name) {
        return management.exchange(name).orElseThrow(() -> new ResourceNotFoundException("El exchange '" + name + "' no existe"));
    }

    private boolean bindingExists(String exchange, String queue, String key) {
        return management.bindings(exchange, queue).stream().anyMatch(b -> key.equals(b.routingKey()));
    }

    private void rejectProtected(String name, String what) {
        if (props.isProtected(name)) {
            throw new ResourceConflictException("No se puede eliminar " + what + " '" + name
                + "': es parte de los flujos del sistema (prefijos protegidos " + props.protectedPrefixes() + ")");
        }
    }

    private QueueView toView(MgmtQueue q) {
        Map<String, Object> args = q.arguments() == null ? Map.of() : q.arguments();
        return new QueueView(q.name(), q.durable(), q.autoDelete(), q.type(), q.node(),
            q.messagesReady(), q.messagesUnacknowledged(), q.consumers(),
            str(args.get("x-dead-letter-exchange")), str(args.get("x-dead-letter-routing-key")),
            num(args.get("x-message-ttl")), num(args.get("x-max-length")),
            props.isProtected(q.name()), q.name().endsWith(props.deadLetterSuffix()));
    }

    @SuppressWarnings("unchecked")
    private static PeekedMessage toPeeked(MgmtMessage m) {
        Map<String, Object> properties = m.properties() == null ? Map.of() : m.properties();
        Map<String, Object> headers = properties.get("headers") instanceof Map<?, ?> h ? (Map<String, Object>) h : Map.of();
        String originalQueue = str(headers.get("x-first-death-queue"));
        String reason = str(headers.get("x-first-death-reason"));
        if (headers.get("x-death") instanceof List<?> deaths && !deaths.isEmpty() && deaths.get(0) instanceof Map<?, ?> d) {
            if (originalQueue == null) originalQueue = str(d.get("queue"));
            if (reason == null) reason = str(d.get("reason"));
        }
        Long retry = num(headers.get("x-retry-count"));
        return new PeekedMessage(str(properties.get("message_id")), m.exchange(), m.routingKey(), m.redelivered(),
            originalQueue, reason, retry == null ? null : retry.intValue(), str(headers.get("x-last-error")),
            headers, m.payload());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? null : Long.parseLong(o.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
