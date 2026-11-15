package com.tienda.pedidos.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Cuerpo uniforme de las respuestas de error")
public class ApiError {

    @Schema(description = "Codigo HTTP", example = "404")
    private final int status;

    @Schema(description = "Descripcion corta del codigo HTTP", example = "Not Found")
    private final String error;

    @Schema(description = "Detalle del error para el cliente", example = "Pedido con id 999 no encontrado.")
    private final String mensaje;

    @Schema(description = "Fecha y hora del error", example = "2026-11-14T10:15:30.123")
    private final String timestamp;

    @Schema(description = "Ruta solicitada", example = "/api/pedidos/999")
    private final String path;

    public ApiError(int status, String error, String mensaje, String path) {
        this.status = status;
        this.error = error;
        this.mensaje = mensaje;
        this.timestamp = LocalDateTime.now().toString();
        this.path = path;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getPath() {
        return path;
    }
}
