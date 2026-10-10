# ms-digitalfix-rabbit-admin (puerto 8087)

[![CI](https://github.com/DigitalFix-Grupo12/ms-digitalfix-rabbit-admin/actions/workflows/ci.yml/badge.svg)](https://github.com/DigitalFix-Grupo12/ms-digitalfix-rabbit-admin/actions/workflows/ci.yml)

Microservicio administrador de RabbitMQ. Toda la lógica está en `RabbitAdminService`; los controladores
solo reciben DTO validados y llaman métodos de alto nivel. Se expone por el BFF únicamente al rol **Admin**
(`/api/rabbit/**` → JWT en API Gateway + rol en el BFF).

## API

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/rabbit/overview` | Clúster: nodos, versión, totales |
| GET | `/api/rabbit/queues` | Colas con mensajes, consumidores, nodo y DLX |
| GET | `/api/rabbit/queues/{name}` | Detalle (404 si no existe) |
| POST | `/api/rabbit/queues` | Crea cola (opcional DLQ, TTL, máx. mensajes) → **201** + `Location` |
| DELETE | `/api/rabbit/queues/{name}?ifUnused=&ifEmpty=` | Elimina → **204** (409 si está protegida o no cumple la condición) |
| DELETE | `/api/rabbit/queues/{name}/messages` | Vacía la cola (purge) |
| GET | `/api/rabbit/queues/{name}/messages?count=` | Lee mensajes **sin consumirlos** (origen, motivo y reintentos si es DLQ) |
| POST | `/api/rabbit/queues/{dlq}/replay?max=` | Devuelve mensajes de una DLQ a su cola de origen |
| GET | `/api/rabbit/exchanges` | Exchanges de la aplicación (`?includeSystem=true` incluye `amq.*`) |
| POST | `/api/rabbit/exchanges` | Crea exchange `direct`, `topic`, `fanout` o `headers` → **201** |
| DELETE | `/api/rabbit/exchanges/{name}` | Elimina → **204** |
| GET | `/api/rabbit/bindings` | Bindings exchange → cola |
| POST | `/api/rabbit/bindings` | Crea binding → **201** (409 si ya existe) |
| DELETE | `/api/rabbit/bindings?exchange=&queue=&routingKey=` | Elimina → **204** |
| POST | `/api/rabbit/messages` | Publica un mensaje de prueba → **202** con `routed` (si alguna cola lo recibió) |

## Validaciones

- Nombres: 1–200 caracteres `[A-Za-z0-9._:-]`, sin espacios, sin prefijo reservado `amq.` → **400** con detalle por campo.
- `type` de exchange solo `direct | topic | fanout | headers`; TTL 1 ms–7 días; `maxLength` 1–1.000.000.
- Comodines `*` / `#` solo en bindings de exchanges **topic**; al publicar no se aceptan.
- Una DLQ no puede tener su propia DLQ; no se crean colas/exchanges/bindings duplicados (**409**).
- Recursos del sistema (`cmd.*`, `q.cmd.*`) no se pueden eliminar (**409**).
- Errores en formato RFC 7807 (`application/problem+json`): `title`, `detail`, `errors`.

```json
{ "title": "Datos invalidos", "status": 400, "detail": "Revisa los campos indicados en 'errors'",
  "errors": { "name": "debe tener 1 a 200 caracteres (...) y sin el prefijo reservado 'amq.'" } }
```

## Implementación

- Crear/eliminar: AMQP con `AmqpAdmin` (Spring AMQP).
- Consultas, lectura sin consumir y publicación con `routed`: Management HTTP API (`RabbitManagementClient`),
  con las URL de **ambos nodos**: si uno no responde se usa el otro.
- Reproceso de DLQ (`DeadLetterReplayer`): `basicGet` + republicación con *publisher confirms*; el mensaje se
  confirma en la DLQ solo después de que el broker aceptó la copia.

## Ejecutar

```powershell
mvn clean package
java -jar target/ms-digitalfix-rabbit-admin-0.0.1-SNAPSHOT.jar
```

Variables: `RABBITMQ_ADDRESSES`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`,
`RABBITMQ_MANAGEMENT_URLS` (default `http://localhost:15672,http://localhost:15673`).

```powershell
# Ejemplos (directo, local)
Invoke-RestMethod http://localhost:8087/api/rabbit/overview
Invoke-RestMethod -Method Post http://localhost:8087/api/rabbit/queues -ContentType 'application/json' -Body '{"name":"q.demo","withDeadLetterQueue":true}'
Invoke-RestMethod -Method Delete http://localhost:8087/api/rabbit/queues/q.demo
```
