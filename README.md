# Demo Backend

Spring Boot backend for a sample CRUD application. The service exposes REST APIs for `SampleItem` and uses an in-memory H2 database for local development.

## Stack

- Java 17
- Spring Boot 4
- Spring Web
- Spring Data JPA
- H2 in-memory database
- Maven Wrapper
- Docker

## Project Structure

```text
src/main/java/com/example/demo
  controller
  entity
  repository
  service
```

## Prerequisites

Verify Java is available:

```powershell
java -version
```

If Java is installed but not on PATH, use this for the current PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Users\hamik\.jdks\corretto-17.0.19"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Run Locally

```powershell
cd "C:\Users\hamik\Downloads\demo (1)\demo"
.\mvnw.cmd spring-boot:run
```

The backend runs on port `8080`.

## API Endpoints

```text
GET    /api/items
POST   /api/items
PUT    /api/items/{id}
DELETE /api/items/{id}
```

Create an item:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/items `
  -ContentType "application/json" `
  -Body '{"name":"sample item"}'
```

Update an item:

```powershell
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/items/1 `
  -ContentType "application/json" `
  -Body '{"name":"updated item"}'
```

List items:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/items
```

Delete an item:

```powershell
Invoke-RestMethod -Method Delete -Uri http://localhost:8080/api/items/1
```

## Tests

```powershell
.\mvnw.cmd test
```

## Docker

Build and run the backend container:

```powershell
cd "C:\Users\hamik\Downloads\demo (1)\demo"
docker compose up --build
```

Run in detached mode:

```powershell
docker compose up --build -d
```

Stop:

```powershell
docker compose down
```

## Running With Frontend Docker Compose

The backend and frontend use separate compose files. Create the shared Docker network once:

```powershell
docker network create demo-network
```

If the network already exists, ignore the message.

Start backend from this folder:

```powershell
docker compose up --build -d
```

Then start the frontend from:

```text
C:\Users\hamik\Downloads\frontend
```

## Deployment Template

This repo includes a service-level `kitt.yml` template with:

- Docker image metadata
- Min/max pod counts
- Routing metadata
- Readiness/liveness health checks
- Global deployment placeholders

The exact KITT keys may need to be adjusted to match the target platform schema.

## Troubleshooting

If port `8080` is busy:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

Stop a stale backend container:

```powershell
docker rm -f demo-backend
```
