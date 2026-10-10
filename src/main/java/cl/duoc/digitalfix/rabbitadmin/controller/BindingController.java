package cl.duoc.digitalfix.rabbitadmin.controller;

import cl.duoc.digitalfix.rabbitadmin.dto.CreateBindingRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.RabbitNames;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.BindingView;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bindings (exchange -> cola). Un binding se identifica por la terna
 * exchange + cola + routing key, por eso DELETE usa parametros de consulta.
 * <pre>
 * GET    /api/rabbit/bindings
 * POST   /api/rabbit/bindings                                   (201)
 * DELETE /api/rabbit/bindings?exchange=&amp;queue=&amp;routingKey=   (204)
 * </pre>
 */
@RestController
@RequestMapping("/api/rabbit/bindings")
public class BindingController {

    private final RabbitAdminService service;

    public BindingController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<BindingView> list() {
        return service.listBindings();
    }

    @PostMapping
    public ResponseEntity<BindingView> create(@Valid @RequestBody CreateBindingRequest request,
                                              @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBinding(request, user));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestParam @NotBlank @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String exchange,
            @RequestParam @NotBlank @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String queue,
            @RequestParam(defaultValue = "") @NotNull
            @Pattern(regexp = RabbitNames.BINDING_KEY_REGEX, message = RabbitNames.BINDING_KEY_MESSAGE) String routingKey,
            @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        service.deleteBinding(exchange, queue, routingKey, user);
        return ResponseEntity.noContent().build();
    }
}
