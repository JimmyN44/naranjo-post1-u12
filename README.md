# Post-contenido — Unidad 12: Despliegue y CI/CD

[![CI Status](https://github.com/JimmyN44/naranjo-post1-u12/actions/workflows/ci.yml/badge.svg)](https://github.com/JimmyN44/naranjo-post1-u12/actions/workflows/ci.yml)

Programación Web — Unidad 12 · Laboratorio post-contenido (Parte 1 y Parte 2)

## 1. Descripción

Repositorio del laboratorio de la Unidad 12 de Programación Web. Contiene la aplicación Spring Boot **`pedidos`** desarrollada en la Unidad 11 —una API REST de gestión de pedidos de una tienda en línea, con paquete raíz `com.tienda.pedidos`— llevada a un flujo real de contenedorización, integración continua y despliegue:

- **Parte 1 — Dockerización con perfiles y variables de entorno:** Dockerfile multi-stage, configuración externalizada con placeholders `${VAR:default}` y separación de los ambientes `dev` y `prod` mediante perfiles de Spring activados por variable de entorno.
- **Parte 2 — Pipeline CI/CD con GitHub Actions y monitoreo con Actuator:** Spring Boot Actuator con exposición diferenciada por perfil, prueba automatizada de contexto, workflow de GitHub Actions que compila, prueba, construye la imagen y verifica con un smoke test que el contenedor responde `UP`, y despliegue manual en una plataforma cloud a partir del mismo Dockerfile.

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
| Spring Boot Actuator | 3.2.12 (gestionada por el BOM de Spring Boot) | Monitoreo (`/actuator/health`) |
| H2 Database | 2.2.224 | Base de datos en memoria (perfil `dev` y pruebas) |
| Driver PostgreSQL | 42.6.2 | Base de datos externa (perfil `prod`) |
| springdoc-openapi-starter-webmvc-ui | 2.3.0 | OpenAPI 3 y Swagger UI |
| JUnit 5, Mockito, MockMvc, AssertJ | (spring-boot-starter-test) | Pruebas automatizadas |
| Maven / Maven Wrapper | 3.9.11 / 3.3.4 | Construcción |
| Imagen de compilación | `maven:3.9.11-eclipse-temurin-17-alpine` | Etapa *builder* del Dockerfile |
| Imagen de ejecución | `eclipse-temurin:17-jre-alpine` | Etapa final del Dockerfile |
| GitHub Actions | `actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4` | Pipeline CI |

## 4. Arquitectura del proyecto

```
naranjo-post1-u12/
├── README.md
├── Dockerfile                    # build multi-stage (Maven + JDK 17 → JRE 17 Alpine)
├── .dockerignore
├── .gitignore
├── .github/
│   └── workflows/
│       └── ci.yml                # pipeline: build-and-test → docker-build (smoke test)
├── docs/evidencias/              # capturas de evidencia por parte (sección 20)
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
        └── test/java/com/tienda/pedidos/
            ├── ApplicationContextTest.java       # prueba de contexto (Unidad 12)
            └── ...                               # pruebas de la Unidad 11
```

El proyecto Maven vive en la subcarpeta `pedidos/`; el `Dockerfile` está en la raíz y el contexto de build es la raíz del repositorio.

Flujo completo:

```
git push / pull request a main
        │
        ▼
GitHub Actions ── build-and-test ──(needs)──▶ docker-build
                  mvn clean verify            docker build → docker run → /actuator/health = UP
                  reportes Surefire

Railway / Render ── detecta el Dockerfile ──▶ construye la imagen ──▶ ejecuta con perfil prod + PostgreSQL
```

### Endpoints de la aplicación

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/pedidos` | Crea un pedido y calcula descuento (REGULAR 0 %, VIP 15 %, MAYORISTA 25 %) e IVA del 19 % | `201`, `400`, `500` |
| `GET` | `/api/pedidos/{id}` | Consulta un pedido por id (nunca expone `notasInternas`) | `200`, `400`, `404`, `500` |
| `GET` | `/swagger-ui.html` | Documentación interactiva (Swagger UI) | `200` |
| `GET` | `/api-docs` | Contrato OpenAPI 3 en JSON | `200` |
| `GET` | `/actuator/health` | Estado de la aplicación (Actuator) | `200` UP / `503` DOWN |
| `GET` | `/actuator/info` | Información de la aplicación (Actuator) | `200` |
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
| Actuator | Todos los endpoints, `show-details=always` | Solo `health` e `info`, `show-details=when-authorized` |

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
curl http://localhost:8080/actuator/health
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

---

# Parte 2 — Pipeline CI/CD con GitHub Actions y monitoreo con Actuator

## 10. Parte 2: Spring Boot Actuator

Se agregó la dependencia `spring-boot-starter-actuator` sin versión explícita (la gestiona el BOM de Spring Boot 3.2.12). La exposición se diferencia por perfil:

```properties
# application-dev.properties
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always

# application-prod.properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

| Petición | `dev` | `prod` |
|---|---|---|
| `GET /actuator/health` | `200` con `status` y detalle de componentes (`db`, `diskSpace`, `ping`) | `200` `{"status":"UP"}` (sin detalles) |
| `GET /actuator/beans`, `/env`, `/configprops`, `/heapdump`, ... | Expuestos (diagnóstico local) | `404` |
| `GET /actuator/health` con la base de datos caída | — | `503` `{"status":"DOWN"}` |

En `dev`, `/actuator/env` enmascara los valores sensibles (`******`), comportamiento por defecto de Spring Boot 3.

## 11. Pruebas automatizadas

El proyecto ejecuta **26 pruebas** (JUnit 5) con `mvn clean verify`:

| Clase | Tipo | Pruebas |
|---|---|---|
| `ApplicationContextTest` *(Unidad 12)* | Contexto con `@SpringBootTest` + `@ActiveProfiles("dev")` | 4 |
| `PedidosApplicationTests` | Carga de contexto | 1 |
| `controller.PedidoControllerIntegrationTest` | Integración de endpoints REST (MockMvc) | 10 |
| `exception.GlobalExceptionHandlerTest` | `@WebMvcTest` (respuesta 500) | 1 |
| `service.PedidoServiceImplTest` | Unitarias con Mockito | 7 |
| `strategy.EstrategiaDescuentoTest` | Unitarias | 3 |

`ApplicationContextTest` verifica con aserciones reales que, con el perfil `dev`:

1. el único perfil activo es `dev` y se cargaron propiedades exclusivas de `application-dev.properties`;
2. el contexto registra el controlador, el servicio (`PedidoServiceImpl`), el repositorio y exactamente las estrategias `REGULAR`, `VIP` y `MAYORISTA`;
3. la conexión real a la base de datos es H2 en `jdbc:h2:mem:pedidosdb`;
4. `GET /actuator/health` responde `200` con `status` `UP` y la base de datos `H2` en `UP`.

Los reportes se generan en `pedidos/target/surefire-reports/`.

## 12. GitHub Actions

Archivo: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

- **Disparadores:** `push` a `main` y `pull_request` hacia `main`.
- **Permisos:** `contents: read` (solo lectura del repositorio).
- **Jobs:** `build-and-test` → `docker-build`, encadenados con `needs:`; cada uno con `timeout-minutes: 15`.
- **Sin registro externo:** no hay `docker push` ni secretos de Docker Hub.

Estado e historial de ejecuciones: pestaña [Actions](https://github.com/JimmyN44/naranjo-post1-u12/actions) del repositorio y badge al inicio de este README.

## 13. Job `build-and-test`

| Paso | Detalle |
|---|---|
| Checkout | `actions/checkout@v4` |
| JDK | `actions/setup-java@v4` con `java-version: "17"`, `distribution: temurin` |
| Caché Maven | `cache: maven`, clave calculada a partir de `pedidos/pom.xml` |
| Compilar y probar | `mvn -B -ntp clean verify` en `working-directory: pedidos` |
| Reportes | `actions/upload-artifact@v4` sube `pedidos/target/surefire-reports/` como artefacto `surefire-reports`, `retention-days: 7`, con `if: always()` para conservarlos también cuando una prueba falla |

## 14. Job `docker-build`

| Paso | Detalle |
|---|---|
| Dependencia | `needs: build-and-test`: solo se ejecuta si compilación y pruebas pasaron |
| Checkout | `actions/checkout@v4` |
| Construir imagen | `docker build -t mi-spring-app:${{ github.sha }} .` con el mismo Dockerfile de la Parte 1 |
| Smoke test | Levanta el contenedor y espera `/actuator/health` = `UP` (sección 15) |
| Limpieza | `docker rm -f smoke-test \|\| true` con `if: always()`: el contenedor se elimina aunque el smoke test falle |

## 15. Smoke test

```bash
docker run -d --name "$CONTAINER" -p 8080:8080 "$IMAGE"

for i in $(seq 1 15); do
  if curl -sf --max-time 3 http://localhost:8080/actuator/health | jq -e '.status == "UP"' > /dev/null; then
    echo "La aplicacion respondio UP (intento ${i}/15):"
    curl -s http://localhost:8080/actuator/health | jq .
    exit 0
  fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER")" != "true" ]; then
    echo "El contenedor se detuvo antes de responder"
    break
  fi
  echo "Esperando a que la aplicacion arranque... (${i}/15)"
  sleep 2
done

echo "La aplicacion no respondio UP a tiempo"
docker logs "$CONTAINER"
exit 1
```

- El contenedor arranca **sin variables**, es decir, con el perfil `dev` y H2 en memoria: no depende de ninguna base de datos ni cuenta externa.
- Hasta **15 intentos con 2 s** de espera entre ellos; cada petición tiene un límite de 3 s (`--max-time 3`).
- `curl -f` falla con HTTP ≥ 400 (un health `DOWN` responde `503`) y `jq -e '.status == "UP"'` valida el estado **de primer nivel**. Un `grep '"status":"UP"'` daría un falso positivo, porque en `dev` la respuesta incluye componentes con su propio `"status":"UP"` aunque el estado general fuera `DOWN`.
- Si el contenedor termina antes de responder, el bucle se corta sin esperar los 30 s.
- En caso de fallo se imprime `docker logs` y el paso termina con `exit 1`, lo que marca el job en rojo.

## 16. Despliegue en Railway o Render

El despliegue es manual y lo realiza la plataforma a partir del mismo `Dockerfile` de la raíz, con su propio build e independiente de GitHub Actions; el workflow no necesita credenciales de la plataforma.

> **Plataforma utilizada:** _PENDIENTE — indicar Railway o Render._

### Railway

1. Iniciar sesión en railway.com con GitHub y autorizar el acceso al repositorio.
2. *New Project → Deploy from GitHub repo →* `naranjo-post1-u12`. Rama `main`; *Root Directory* vacío (raíz del repositorio, donde está el `Dockerfile`).
3. Agregar una base de datos: *Create → Database → PostgreSQL* (servicio `Postgres`).
4. En el servicio de la aplicación, *Variables → Raw Editor*:
   ```
   SPRING_PROFILES_ACTIVE=prod
   DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DATASOURCE_USER=${{Postgres.PGUSER}}
   DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
   JPA_DDL_AUTO=update
   PORT=8080
   ```
   Las referencias `${{Postgres.…}}` las resuelve Railway; ninguna credencial se copia ni se versiona.
5. *Settings → Deploy → Healthcheck Path:* `/actuator/health`. Desplegar y verificar en *Deploy Logs* `The following 1 profile is active: "prod"`.
6. *Settings → Networking → Generate Domain* (puerto `8080`).
7. Tras el primer despliegue correcto, eliminar `JPA_DDL_AUTO` para volver a `validate`.

### Render

1. *New → PostgreSQL*; anotar de *Connections* Hostname interno, Port, Database, Username y Password.
2. *New → Web Service →* repositorio `naranjo-post1-u12`; *Runtime: Docker*, rama `main`, *Root Directory* vacío, misma región que la base de datos.
3. Variables: `SPRING_PROFILES_ACTIVE=prod`, `DATASOURCE_URL=jdbc:postgresql://<hostname-interno>:<port>/<database>`, `DATASOURCE_USER`, `DATASOURCE_PASSWORD` y `JPA_DDL_AUTO=update` (solo el primer despliegue). Render inyecta `PORT` y la aplicación la respeta.
4. *Advanced → Health Check Path:* `/actuator/health`.

## 17. URL pública

> **URL de la aplicación:** _PENDIENTE — agregar la URL pública real generada por la plataforma._
>
> **Health check:** _PENDIENTE — `<URL pública>/actuator/health`._

## 18. Endpoint `/actuator/health`

Respuesta esperada en producción (perfil `prod`, sin detalles):

```bash
curl https://<URL-pública>/actuator/health
```

```json
{"status":"UP"}
```

Comprobaciones adicionales en producción:

```bash
curl -i https://<URL-pública>/actuator/beans      # 404: no expuesto en prod
curl -X POST https://<URL-pública>/api/pedidos \
  -H "Content-Type: application/json" \
  -d '{"clienteEmail":"ana@correo.com","tipoCliente":"VIP","subtotal":100000}'   # 201
```

Si la base de datos no está disponible, `/actuator/health` responde `503` con `{"status":"DOWN"}`, y el healthcheck de la plataforma lo detecta.

## 19. Decisiones de diseño

### Parte 1

1. **Placeholders con valor por defecto (`${VAR:default}`) en lugar de placeholders estrictos.**
   Con valores por defecto razonables (H2 en memoria, puerto 8080, perfil `dev`) la misma imagen arranca sin fricción en la máquina del desarrollador, en las pruebas y en el smoke test del pipeline, sin exportar variables. En producción la plataforma sobrescribe cada variable con su valor real. Las credenciales de producción (`DATASOURCE_USER`, `DATASOURCE_PASSWORD`) son la excepción: no tienen valor por defecto, porque una credencial de producción nunca debe tener un valor utilizable escrito en el código.

2. **`application-dev.properties` y `application-prod.properties` como archivos separados.**
   En lugar de bloques condicionales (`#---` con `spring.config.activate.on-profile`) en un único archivo, cada ambiente tiene su archivo según la convención `application-{perfil}.properties`. Con solo ver el nombre del archivo queda claro qué configuración corresponde a cada ambiente y se reduce el riesgo de modificar por error una propiedad de producción mientras se trabaja en desarrollo.

3. **Imagen final `eclipse-temurin:17-jre-alpine` en lugar de una imagen JDK o distroless.**
   Para ejecutar el JAR solo se necesita el JRE: la imagen final no incluye compilador ni Maven, lo que reduce tamaño y superficie de ataque. A diferencia de distroless, Alpine conserva un shell mínimo que permite diagnosticar el contenedor con `docker exec -it <contenedor> sh`. Se usa Java 17 porque es la versión que declara el proyecto; no fue necesario cambiar a Java 21.

### Parte 2

1. **Prueba de contexto automatizada en lugar de omitir la fase de pruebas.**
   El proyecto ya traía pruebas de la Unidad 11, pero su prueba de contexto (`contextLoads`) no contenía aserciones y habría pasado sin validar nada. `ApplicationContextTest` obliga a Spring a construir el contexto completo con el perfil `dev` y comprueba con aserciones el perfil activo, los beans de cada capa, la base H2 y la respuesta `UP` de Actuator, así el job `build-and-test` valida algo real antes de que el pipeline avance a Docker.

2. **El job `docker-build` construye y verifica la imagen con un smoke test, pero no la publica en un registro.**
   Publicar en Docker Hub o GHCR exigiría configurar secretos de una cuenta externa solo para que el pipeline pase. Lo que importa verificar es que la imagen construida arranca y expone su endpoint de monitoreo, y eso se comprueba dentro del propio runner. El despliegue lo hace Railway/Render construyendo desde el mismo Dockerfile, sin que el workflow tenga que autenticarse contra ellas.

3. **Exposición de Actuator diferenciada por perfil.**
   En `dev` se exponen todos los endpoints con detalles completos para diagnosticar localmente. En `prod` solo `health` e `info`, y `health` sin detalles (`when-authorized`, sin usuarios autorizados): endpoints como `env`, `configprops`, `beans` o `heapdump` podrían revelar configuración, variables de entorno o memoria de la aplicación, por eso responden `404`.

## 20. Evidencias

Capturas **reales** del laboratorio, tomadas en el entorno de desarrollo (contenedor Linux en la nube) con herramientas reales: una terminal `xterm` (servidor gráfico Xvfb) capturada con ImageMagick, el navegador Chromium y la API pública de GitHub para los datos del pipeline. Ninguna imagen es generada ni editada; la fecha y hora UTC de cada captura aparece en su primera línea. Las contraseñas de prueba se leen de archivos `--env-file` y se muestran enmascaradas (`********`).

### Parte 1 — Dockerización (`docs/evidencias/parte-1-dockerizacion/`)

| # | Checkpoint | Captura |
|---|---|---|
| 1 | Configuración base: `server.port=${PORT:8080}` y `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}` | [`01-application-properties.png`](docs/evidencias/parte-1-dockerizacion/01-application-properties.png) |
| 2 | Perfil `dev`: H2 en memoria y placeholders con valor por defecto | [`02-perfil-dev-properties.png`](docs/evidencias/parte-1-dockerizacion/02-perfil-dev-properties.png) |
| 3 | Perfil `prod`: PostgreSQL, credenciales solo por variables de entorno, `ddl-auto` `validate` | [`03-perfil-prod-properties.png`](docs/evidencias/parte-1-dockerizacion/03-perfil-prod-properties.png) |
| 4 | Dockerfile multi-stage (Maven + JDK 17 → JRE 17 Alpine, usuario no root, `ENTRYPOINT java -jar`) | [`04-dockerfile-multi-stage.png`](docs/evidencias/parte-1-dockerizacion/04-dockerfile-multi-stage.png) |
| 5 | Archivo `.dockerignore` | [`05-dockerignore.png`](docs/evidencias/parte-1-dockerizacion/05-dockerignore.png) |
| 6 | Construcción de la imagen, `docker images` y tamaño de las capas (246.6 MB) ¹ | [`06-docker-build-imagen.png`](docs/evidencias/parte-1-dockerizacion/06-docker-build-imagen.png) |
| 7 | Contenedor en ejecución: `docker ps`, usuario `spring`, sin `javac` ni `mvn`, `ENTRYPOINT` | [`07-contenedor-en-ejecucion.png`](docs/evidencias/parte-1-dockerizacion/07-contenedor-en-ejecucion.png) |
| 8 | Perfil `dev` activo con H2: `POST` 201, `GET` 200, `GET` inexistente 404, 0 errores en el log | [`08-perfil-dev-respuesta-api.png`](docs/evidencias/parte-1-dockerizacion/08-perfil-dev-respuesta-api.png) |
| 9 | Perfil `prod` activo con PostgreSQL 16 local de prueba: `POST` 201, fila en la tabla `pedido`, consola H2 404 | [`09-perfil-prod-postgresql.png`](docs/evidencias/parte-1-dockerizacion/09-perfil-prod-postgresql.png) |
| 10 | Estructura de archivos versionados; `target/` y `logs/` ignorados | [`10-estructura-proyecto.png`](docs/evidencias/parte-1-dockerizacion/10-estructura-proyecto.png) |

¹ La red de este entorno inspecciona TLS, por lo que la imagen se construyó con una copia del Dockerfile que solo añade 2 líneas en la etapa *builder* para confiar en esa CA (visible en la propia captura); la etapa final es idéntica. En GitHub Actions la imagen se construye con el `Dockerfile` original del repositorio.

**P1-01.** Configuración base: `server.port=${PORT:8080}` y `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`

![P1-01 application-properties](docs/evidencias/parte-1-dockerizacion/01-application-properties.png)

**P1-02.** Perfil `dev`: H2 en memoria y placeholders con valor por defecto

![P1-02 perfil-dev-properties](docs/evidencias/parte-1-dockerizacion/02-perfil-dev-properties.png)

**P1-03.** Perfil `prod`: PostgreSQL, credenciales solo por variables de entorno, `ddl-auto` `validate`

![P1-03 perfil-prod-properties](docs/evidencias/parte-1-dockerizacion/03-perfil-prod-properties.png)

**P1-04.** Dockerfile multi-stage (Maven + JDK 17 → JRE 17 Alpine, usuario no root, `ENTRYPOINT java -jar`)

![P1-04 dockerfile-multi-stage](docs/evidencias/parte-1-dockerizacion/04-dockerfile-multi-stage.png)

**P1-05.** Archivo `.dockerignore`

![P1-05 dockerignore](docs/evidencias/parte-1-dockerizacion/05-dockerignore.png)

**P1-06.** Construcción de la imagen, `docker images` y tamaño de las capas (246.6 MB) ¹

![P1-06 docker-build-imagen](docs/evidencias/parte-1-dockerizacion/06-docker-build-imagen.png)

**P1-07.** Contenedor en ejecución: `docker ps`, usuario `spring`, sin `javac` ni `mvn`, `ENTRYPOINT`

![P1-07 contenedor-en-ejecucion](docs/evidencias/parte-1-dockerizacion/07-contenedor-en-ejecucion.png)

**P1-08.** Perfil `dev` activo con H2: `POST` 201, `GET` 200, `GET` inexistente 404, 0 errores en el log

![P1-08 perfil-dev-respuesta-api](docs/evidencias/parte-1-dockerizacion/08-perfil-dev-respuesta-api.png)

**P1-09.** Perfil `prod` activo con PostgreSQL 16 local de prueba: `POST` 201, fila en la tabla `pedido`, consola H2 404

![P1-09 perfil-prod-postgresql](docs/evidencias/parte-1-dockerizacion/09-perfil-prod-postgresql.png)

**P1-10.** Estructura de archivos versionados; `target/` y `logs/` ignorados

![P1-10 estructura-proyecto](docs/evidencias/parte-1-dockerizacion/10-estructura-proyecto.png)

### Parte 2 — CI/CD y monitoreo (`docs/evidencias/parte-2-ci-cd-monitoreo/`)

Las capturas 6 a 9 muestran datos reales obtenidos de la API pública de GitHub (`api.github.com`) sobre las ejecuciones del workflow; en este entorno los recursos visuales de github.com están bloqueados por la red, por lo que la interfaz web de GitHub no se pudo capturar con estilos.

| # | Checkpoint | Captura |
|---|---|---|
| 1 | Dependencia `spring-boot-starter-actuator` (versión del BOM, 3.2.12) y configuración `management.*` por perfil | [`01-actuator-dependencia-configuracion.png`](docs/evidencias/parte-2-ci-cd-monitoreo/01-actuator-dependencia-configuracion.png) |
| 2 | `/actuator/health` en Chromium: `dev` (puerto 8080) con detalle de componentes y `prod` (puerto 8081) `{"status":"UP"}` | [`02-actuator-health-navegador-dev-prod.png`](docs/evidencias/parte-2-ci-cd-monitoreo/02-actuator-health-navegador-dev-prod.png) |
| 3 | Exposición diferenciada: en `dev` todos los endpoints responden 200; en `prod` solo `health` e `info`, el resto 404 | [`03-actuator-exposicion-dev-vs-prod.png`](docs/evidencias/parte-2-ci-cd-monitoreo/03-actuator-exposicion-dev-vs-prod.png) |
| 4 | `mvn clean verify`: 26 pruebas, 0 fallos, BUILD SUCCESS y reportes de Surefire | [`04-pruebas-mvn-clean-verify.png`](docs/evidencias/parte-2-ci-cd-monitoreo/04-pruebas-mvn-clean-verify.png) |
| 5 | Workflow `.github/workflows/ci.yml` completo | [`05-workflow-ci-yml.png`](docs/evidencias/parte-2-ci-cd-monitoreo/05-workflow-ci-yml.png) |
| 6 | Job `build-and-test` exitoso (run #1): pasos, `Tests run: 26`, BUILD SUCCESS, artefacto `surefire-reports` y caché de Maven | [`06-job-build-and-test-exitoso.png`](docs/evidencias/parte-2-ci-cd-monitoreo/06-job-build-and-test-exitoso.png) |
| 7 | Job `docker-build` exitoso tras `build-and-test` (`needs`), con la imagen `mi-spring-app:<sha>` construida | [`07-job-docker-build-exitoso.png`](docs/evidencias/parte-2-ci-cd-monitoreo/07-job-docker-build-exitoso.png) |
| 8 | Log real del smoke test en el runner: espera, `La aplicacion respondio UP (intento 5/15)` y limpieza del contenedor | [`08-smoke-test-log.png`](docs/evidencias/parte-2-ci-cd-monitoreo/08-smoke-test-log.png) |
| 9 | Tres ejecuciones exitosas del workflow en `main` (runs #1, #2 y #3), cada una con `build-and-test` y `docker-build` en `success` | [`09-tres-ejecuciones-pipeline.png`](docs/evidencias/parte-2-ci-cd-monitoreo/09-tres-ejecuciones-pipeline.png) |

**P2-01.** Dependencia `spring-boot-starter-actuator` (versión del BOM, 3.2.12) y configuración `management.*` por perfil

![P2-01 actuator-dependencia-configuracion](docs/evidencias/parte-2-ci-cd-monitoreo/01-actuator-dependencia-configuracion.png)

**P2-02.** `/actuator/health` en Chromium: `dev` (puerto 8080) con detalle de componentes y `prod` (puerto 8081) `{"status":"UP"}`

![P2-02 actuator-health-navegador-dev-prod](docs/evidencias/parte-2-ci-cd-monitoreo/02-actuator-health-navegador-dev-prod.png)

**P2-03.** Exposición diferenciada: en `dev` todos los endpoints responden 200; en `prod` solo `health` e `info`, el resto 404

![P2-03 actuator-exposicion-dev-vs-prod](docs/evidencias/parte-2-ci-cd-monitoreo/03-actuator-exposicion-dev-vs-prod.png)

**P2-04.** `mvn clean verify`: 26 pruebas, 0 fallos, BUILD SUCCESS y reportes de Surefire

![P2-04 pruebas-mvn-clean-verify](docs/evidencias/parte-2-ci-cd-monitoreo/04-pruebas-mvn-clean-verify.png)

**P2-05.** Workflow `.github/workflows/ci.yml` completo

![P2-05 workflow-ci-yml](docs/evidencias/parte-2-ci-cd-monitoreo/05-workflow-ci-yml.png)

**P2-06.** Job `build-and-test` exitoso (run #1): pasos, `Tests run: 26`, BUILD SUCCESS, artefacto `surefire-reports` y caché de Maven

![P2-06 job-build-and-test-exitoso](docs/evidencias/parte-2-ci-cd-monitoreo/06-job-build-and-test-exitoso.png)

**P2-07.** Job `docker-build` exitoso tras `build-and-test` (`needs`), con la imagen `mi-spring-app:<sha>` construida

![P2-07 job-docker-build-exitoso](docs/evidencias/parte-2-ci-cd-monitoreo/07-job-docker-build-exitoso.png)

**P2-08.** Log real del smoke test en el runner: espera, `La aplicacion respondio UP (intento 5/15)` y limpieza del contenedor

![P2-08 smoke-test-log](docs/evidencias/parte-2-ci-cd-monitoreo/08-smoke-test-log.png)

**P2-09.** Tres ejecuciones exitosas del workflow en `main` (runs #1, #2 y #3), cada una con `build-and-test` y `docker-build` en `success`

![P2-09 tres-ejecuciones-pipeline](docs/evidencias/parte-2-ci-cd-monitoreo/09-tres-ejecuciones-pipeline.png)

### Evidencias pendientes

| Checkpoint | Estado | Cómo obtenerla |
|---|---|---|
| P2-10 Aplicación desplegada en Railway o Render | **Pendiente (manual)** — sin acceso a la plataforma desde este entorno | Captura del panel del servicio con el despliegue activo |
| P2-11 URL pública respondiendo `{"status":"UP"}` | **Pendiente (manual)** — no hay URL pública registrada | Abrir `https://<URL-pública>/actuator/health` en el navegador con la URL visible |
| P2-12 README con el badge de GitHub Actions en GitHub | **Pendiente (manual)** — la interfaz de github.com no se renderiza en este entorno | Captura de la página principal del repositorio con el badge visible |
