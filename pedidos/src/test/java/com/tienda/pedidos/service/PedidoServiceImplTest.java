package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.PedidoRequestDTO;
import com.tienda.pedidos.dto.PedidoResponseDTO;
import com.tienda.pedidos.entity.Pedido;
import com.tienda.pedidos.exception.RecursoNoEncontradoException;
import com.tienda.pedidos.repository.PedidoRepository;
import com.tienda.pedidos.strategy.DescuentoMayorista;
import com.tienda.pedidos.strategy.DescuentoRegular;
import com.tienda.pedidos.strategy.DescuentoVip;
import com.tienda.pedidos.strategy.EstrategiaDescuento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoServiceImplTest {

    private PedidoRepository repo;
    private NotificacionService notificador;
    private PedidoServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        notificador = mock(NotificacionService.class);
        Map<String, EstrategiaDescuento> estrategias = Map.of(
                "REGULAR", new DescuentoRegular(),
                "VIP", new DescuentoVip(),
                "MAYORISTA", new DescuentoMayorista());
        service = new PedidoServiceImpl(repo, notificador, estrategias);

        when(repo.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @ParameterizedTest
    @CsvSource({
            "REGULAR,   0.0,     19000.0, 119000.0",
            "VIP,       15000.0, 16150.0, 101150.0",
            "MAYORISTA, 25000.0, 14250.0, 89250.0",
            "vip,       15000.0, 16150.0, 101150.0"
    })
    void crearCalculaDescuentoIvaYTotalSegunTipoCliente(String tipo, double descuento,
                                                       double iva, double total) {
        PedidoResponseDTO resp = service.crear(request(tipo, 100000.0));

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getTipoCliente()).isEqualTo(tipo.toUpperCase());
        assertThat(resp.getDescuento()).isEqualTo(descuento);
        assertThat(resp.getIva()).isEqualTo(iva);
        assertThat(resp.getTotal()).isEqualTo(total);
        assertThat(resp.getEstado()).isEqualTo("CREADO");
        verify(notificador).enviarConfirmacion("ana@correo.com", total);
    }

    @Test
    void crearPersisteNotasInternasEnLaEntidad() {
        service.crear(request("VIP", 100000.0));

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getNotasInternas()).isNotBlank();
        assertThat(captor.getValue().getFechaCreacion()).isNotNull();
    }

    @Test
    void tipoClienteDesconocidoUsaEstrategiaRegular() {
        PedidoResponseDTO resp = service.crear(request("ORO", 100000.0));

        assertThat(resp.getDescuento()).isEqualTo(0.0);
        assertThat(resp.getTotal()).isEqualTo(119000.0);
    }

    @Test
    void buscarPorIdInexistenteLanzaRecursoNoEncontrado() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Pedido con id 999 no encontrado.");
    }

    private PedidoRequestDTO request(String tipo, double subtotal) {
        PedidoRequestDTO dto = new PedidoRequestDTO();
        dto.setClienteEmail("ana@correo.com");
        dto.setTipoCliente(tipo);
        dto.setSubtotal(subtotal);
        return dto;
    }
}
