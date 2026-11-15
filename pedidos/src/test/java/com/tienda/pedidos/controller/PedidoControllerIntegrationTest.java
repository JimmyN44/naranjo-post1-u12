package com.tienda.pedidos.controller;

import com.tienda.pedidos.strategy.EstrategiaDescuento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Map<String, EstrategiaDescuento> estrategias;

    @Test
    void springRegistraLasTresEstrategiasConSuNombre() {
        assertThat(estrategias).containsOnlyKeys("REGULAR", "VIP", "MAYORISTA");
    }

    @ParameterizedTest
    @CsvSource({
            "REGULAR,   0.0,     19000.0, 119000.0",
            "VIP,       15000.0, 16150.0, 101150.0",
            "MAYORISTA, 25000.0, 14250.0, 89250.0"
    })
    void postCreaPedidoConDescuentoSegunTipoClienteSinNotasInternas(String tipo, double descuento,
                                                                   double iva, double total) throws Exception {
        String body = """
                {"clienteEmail":"ana@correo.com","tipoCliente":"%s","subtotal":100000}
                """.formatted(tipo);

        mockMvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.tipoCliente").value(tipo))
                .andExpect(jsonPath("$.descuento").value(descuento))
                .andExpect(jsonPath("$.iva").value(iva))
                .andExpect(jsonPath("$.total").value(total))
                .andExpect(jsonPath("$.estado").value("CREADO"))
                .andExpect(jsonPath("$.notasInternas").doesNotExist());
    }

    @Test
    void getDevuelveElPedidoCreadoSinNotasInternas() throws Exception {
        MvcResult creado = mockMvc.perform(post("/api/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteEmail\":\"ana@correo.com\",\"tipoCliente\":\"VIP\",\"subtotal\":100000}"))
                .andExpect(status().isCreated())
                .andReturn();
        String json = creado.getResponse().getContentAsString();
        String id = json.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/api/pedidos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(id)))
                .andExpect(jsonPath("$.total").value(101150.0))
                .andExpect(jsonPath("$.notasInternas").doesNotExist());
    }

    @Test
    void postConBodyVacioRetorna400ConErroresDeValidacion() throws Exception {
        mockMvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.mensaje", containsString("clienteEmail: El email del cliente es obligatorio")))
                .andExpect(jsonPath("$.mensaje", containsString("tipoCliente: El tipo de cliente es obligatorio")))
                .andExpect(jsonPath("$.mensaje", containsString("subtotal: El subtotal es obligatorio")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/pedidos"));
    }

    @Test
    void getPedidoInexistenteRetorna404ConApiError() throws Exception {
        mockMvc.perform(get("/api/pedidos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.mensaje").value("Pedido con id 999 no encontrado."))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.path").value("/api/pedidos/999"));
    }

    @Test
    void jsonMalFormadoRetorna400() throws Exception {
        mockMvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("{\"clienteEmail\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la solicitud no es un JSON valido"));
    }

    @Test
    void idNoNumericoRetorna400() throws Exception {
        mockMvc.perform(get("/api/pedidos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El parametro 'id' tiene un formato invalido"));
    }

    @Test
    void metodoNoSoportadoConservaSu405() throws Exception {
        mockMvc.perform(delete("/api/pedidos/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
