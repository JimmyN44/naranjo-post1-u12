# Post-contenido — Unidad 12: Despliegue y CI/CD

Programación Web — Unidad 12 · Laboratorio post-contenido

## 1. Descripción

Repositorio del laboratorio de la Unidad 12 de Programación Web. Contiene la aplicación Spring Boot **`pedidos`** desarrollada en la Unidad 11 —una API REST de gestión de pedidos de una tienda en línea, con paquete raíz `com.tienda.pedidos`— llevada a un flujo real de contenedorización, integración continua y despliegue:

- **Parte 1 — Dockerización con perfiles y variables de entorno:** Dockerfile multi-stage, configuración externalizada con placeholders `${VAR:default}` y separación de los ambientes `dev` y `prod` mediante perfiles de Spring activados por variable de entorno.
- **Parte 2 — Pipeline CI/CD con GitHub Actions y monitoreo con Actuator** *(pendiente; se documentará en este README al completarla)*: Spring Boot Actuator con exposición diferenciada por perfil, prueba automatizada de contexto, workflow de GitHub Actions que compila, prueba, construye la imagen y verifica con un smoke test que el contenedor responde `UP`, y despliegue manual en una plataforma cloud a partir del mismo Dockerfile.

La lógica de negocio de la Unidad 11 se conserva sin cambios: arquitectura por capas, DTOs, patrón Strategy para el descuento por tipo de cliente, manejo centralizado de errores, logging con SLF4J/Logback y documentación con Swagger UI.

## 2. Objetivo del laboratorio

1. Dockerizar la aplicación existente con un Dockerfile multi-stage y un usuario no root.
2. Externalizar la configuración sensible y separar desarrollo de producción con perfiles, de modo que la **misma imagen** arranque sin configuración en local (`dev`, H2) y con variables de entorno en producción (`prod`, PostgreSQL).
3. Exponer un endpoint de monitoreo (`/actuator/health`) sin publicar información sensible en producción.
4. Automatizar en cada push y pull request a `main` la compilación, las pruebas, la construcción de la imagen y la verificación de que el contenedor recién construido arranca y responde saludable.
5. Desplegar la aplicación en Railway o Render a partir del mismo Dockerfile.

## 3. Tecnologías y versiones

Versiones tomadas del `pom.xml`, del JAR construido y de las imágenes base.

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 17 (`<java.version>17</java.version>`) | Lenguaje |
| Spring Boot | 3.2.12 | Framework base |
| Spring Web MVC / Tomcat embebido | 6.1.15 / 10.1.33 | API REST |
| Spring Data JPA + Hibernate | Hibernate 6.4.10.Final | Persistencia |
| H2 Database | 2.2.224 | Base de datos en memoria (perfil `dev` y pruebas) |
| Driver PostgreSQL | 42.6.2 | Base de datos externa (perfil `prod`) |
| springdoc-openapi-starter-webmvc-ui | 2.3.0 | OpenAPI 3 y Swagger UI |
| JUnit 5, Mockito, MockMvc, AssertJ | (spring-boot-starter-test) | Pruebas automatizadas |
| Maven / Maven Wrapper | 3.9.11 / 3.3.4 | Construcción |
| Imagen de compilación | `maven:3.9.11-eclipse-temurin-17-alpine` | Etapa *builder* del Dockerfile |
| Imagen de ejecución | `eclipse-temurin:17-jre-alpine` | Etapa final del Dockerfile |

## 4. Arquitectura del proyecto

```
naranjo-post1-u12/
├── README.md
├── Dockerfile                    # build multi-stage (Maven + JDK 17 → JRE 17 Alpine)
├── .dockerignore
├── .gitignore
├── capturas/                     # evidencias del laboratorio (ver sección de capturas)
└── pedidos/                      # proyecto Spring Boot (Unidad 11)
    ├── pom.xml
    ├── mvnw, mvnw.cmd, .mvn/
    └── src/
        ├── main/
        │   ├── java/com/tienda/pedidos/
        │   │   ├── PedidosApplication.java
        │   │   ├── controller/   PedidoController
        │   │   ├── dto/          PedidoRequestDTO, PedidoResponseDTO
        │   │   ├── entity/       Pedido (entidad JPA)
        │   │   ├── exception/    ApiError, GlobalExceptionHandler, RecursoNoEncontradoException
        │   │   ├── repository/   PedidoRepository (Spring Data JPA)
        │   │   ├── service/      PedidoService(+Impl), NotificacionService(+EmailServiceImpl)
        │   │   └── strategy/     EstrategiaDescuento, DescuentoRegular, DescuentoVip, DescuentoMayorista
        │   └── resources/
        │       ├── application.properties        # base común
        │       ├── application-dev.properties    # perfil dev
        │       ├── application-prod.properties   # perfil prod
        │       └── logback-spring.xml
        └── test/java/com/tienda/pedidos/         # pruebas de la Unidad 11 (JUnit 5, Mockito, MockMvc)
```

El proyecto Maven vive en la subcarpeta `pedidos/`; el `Dockerfile` está en la raíz y el contexto de build es la raíz del repositorio.

### Endpoints de la aplicación

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/pedidos` | Crea un pedido y calcula descuento (REGULAR 0 %, VIP 15 %, MAYORISTA 25 %) e IVA del 19 % | `201`, `400`, `500` |
| `GET` | `/api/pedidos/{id}` | Consulta un pedido por id (nunca expone `notasInternas`) | `200`, `400`, `404`, `500` |
| `GET` | `/swagger-ui.html` | Documentación interactiva (Swagger UI) | `200` |
| `GET` | `/api-docs` | Contrato OpenAPI 3 en JSON | `200` |
| `GET` | `/h2-console` | Consola de H2 (**solo perfil `dev`**) | `200` en dev, `404` en prod |

Cuerpo de ejemplo para `POST /api/pedidos`:

```json
{ "clienteEmail": "ana@correo.com", "tipoCliente": "VIP", "subtotal": 100000 }
```

---

# Parte 1 — Dockerización con perfiles y variables de entorno

## 5. Parte 1: Dockerización

La aplicación se empaqueta en una imagen Docker construida con un Dockerfile multi-stage (sección 8). La configuración sensible se externaliza con placeholders `${VARIABLE:valor-por-defecto}`: si la variable de entorno existe, Spring la usa; si no, usa el valor por defecto. Así la misma imagen:

- arranca **sin ninguna variable** con el perfil `dev` y H2 en memoria, y
- arranca en producción con `SPRING_PROFILES_ACTIVE=prod` y la conexión a PostgreSQL recibida por variables de entorno.

## 6. Perfiles `dev` y `prod`

`application.properties` contiene solo lo común y decide el perfil:

```properties
server.port=${PORT:8080}
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}
```

| Aspecto | `dev` (por defecto) | `prod` |
|---|---|---|
| Base de datos | H2 en memoria `jdbc:h2:mem:pedidosdb` | PostgreSQL externo |
| Credenciales | Por defecto `sa` / vacía | **Obligatorias** por variable de entorno, sin valor por defecto |
| `ddl-auto` | `create-drop` (esquema nuevo en cada arranque) | `validate` por defecto (configurable con `JPA_DDL_AUTO`) |
| SQL en el log (`show-sql`) | Sí | No |
| Consola H2 (`/h2-console`) | Activa | Desactivada |
| Logging | `com.tienda.pedidos=DEBUG` | `root=WARN`, `com.tienda.pedidos=INFO` |
| Actuator *(se agrega en la Parte 2)* | Todos los endpoints, `show-details=always` | Solo `health` e `info`, `show-details=when-authorized` |

## 7. Variables de entorno

| Variable | Finalidad | Valor por defecto en `dev` | En `prod` |
|---|---|---|---|
| `PORT` | Puerto HTTP. Railway/Render pueden inyectarla | `8080` | La define la plataforma (o `8080`) |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `dev` | `prod` |
| `DATASOURCE_URL` | URL **JDBC** de la base de datos | `jdbc:h2:mem:pedidosdb` | `jdbc:postgresql://<host>:<puerto>/<bd>` |
| `DATASOURCE_USER` | Usuario de la base de datos | `sa` | **Obligatoria** |
| `DATASOURCE_PASSWORD` | Contraseña de la base de datos | *(vacía)* | **Obligatoria** |
| `DATASOURCE_DRIVER` | Clase del driver JDBC (solo `dev`) | `org.h2.Driver` | No se usa (fijo `org.postgresql.Driver`) |
| `JPA_DDL_AUTO` | Estrategia de esquema de Hibernate (solo `prod`) | No se usa (`create-drop`) | `validate`; `update` **solo en el primer despliegue** sobre una base vacía |

Notas:

- El proyecto no usa autenticación JWT, por lo que no requiere `JWT_SECRET`.
- Ninguna credencial real está escrita en el repositorio. Si en `prod` faltan `DATASOURCE_USER` o `DATASOURCE_PASSWORD`, la base de datos rechaza la conexión y la aplicación no arranca: nunca usa una credencial por defecto.
- `DATASOURCE_URL` debe tener formato **JDBC** (`jdbc:postgresql://...`). Las URL `postgresql://usuario:clave@host/bd` que entregan Railway (`DATABASE_URL`) o Render (*Internal Database URL*) no sirven directamente.
- El proyecto no usa Flyway ni Liquibase: con `validate` sobre una base **vacía** la aplicación no arranca (`Schema-validation: missing table [pedido]`). El primer arranque se hace con `JPA_DDL_AUTO=update` y luego se elimina esa variable.
- Si falta `SPRING_PROFILES_ACTIVE=prod`, la aplicación arranca sin errores con el perfil `dev` y H2; por eso siempre se verifica en los logs la línea `The following 1 profile is active: "prod"`.

## 8. Dockerfile multi-stage

```dockerfile
# Etapa 1: compilación con Maven + JDK 17
FROM maven:3.9.11-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pedidos/pom.xml .
RUN mvn -B -q dependency:go-offline          # capa cacheada mientras no cambie el pom
COPY pedidos/src ./src
RUN mvn -B -q clean package -DskipTests \
    && cp target/*.jar /build/app.jar

# Etapa 2: ejecución (solo JRE 17)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring \
    && mkdir -p /app/logs && chown spring:spring /app/logs
COPY --from=builder /build/app.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

*(Extracto sin comentarios; el archivo completo está en [`Dockerfile`](Dockerfile).)*

- **Etapa builder:** usa la imagen oficial de Maven con JDK 17 (la imagen `eclipse-temurin:*-jdk-alpine` no trae Maven) y la misma versión de Maven que el wrapper del proyecto (3.9.11).
- **Caché de capas:** `pom.xml` se copia y las dependencias se descargan **antes** de copiar `src/`; un cambio solo en el código reutiliza la capa de dependencias y únicamente repite la compilación.
- **Un solo JAR:** `target/*.jar` coincide solo con el JAR ejecutable de Spring Boot (el original queda como `*.jar.original`); si hubiera más de uno, `cp` falla y el build se detiene.
- **Etapa final:** solo JRE 17 (sin `javac` ni Maven), usuario no root `spring`, JAR de solo lectura y únicamente `/app/logs` escribible (lo usa `logback-spring.xml`).
- **`.dockerignore`:** excluye `target/`, `.git/`, `.github/`, archivos de IDE, logs, el Maven Wrapper, archivos sensibles locales (`.env*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `application-local*.properties`), temporales, documentación y capturas. El contexto enviado al build contiene solo `pedidos/pom.xml` y `pedidos/src/`.

## 9. Construcción y ejecución local

### Compilar y probar sin Docker (requiere JDK 17+)

```bash
cd pedidos
./mvnw clean verify                              # compila, ejecuta las pruebas y empaqueta
java -jar target/pedidos-0.0.1-SNAPSHOT.jar      # perfil dev en http://localhost:8080
```

En Windows se usa `mvnw.cmd` en lugar de `./mvnw`.

### Construir la imagen (desde la raíz del repositorio)

```bash
docker build -t mi-spring-app:local .
docker images mi-spring-app
```

En Docker reciente con el almacén de imágenes de containerd, la columna `DISK USAGE` de `docker images` suma las capas descomprimidas y los blobs comprimidos, por lo que muestra una cifra mayor que el tamaño de las capas de la imagen; ese tamaño se consulta con `docker history mi-spring-app:local`.

### Ejecutar con el perfil `dev` (sin variables)

```bash
docker run -d --name mi-app-dev -p 8080:8080 mi-spring-app:local
docker logs mi-app-dev | grep "profile is active"     # "dev"
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{"clienteEmail":"ana@correo.com","tipoCliente":"VIP","subtotal":100000}'
docker stop mi-app-dev && docker rm mi-app-dev
```

### Ejecutar con el perfil `prod` (PostgreSQL local de prueba)

En Linux `host.docker.internal` no resuelve sin configuración adicional, por eso se usa una red de Docker. Los valores entre `<...>` se reemplazan por valores propios y no se versionan.

```bash
docker network create red-u12

docker run -d --name pg-u12 --network red-u12 \
  -e POSTGRES_DB=pedidosdb -e POSTGRES_USER=pedidos_app -e POSTGRES_PASSWORD=<clave-local> \
  postgres:16-alpine

# Primer arranque sobre la base vacía: JPA_DDL_AUTO=update crea la tabla
docker run -d --name mi-app-prod --network red-u12 -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATASOURCE_URL=jdbc:postgresql://pg-u12:5432/pedidosdb \
  -e DATASOURCE_USER=pedidos_app \
  -e DATASOURCE_PASSWORD=<clave-local> \
  -e JPA_DDL_AUTO=update \
  mi-spring-app:local

docker logs -f mi-app-prod        # "The following 1 profile is active: "prod""

# Limpieza
docker stop mi-app-prod pg-u12 && docker rm mi-app-prod pg-u12
docker network rm red-u12
```

En arranques posteriores se omite `-e JPA_DDL_AUTO=update` y la aplicación valida el esquema existente.
## Decisiones de diseño (Parte 1)

1. **Placeholders con valor por defecto (`${VAR:default}`) en lugar de placeholders estrictos.**
   Con valores por defecto razonables (H2 en memoria, puerto 8080, perfil `dev`) la misma imagen arranca sin fricción en la máquina del desarrollador, y en las pruebas, sin exportar variables. En producción la plataforma sobrescribe cada variable con su valor real. Las credenciales de producción (`DATASOURCE_USER`, `DATASOURCE_PASSWORD`) son la excepción: no tienen valor por defecto, porque una credencial de producción nunca debe tener un valor utilizable escrito en el código.

2. **`application-dev.properties` y `application-prod.properties` como archivos separados.**
   En lugar de bloques condicionales (`#---` con `spring.config.activate.on-profile`) en un único archivo, cada ambiente tiene su archivo según la convención `application-{perfil}.properties`. Con solo ver el nombre del archivo queda claro qué configuración corresponde a cada ambiente y se reduce el riesgo de modificar por error una propiedad de producción mientras se trabaja en desarrollo.

3. **Imagen final `eclipse-temurin:17-jre-alpine` en lugar de una imagen JDK o distroless.**
   Para ejecutar el JAR solo se necesita el JRE: la imagen final no incluye compilador ni Maven, lo que reduce tamaño y superficie de ataque. A diferencia de distroless, Alpine conserva un shell mínimo que permite diagnosticar el contenedor con `docker exec -it <contenedor> sh`. Se usa Java 17 porque es la versión que declara el proyecto; no fue necesario cambiar a Java 21.

## Capturas de pantalla

Las capturas se guardan en [`capturas/`](capturas/):

| Archivo | Qué debe mostrar |
|---|---|
| `capturas/docker-images.png` | La imagen `mi-spring-app:local` construida localmente (`docker images mi-spring-app`) |
| `capturas/docker-dev.png` | Contenedor en perfil `dev`: log con `profile is active: "dev"` y respuesta del endpoint REST |
| `capturas/docker-prod.png` | Contenedor en perfil `prod`: log con `profile is active: "prod"` y la aplicación iniciada con PostgreSQL |
