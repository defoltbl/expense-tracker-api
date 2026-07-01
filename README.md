# Expense Tracker API

![language](https://img.shields.io/badge/language-Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)
![framework](https://img.shields.io/badge/framework-Ktor-087CFA?style=flat&logo=ktor&logoColor=white)
![database](https://img.shields.io/badge/database-PostgreSQL-4169E1?style=flat&logo=postgresql&logoColor=white)
![cloud](https://img.shields.io/badge/cloud-Azure-0078D4?style=flat&logo=microsoftazure&logoColor=white)
![containerized](https://img.shields.io/badge/containerized-Docker-2496ED?style=flat&logo=docker&logoColor=white)

A REST API for tracking personal expenses, built with Kotlin and Ktor, featuring JWT authentication, per-user data isolation, and full Docker + Azure cloud deployment.

**Live demo:** https://expense-tracker-api-andrii.azurewebsites.net (visiting it returns a small JSON status response, since this is a backend-only API with no UI – see below for actual usable endpoints)

> Note: the live demo runs on a paid Azure tier and may be stopped between active development periods to manage cloud costs. If the link is unresponsive, the project can be run locally – see [Running locally](#running-locally) below.

---

## Overview

This project started as a learning exercise in backend development with Kotlin and ended up touching nearly every layer of a real production-style API: routing, persistence, authentication, authorization, containerization, and cloud deployment.

It manages personal expenses for multiple users, where each user can only see and modify their own data – enforced at the database query level using the identity embedded in their JWT token, not anything the client sends.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Web framework | Ktor (Netty engine) |
| Database | PostgreSQL |
| ORM / SQL DSL | Exposed |
| Connection pooling | HikariCP |
| Authentication | JWT (Auth0 java-jwt) |
| Password hashing | BCrypt (at.favre.lib) |
| Transactional email | SendGrid |
| Serialization | kotlinx.serialization |
| Containerization | Docker (multi-stage build) |
| Cloud hosting | Azure App Service + Azure Database for PostgreSQL + Azure Container Registry |
| Monitoring | Azure Application Insights |
| CI | GitHub Actions |
| Build tool | Gradle (Kotlin DSL) with Shadow plugin |

## Architecture

```
Client
  |
  | HTTP requests (JSON)
  v
Ktor Routing  ---->  Rate Limiting  ---->  Authentication Plugin (JWT verification)
  |
  v
Application logic (Routing.kt)
  |
  v
Exposed (SQL DSL)
  |
  v
PostgreSQL (Azure Database for PostgreSQL Flexible Server)
```

**Auth flow:**
1. Client registers (`POST /register`) → password is hashed with BCrypt before storage
2. Client logs in (`POST /login`) → password is verified against the stored hash → a short-lived access token and a longer-lived refresh token are issued
3. Client includes the access token as `Authorization: Bearer <token>` on subsequent requests
4. Protected routes extract the user ID from the verified token (never from the request body) and scope every database query to that user
5. When the access token expires, the client exchanges its refresh token at `POST /refresh` for a new access token, without re-entering credentials

**Password reset flow:**
1. Client requests a reset (`POST /forgot-password`) with their username
2. The API always returns the same generic response, whether or not the account exists, so the endpoint can't be used to discover registered usernames
3. If the account exists, a single-use, time-limited reset token is generated and emailed to the user's address via SendGrid
4. Client submits the token and a new password (`POST /reset-password`) → the password is updated and the token is invalidated immediately, win or lose

**Email verification flow:**
1. On registration, the user is created with a `verified` flag set to false, and a single-use, time-limited verification token is emailed to them via SendGrid
2. Registration still succeeds and the user can log in – verification is tracked rather than enforced, so an unverified user is never locked out
3. Client submits the token (`POST /verify-email`) → the user's `verified` flag is set to true and the token is invalidated immediately
4. The `verified` flag is carried on the user record, so specific actions can be gated to verified users in the future

## Features

- Full CRUD for expenses (`Create`, `Read`, `Update`, `Delete`)
- User registration and login with BCrypt password hashing
- Stateless JWT authentication with short-lived access tokens and long-lived refresh tokens
- Per-user data isolation – users can only access their own expenses, enforced server-side
- Self-service password reset via emailed, single-use, expiring tokens (SendGrid)
- Email verification on registration via emailed, single-use, expiring tokens (SendGrid)
- Rate limiting on authentication endpoints to slow down brute-force attempts
- Real PostgreSQL persistence (not in-memory)
- Containerized with a multi-stage Docker build
- Deployed live on Azure (App Service, Container Registry, managed PostgreSQL)
- Application Insights integration for live request and performance monitoring
- Continuous integration via GitHub Actions – tests run automatically on every push and pull request
- Automated test suite covering registration, login, rate limiting, token refresh, and email verification

## API Endpoints

| Method | Endpoint | Auth required | Description |
|---|---|---|---|
| GET | `/` | No | Health check – confirms the API is running |
| POST | `/register` | No | Create a new user account |
| POST | `/login` | No | Authenticate and receive an access + refresh token pair |
| POST | `/refresh` | No | Exchange a valid refresh token for a new access token |
| POST | `/forgot-password` | No | Request a password reset email |
| POST | `/reset-password` | No | Reset a password using a valid reset token |
| POST | `/verify-email` | No | Verify a user's email using a valid verification token |
| GET | `/expenses` | Yes | List the authenticated user's expenses |
| POST | `/expenses` | Yes | Create a new expense |
| GET | `/expenses/{id}` | Yes | Get a single expense by ID |
| PUT | `/expenses/{id}` | Yes | Update an expense |
| DELETE | `/expenses/{id}` | Yes | Delete an expense |

`/register`, `/login`, and `/refresh` are rate-limited to 5 requests per 60 seconds per client.

## Running locally

### Prerequisites
- JDK 21
- Docker (for local PostgreSQL)
- A SendGrid API key, if you want to test the password reset flow (optional – everything else works without it)

### Steps

1. Start a local PostgreSQL instance:
   ```bash
   docker run --name expense-db \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     -e POSTGRES_DB=expensetracker \
     -p 5433:5432 \
     -d postgres
   ```

2. (Optional) Set environment variables for email sending:
   ```bash
   export SENDGRID_API_KEY="your-key-here"
   export SENDGRID_FROM_EMAIL="your-verified-sender@example.com"
   ```

3. Run the application:
   ```bash
   ./gradlew :server:run
   ```

4. The API will be available at `http://localhost:8080`

### Running tests
```bash
./gradlew clean test
```
Tests run against the local PostgreSQL container started above.

### Running with Docker
```bash
docker build -t expense-tracker-api .
docker run -p 8081:8080 \
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5433/expensetracker" \
  -e DATABASE_USER="postgres" \
  -e DATABASE_PASSWORD="postgres" \
  expense-tracker-api
```

## Problems & Solutions

Building and deploying this project surfaced several real issues that aren't obvious from tutorials. Documenting them here, since debugging them was as valuable as writing the original code.

### 1. Shadow JAR ran the wrong entry point

**Problem:** After containerizing the app, `java -jar app.jar` started successfully and bound to the correct port – but the database was never initialized, and every request that touched the database failed with `Please call Database.connect() before using this code`.

**Root cause:** The Gradle `application` block set `mainClass = "io.ktor.server.netty.EngineMain"`, which the Shadow plugin used directly as the JAR's `Main-Class`. This bypassed my actual `fun main()` in `MainKt` entirely – including the `DatabaseFactory.init()` call inside it – because `EngineMain` boots Ktor straight from `application.yaml`'s module list, with no knowledge of my custom `main()` function.

**Fix:** Explicitly overrode the Shadow JAR's manifest:
```kotlin
tasks.shadowJar {
   manifest {
      attributes["Main-Class"] = "com.andrii.MainKt"
   }
}
```

### 2. Docker container couldn't reach the local database

**Problem:** The app worked fine run directly via IntelliJ, but failed to connect to PostgreSQL once containerized, even though the same database was running locally.

**Root cause:** The JDBC URL was hardcoded to `localhost:5433`. Inside a Docker container, `localhost` refers to the container's own network namespace, not the host machine – so there was nothing listening there.

**Fix:** Externalized all database configuration to environment variables with sensible local defaults:
```kotlin
val jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/expensetracker"
```
Locally this falls back to `localhost`; in Docker, `host.docker.internal` is passed in; in Azure, the real managed database URL is passed in. Same code, three environments.

### 3. Exposed's `eq` and `and` operators needed explicit imports

**Problem:** `Unresolved reference 'eq'` and similar errors despite importing `org.jetbrains.exposed.sql.*`.

**Root cause:** Exposed's comparison operators (`eq`, `and`, etc.) are scoped inside `SqlExpressionBuilder` rather than being top-level package members, so the wildcard import doesn't pull them in automatically.

**Fix:** Added explicit imports:
```kotlin
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
```

### 4. Stale boilerplate test broke the build

**Problem:** `./gradlew build` started failing on a test that had nothing to do with any change I'd made.

**Root cause:** The Ktor project generator scaffolds a placeholder test asserting that `GET /` returns `200 OK`. That route was a placeholder I'd already replaced with real CRUD/auth routes back in early development – the test was checking a route that no longer existed.

**Fix:** Replaced the placeholder test with a real test suite covering registration, login (success and failure), rate limiting, and refresh tokens – see [Running tests](#running-tests).

### 5. Docker image built on Apple Silicon failed to deploy to Azure

**Problem:** Azure App Service reported `ImagePullUnauthorizedFailure` / "Check registry credentials" – even after confirming the registry credentials worked correctly via a direct `docker login` test.

**Root cause:** The misleading error obscured the actual issue, buried in the raw container logs: `no matching manifest for linux/amd64 in the manifest list entries`. The image had been built on an Apple Silicon Mac, defaulting to the `arm64` architecture. Azure App Service's Linux containers run on `amd64`, so no compatible image layer existed for it to pull – Azure just reported this as an authorization failure rather than an architecture mismatch.

**Fix:** Rebuilt and pushed the image explicitly targeting the correct platform:
```bash
docker buildx build --platform linux/amd64 -t <registry>/expense-tracker-api:latest --push .
```

### 6. Azure App Service silently ignored registry credentials in favor of a broken managed identity

**Problem:** Even after fixing the architecture issue and re-confirming registry credentials, image pulls kept failing intermittently with conflicting error messages across multiple attempts.

**Root cause:** The App Service had a `acrUseManagedIdentityCreds: true` flag set (likely a default from how it was first created via the Azure Portal wizard), pointing to a user-assigned managed identity that had never been granted `AcrPull` permissions on the registry. This caused App Service to ignore the explicitly configured username/password credentials and attempt – and fail – to authenticate via identity instead.

**Fix:** Recreated the App Service from a clean state via Azure CLI, specifying registry credentials directly at creation time, avoiding the inconsistent state that had accumulated from incremental portal-based configuration changes.

### 7. A stray character in an environment variable silently broke email sending

**Problem:** `POST /forgot-password` returned a successful response, but no email ever arrived, with no error logged anywhere.

**Root cause:** The IntelliJ Run Configuration's `SENDGRID_API_KEY` environment variable had a stray leading `<` character – likely left over from an incompletely replaced placeholder when the value was first pasted in. SendGrid's API responded with `401 Unauthorized` to the malformed key, but since `/forgot-password` is intentionally designed to return the same generic success message regardless of outcome (to avoid leaking which usernames exist), the failure was invisible from the API response and only visible in the server's own console log.

**Fix:** Regenerated a fresh SendGrid API key and pasted it cleanly into the environment variable, with nothing before or after it. A good reminder that "the call succeeded" and "the call did what I wanted" are different claims, especially for endpoints designed to look the same on success and failure.

## Known limitations / future improvements

- Test suite requires a live PostgreSQL container (locally or in CI) rather than mocking the database layer

## Author

Andrii Maksymenko – [github.com/defoltbl](https://github.com/defoltbl)