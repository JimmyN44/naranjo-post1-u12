package com.tienda.pedidos;

import com.tienda.pedidos.controller.PedidoController;
import com.tienda.pedidos.repository.PedidoRepository;
import com.tienda.pedidos.service.PedidoService;
import com.tienda.pedidos.service.PedidoServiceImpl;
import com.tienda.pedidos.strategy.EstrategiaDescuento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de contexto con el perfil dev: verifica que Spring construye el contexto
 * completo de la aplicacion (capas, base de datos H2 y Actuator) sin necesitar
 * ninguna base de datos externa ni variables de entorno. Es la prueba que garantiza
 * que el job de CI valida algo real antes de construir la imagen Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextoSeConstruyeConElPerfilDev() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev");
        // Propiedades que solo existen en application-dev.properties
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("*");
    }

    @Test
    void contextoRegistraLasCapasDeLaAplicacion() {
        assertThat(context.getBean(PedidoController.class)).isNotNull();
        assertThat(context.getBean(PedidoService.class)).isInstanceOf(PedidoServiceImpl.class);
        assertThat(context.getBean(PedidoRepository.class)).isNotNull();
        assertThat(context.getBeansOfType(EstrategiaDescuento.class))
                .containsOnlyKeys("REGULAR", "VIP", "MAYORISTA");
    }

    @Test
    void perfilDevUsaBaseDeDatosH2EnMemoria() throws Exception {
        try (Connection conexion = dataSource.getConnection()) {
            assertThat(conexion.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(conexion.getMetaData().getURL()).isEqualTo("jdbc:h2:mem:pedidosdb");
        }
    }

    @Test
    void actuatorHealthRespondeUpConLaBaseDeDatosDisponible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.database").value("H2"));
    }
}
