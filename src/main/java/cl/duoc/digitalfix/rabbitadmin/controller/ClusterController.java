package cl.duoc.digitalfix.rabbitadmin.controller;

import cl.duoc.digitalfix.rabbitadmin.dto.Views.ClusterOverview;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/rabbit/overview: estado del cluster (nodos, totales de colas y mensajes). */
@RestController
@RequestMapping("/api/rabbit")
public class ClusterController {

    private final RabbitAdminService service;

    public ClusterController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ClusterOverview overview() {
        return service.overview();
    }
}
