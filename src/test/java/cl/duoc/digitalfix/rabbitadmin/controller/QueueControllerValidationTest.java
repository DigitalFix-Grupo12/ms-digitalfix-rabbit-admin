package cl.duoc.digitalfix.rabbitadmin.controller;

import cl.duoc.digitalfix.rabbitadmin.dto.CreateQueueRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.Views.QueueView;
import cl.duoc.digitalfix.rabbitadmin.exception.ResourceConflictException;
import cl.duoc.digitalfix.rabbitadmin.exception.ResourceNotFoundException;
import cl.duoc.digitalfix.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {QueueController.class, ExchangeController.class, BindingController.class, MessageController.class})
class QueueControllerValidationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RabbitAdminService service;

    private static QueueView view(String name) {
        return new QueueView(name, true, false, "classic", "rabbit@rabbit1", 0, 0, 0, null, null, null, null, false, false);
    }

    @Test
    void creaColaValidaResponde201ConLocation() throws Exception {
        when(service.createQueue(any(CreateQueueRequest.class), eq("admin@test"))).thenReturn(view("q.demo"));
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Name", "admin@test")
                .content("{\"name\":\"q.demo\",\"withDeadLetterQueue\":true}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/rabbit/queues/q.demo"))
            .andExpect(jsonPath("$.name").value("q.demo"));
    }

    @Test
    void nombreVacioOConEspaciosOReservadoEs400() throws Exception {
        for (String name : new String[]{"", "  ", "con espacio", "amq.mia", "-inicio"}) {
            mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        }
        verifyNoInteractions(service);
    }

    @Test
    void ttlYMaxLengthFueraDeRangoSon400() throws Exception {
        mvc.perform(post("/api/rabbit/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"q.ok\",\"messageTtlMs\":-5,\"maxLength\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.messageTtlMs").exists())
            .andExpect(jsonPath("$.errors.maxLength").exists());
    }

    @Test
    void tipoDeExchangeInvalidoEs400() throws Exception {
        mvc.perform(post("/api/rabbit/exchanges").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ex.demo\",\"type\":\"broadcast\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("direct, topic, fanout, headers")));
    }

    @Test
    void bindingSinRoutingKeyEs400() throws Exception {
        mvc.perform(post("/api/rabbit/bindings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"exchange\":\"cmd.topic\",\"queue\":\"q.demo\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.routingKey").exists());
    }

    @Test
    void publicarConComodinEnRoutingKeyEs400() throws Exception {
        mvc.perform(post("/api/rabbit/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"exchange\":\"cmd.topic\",\"routingKey\":\"email.*\",\"body\":\"{}\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.routingKey").exists());
    }

    @Test
    void nombreInvalidoEnLaRutaEs400() throws Exception {
        mvc.perform(delete("/api/rabbit/queues/amq.gen-1"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void colaProtegidaEs409() throws Exception {
        doThrow(new ResourceConflictException("No se puede eliminar la cola 'q.cmd.email'"))
            .when(service).deleteQueue(eq("q.cmd.email"), anyBoolean(), anyBoolean(), anyString());
        mvc.perform(delete("/api/rabbit/queues/q.cmd.email"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("No se puede eliminar la cola 'q.cmd.email'"));
    }

    @Test
    void colaInexistenteEs404() throws Exception {
        when(service.getQueue("q.nada")).thenThrow(new ResourceNotFoundException("La cola 'q.nada' no existe"));
        mvc.perform(get("/api/rabbit/queues/q.nada"))
            .andExpect(status().isNotFound());
    }

    @Test
    void eliminarColaResponde204() throws Exception {
        mvc.perform(delete("/api/rabbit/queues/q.demo").param("ifEmpty", "true").header("X-User-Name", "admin@test"))
            .andExpect(status().isNoContent());
        verify(service).deleteQueue("q.demo", false, true, "admin@test");
    }

    @Test
    void peekConCountFueraDeRangoEs400() throws Exception {
        mvc.perform(get("/api/rabbit/queues/q.demo/messages").param("count", "500"))
            .andExpect(status().isBadRequest());
    }
}
