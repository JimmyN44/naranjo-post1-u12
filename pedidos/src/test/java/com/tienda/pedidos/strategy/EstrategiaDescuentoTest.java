package com.tienda.pedidos.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstrategiaDescuentoTest {

    private static final double SUBTOTAL = 100000.0;

    @Test
    void regularNoAplicaDescuento() {
        assertThat(new DescuentoRegular().calcularDescuento(SUBTOTAL)).isEqualTo(0.0);
    }

    @Test
    void vipAplica15PorCiento() {
        assertThat(new DescuentoVip().calcularDescuento(SUBTOTAL)).isEqualTo(15000.0);
    }

    @Test
    void mayoristaAplica25PorCiento() {
        assertThat(new DescuentoMayorista().calcularDescuento(SUBTOTAL)).isEqualTo(25000.0);
    }
    
}
