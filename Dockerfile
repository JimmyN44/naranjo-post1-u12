# =====================================================================
# Dockerfile multi-stage del proyecto "pedidos" (Spring Boot 3.2 / Java 17).
# El codigo vive en la subcarpeta pedidos/; el contexto de build es la raiz
# del repositorio:   docker build -t mi-spring-app:local .
# =====================================================================

# ── Etapa 1: compilacion con Maven + JDK 17 ─────────────────────────
# La imagen oficial de Maven ya trae JDK 17 (eclipse-temurin) y Maven 3.9.11,
# la misma version que fija el Maven Wrapper del proyecto. La imagen
# eclipse-temurin:*-jdk-alpine NO incluye Maven, por eso no se usa aqui.
FROM maven:3.9.11-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copiar solo pom.xml primero: mientras las dependencias no cambien, Docker
# reutiliza esta capa y no vuelve a descargarlas en builds siguientes.
COPY pedidos/pom.xml .
RUN mvn -B -q dependency:go-offline

# Copiar el codigo fuente y generar el JAR ejecutable (sin pruebas: las
# pruebas se ejecutan en el job build-and-test del pipeline de CI).
COPY pedidos/src ./src
RUN mvn -B -q clean package -DskipTests \
    && cp target/*.jar /build/app.jar
# El glob target/*.jar solo coincide con el JAR repackaged de Spring Boot
# (el original queda como *.jar.original); si hubiera mas de uno, cp falla
# y el build se detiene en lugar de copiar un JAR equivocado.

# ── Etapa 2: imagen de ejecucion (solo JRE 17, sin Maven ni JDK) ─────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Usuario y grupo no root (sintaxis de BusyBox/Alpine).
# logback-spring.xml escribe en logs/pedidos.log (relativo a /app), por eso
# se crea /app/logs con permisos para ese usuario; el resto de /app y el JAR
# quedan como propiedad de root, de solo lectura para la aplicacion.
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/logs && chown spring:spring /app/logs

# Copiar unicamente el JAR generado en la etapa builder.
COPY --from=builder /build/app.jar app.jar

USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
