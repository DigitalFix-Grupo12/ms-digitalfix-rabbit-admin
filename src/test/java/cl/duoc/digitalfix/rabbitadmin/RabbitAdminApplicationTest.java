package cl.duoc.digitalfix.rabbitadmin;

import cl.duoc.digitalfix.rabbitadmin.config.RabbitAdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** El contexto levanta sin broker y la configuracion centralizada se lee del application.yml. */
@SpringBootTest
@ActiveProfiles("test")
class RabbitAdminApplicationTest {

    @Autowired
    private RabbitAdminProperties props;

    @Test
    void cargaConfiguracionDelCluster() {
        assertThat(props.managementUrls()).hasSize(2);
        assertThat(props.isProtected("q.cmd.email")).isTrue();
        assertThat(props.isProtected("q.demo")).isFalse();
    }
}
