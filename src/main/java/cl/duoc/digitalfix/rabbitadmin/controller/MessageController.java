package cl.duoc.digitalfix.rabbitadmin.controller;

import cl.duoc.digitalfix.rabbitadmin.dto.PublishMessageRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.PublishResult;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/rabbit/messages: publica un mensaje de prueba (202 + indicador routed). */
@RestController
@RequestMapping("/api/rabbit/messages")
public class MessageController {

    private final RabbitAdminService service;

    public MessageController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PublishResult> publish(@Valid @RequestBody PublishMessageRequest request,
                                                 @RequestHeader(value = AdminHeaders.USER, defaultValue = AdminHeaders.UNKNOWN) String user) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.publish(request, user));
    }
}
