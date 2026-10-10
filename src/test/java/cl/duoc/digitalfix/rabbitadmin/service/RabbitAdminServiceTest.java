package cl.duoc.digitalfix.rabbitadmin.service;

import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtBinding;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtExchange;
import cl.duoc.digitalfix.rabbitadmin.client.ManagementModels.MgmtQueue;
import cl.duoc.digitalfix.rabbitadmin.client.RabbitManagementClient;
import cl.duoc.digitalfix.rabbitadmin.config.RabbitAdminProperties;
import cl.duoc.digitalfix.rabbitadmin.dto.CreateBindingRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.CreateQueueRequest;
import cl.duoc.digitalfix.rabbitadmin.dto.PublishMessageRequest;
import cl.duoc.digitalfix.rabbitadmin.exception.InvalidRequestException;
import cl.duoc.digitalfix.rabbitadmin.exception.ResourceConflictException;
import cl.duoc.digitalfix.rabbitadmin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitManagementClient mgmt;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        mgmt = mock(RabbitManagementClient.class);
        RabbitAdminProperties props = new RabbitAdminProperties("/", List.of("http://x"), "u", "p",
            "cmd.dead.dlx", ".dlq", List.of("cmd.", "q.cmd."), 50);
        service = new RabbitAdminService(amqpAdmin, mgmt, mock(DeadLetterReplayer.class), props);
    }

    private static MgmtQueue queue(String name, int consumers, long messages) {
        return new MgmtQueue(name, true, false, Map.of(), "rabbit@rabbit1", "classic", "running",
            messages, messages, 0, consumers);
    }

    @Test
    void crearColaConDlqDeclaraDlqBindingYArgumentos() {
        when(mgmt.queue("q.demo")).thenReturn(Optional.empty(), Optional.of(queue("q.demo", 0, 0)));
        when(mgmt.queue("q.demo.dlq")).thenReturn(Optional.empty());

        service.createQueue(new CreateQueueRequest("q.demo", true, false, 60000L, null, true), "admin");

        ArgumentCaptor<Queue> queues = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin, times(2)).declareQueue(queues.capture());
        assertThat(queues.getAllValues()).extracting(Queue::getName).containsExactly("q.demo.dlq", "q.demo");
        assertThat(queues.getAllValues().get(1).getArguments())
            .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
            .containsEntry("x-dead-letter-routing-key", "q.demo.dlq")
            .containsEntry("x-message-ttl", 60000);
        verify(amqpAdmin).declareBinding(any(Binding.class));
    }

    @Test
    void colaExistenteEsConflicto() {
        when(mgmt.queue("q.demo")).thenReturn(Optional.of(queue("q.demo", 0, 0)));
        assertThatThrownBy(() -> service.createQueue(new CreateQueueRequest("q.demo", null, null, null, null, null), "a"))
            .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void dlqNoPuedeTenerSuPropiaDlq() {
        assertThatThrownBy(() -> service.createQueue(new CreateQueueRequest("x.dlq", null, null, null, null, true), "a"))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void noSeEliminanColasDelSistema() {
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.email", false, false, "a"))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("protegidos");
        verify(amqpAdmin, never()).deleteQueue(anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void eliminarSoloSiVaciaRespetaLosMensajesPendientes() {
        when(mgmt.queue("q.demo")).thenReturn(Optional.of(queue("q.demo", 0, 3)));
        assertThatThrownBy(() -> service.deleteQueue("q.demo", false, true, "a"))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("3 mensaje");
    }

    @Test
    void comodinEnExchangeDirectEsInvalido() {
        when(mgmt.exchange("cmd.direct")).thenReturn(Optional.of(new MgmtExchange("cmd.direct", "direct", true, false, false)));
        when(mgmt.queue("q.demo")).thenReturn(Optional.of(queue("q.demo", 0, 0)));
        assertThatThrownBy(() -> service.createBinding(new CreateBindingRequest("cmd.direct", "q.demo", "email.*"), "a"))
            .isInstanceOf(InvalidRequestException.class).hasMessageContaining("topic");
    }

    @Test
    void bindingDuplicadoEsConflictoYExchangeInexistenteEs404() {
        when(mgmt.exchange("cmd.topic")).thenReturn(Optional.of(new MgmtExchange("cmd.topic", "topic", true, false, false)));
        when(mgmt.queue("q.demo")).thenReturn(Optional.of(queue("q.demo", 0, 0)));
        when(mgmt.bindings("cmd.topic", "q.demo")).thenReturn(List.of(new MgmtBinding("cmd.topic", "q.demo", "queue", "demo.#", "demo.#")));
        assertThatThrownBy(() -> service.createBinding(new CreateBindingRequest("cmd.topic", "q.demo", "demo.#"), "a"))
            .isInstanceOf(ResourceConflictException.class);

        when(mgmt.exchange("no.existe")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createBinding(new CreateBindingRequest("no.existe", "q.demo", "k"), "a"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void publicarInformaSiElMensajeNoTuvoColaDestino() {
        when(mgmt.exchange("cmd.topic")).thenReturn(Optional.of(new MgmtExchange("cmd.topic", "topic", true, false, false)));
        when(mgmt.publish(eq("cmd.topic"), eq("nadie.escucha"), anyString(), anyMap())).thenReturn(false);
        var result = service.publish(new PublishMessageRequest("cmd.topic", "nadie.escucha", "{}", null), "admin");
        assertThat(result.routed()).isFalse();
        assertThat(result.note()).contains("Ninguna cola");
    }

    @Test
    void soloSeReprocesanDlq() {
        assertThatThrownBy(() -> service.replayDeadLetters("q.cmd.email", 5, "a"))
            .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void origenDeUnMensajeMuertoSeLeeDeLosHeaders() {
        assertThat(DeadLetterReplayer.originalQueue(Map.of("x-first-death-queue", "q.cmd.email"))).isEqualTo("q.cmd.email");
        assertThat(DeadLetterReplayer.originalQueue(Map.of("x-death", List.of(Map.of("queue", "q.cmd.report"))))).isEqualTo("q.cmd.report");
        assertThat(DeadLetterReplayer.originalQueue(Map.of())).isNull();
    }
}
