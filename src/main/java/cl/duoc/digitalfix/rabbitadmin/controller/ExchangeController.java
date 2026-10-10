package cl.duoc.digitalfix.rabbitadmin.controller;

import cl.duoc.digitalfix.rabbitadmin.dto.CreateExchangeRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.RabbitNames;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.ExchangeView;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
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
 * Exchanges.
 * <pre>
 * GET    /api/rabbit/exchanges            lista (?includeSystem=true incluye amq.*)
 * POST   /api/rabbit/exchanges            crea (201)
 * DELETE /api/rabbit/exchanges/{name}     elimina (204)
 * </pre>
 */
@RestController
@RequestMapping("/api/rabbit/exchanges")
public class ExchangeController {

    private final RabbitAdminService service;

    public ExchangeController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExchangeView> list(@RequestParam(defaultValue = "false") boolean includeSystem) {
        return service.listExchanges(includeSystem);
    }

    @PostMapping
    public ResponseEntity<ExchangeView> create(@Valid @RequestBody CreateExchangeRequest request,
                                               @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        ExchangeView created = service.createExchange(request, user);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{name}").buildAndExpand(created.name()).toUri()).body(created);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = RabbitNames.NAME_REGEX, message = RabbitNames.NAME_MESSAGE) String name,
            @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        service.deleteExchange(name, user);
        return ResponseEntity.noContent().build();
    }
}
