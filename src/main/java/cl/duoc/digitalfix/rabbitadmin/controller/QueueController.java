package cl.duoc.digitalfix.rabbitadmin.controller;

import cl.duoc.digitalfix.rabbitadmin.dto.CreateQueueRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.RabbitNames;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.PeekedMessage;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.PurgeResult;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.QueueView;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.ReplayResult;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Colas.
 * <pre>
 * GET    /api/rabbit/queues                      lista
 * GET    /api/rabbit/queues/{name}               detalle
 * POST   /api/rabbit/queues                      crea (201 + Location)
 * DELETE /api/rabbit/queues/{name}               elimina (204) ?ifUnused&amp;ifEmpty
 * DELETE /api/rabbit/queues/{name}/messages      vacia la cola (purge)
 * GET    /api/rabbit/queues/{name}/messages      lee sin consumir ?count=
 * POST   /api/rabbit/queues/{name}/replay        devuelve mensajes de una DLQ a su cola ?max=
 * </pre>
 */
@RestController
@RequestMapping("/api/rabbit/queues")
public class QueueController {

    private final RabbitAdminService service;

    public QueueController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<QueueView> list() {
        return service.listQueues();
    }

    @GetMapping("/{name}")
    public QueueView get(@PathVariable @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name) {
        return service.getQueue(name);
    }

    @PostMapping
    public ResponseEntity<QueueView> create(@Valid @RequestBody CreateQueueRequest request,
                                            @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        QueueView created = service.createQueue(request, user);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{name}").buildAndExpand(created.name()).toUri()).body(created);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
            @RequestParam(defaultValue = "false") boolean ifUnused,
            @RequestParam(defaultValue = "false") boolean ifEmpty,
            @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        service.deleteQueue(name, ifUnused, ifEmpty, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{name}/messages")
    public PurgeResult purge(
            @PathVariable @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
            @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        return service.purgeQueue(name, user);
    }

    @GetMapping("/{name}/messages")
    public List<PeekedMessage> peek(
            @PathVariable @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "minimo 1") @Max(value = 50, message = "maximo 50") int count) {
        return service.peekMessages(name, count);
    }

    @PostMapping("/{name}/replay")
    public ReplayResult replay(
            @PathVariable @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "minimo 1") @Max(value = 50, message = "maximo 50") int max,
            @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        return service.replayDeadLetters(name, max, user);
    }
}
