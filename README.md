![Icon of Vora](icon.png)

# Vora
A video streaming platform, high-performance, non-blocking Authentication & Authorization microservice built using **Kotlin**, **Spring Boot WebFlux**, and **Hexagonal Architecture**.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Reactive](https://img.shields.io/badge/Reactive-WebFlux-blue.svg)](https://projectreactor.io)

## Auth Service
This service handles user registration, secure login, JWT issuance, and Multi-Factor Authentication (MFA) coordination, utilizing Redis for caching/state and PostgreSQL for persistence.

## 🚀 Key Features

*   **Reactive Stack**: Fully asynchronous IO using Project Reactor (Mono/Flux) and R2DBC.
*   **Hexagonal Architecture**: Strict separation between Domain logic, Inbound adapters (Controllers), and Outbound adapters (Persistence, Notifications).
*   **Security**:
    *   RSA Signed JWTs (Access Tokens).
    *   BCrypt Password Hashing.
*   **Multi-Factor Authentication (MFA)**:
    *   **None**: Standard Email/Password login.
    *   **Authenticator App**: TOTP generation (Google Authenticator) with QR Code generation.
    *   **Email OTP**: Time-sensitive verification codes via SMTP.
*   **Performance Optimizations**:
    *   **Bloom Filters (Redis)**: Rapid existence checks during registration to prevent database hits for existing emails.
    *   **Dead Letter Queue (DLQ)**: Robust error handling for Bloom Filter updates using Redis Streams.
*   **Resilience**: Rate limiting on OTP verification attempts.

## 🛠 Tech Stack

*   **Language**: Kotlin
*   **Framework**: Spring Boot 3 (WebFlux, Security)
*   **Database**: PostgreSQL (R2DBC)
*   **Caching/State**: Redis (Reactive), Redisson
*   **Token**: JJWT (Java JWT)
*   **QR Code**: ZXing
*   **Build Tool**: Gradle
*   **Testing**: JUnit 5, Mockk, Testcontainers, WebTestClient

## 📂 Architecture Overview

The project follows the Ports and Adapters (Hexagonal) pattern:

*   **Domain**: Contains core models (`User`, `MFAOptions`) and business rules. Dependency-free.
*   **Application**: Implements Use Cases (`LoginService`, `RegisterService`).
*   **Ports**: Interfaces defining how the outside world interacts with the domain.
*   **Infrastructure**:
    *   **Inbound**: REST Controllers.
    *   **Outbound**: Implementation of interfaces for PostgreSQL (`UserRepository`), Redis (`BloomFilter`, `OtpStorage`), and Email.

## ⚙️ Configuration & Setup

### Prerequisites

*   JDK 21+
*   Docker (for Redis and PostgreSQL)
*   OpenSSL (to generate RSA keys)

### 1. RSA Key Generation

The service requires an RSA key pair to sign and verify JWTs. Generate them in a specific directory (e.g., `certs/`):

```bash
# Generate Private Key
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:2048

# Generate Public Key
openssl rsa -pubout -in private.pem -out public.pem
```

*Note: Ensure the path to these keys matches the `jwt.rsa-key-pair-path` property.*

### 2. Environment Variables

Create an `application.yml` or set these environment variables:

| Variable                  | Description                             | Default                                    |
|:--------------------------|:----------------------------------------|:-------------------------------------------|
| `SPRING_R2DBC_URL`        | PostgreSQL R2DBC Connection URL         | `r2dbc:postgresql://localhost:5432/authdb` |
| `SPRING_DATA_REDIS_HOST`  | Redis Host                              | `localhost`                                |
| `JWT_RSA_KEY_PAIR_PATH`   | Path to directory containing .pem files | `/path/to/certs`                           |
| `APP_ISSUER`              | Name of the app for TOTP Apps           | `Ethyllium`                                |
| `NOTIFICATION_EMAIL_FROM` | Sender email for OTPs                   | `no-reply@ethyllium.com`                   |
| `SPRING_MAIL_HOST`        | SMTP Server Host                        | `smtp.gmail.com`                           |
| `SPRING_MAIL_USERNAME`    | SMTP Username                           | -                                          |
| `SPRING_MAIL_PASSWORD`    | SMTP Password                           | -                                          |

## 🏃 How to Run

### Using Gradle

```bash
./gradlew bootRun
```

### Using Docker Compose (Recommended)

*Ensure you have a `docker-compose.yml` set up for Postgres and Redis.*

```bash
docker-compose up -d
```

## 🛣️ Upcoming Milestones

The following features and improvements are planned to complete the Authentication Service:

### Phase 1: Token Lifecycle Management (High Priority)
- [ ] **Refresh Token Endpoint**: Although `generateRefreshToken` exists in the service, the API endpoint (`/api/v1/refresh-token`) to exchange a Refresh Token for a new Access Token is missing.
- [ ] **Logout Mechanism**: Implement a "Blacklist" strategy (using Redis with TTL) to invalidate JWTs before their natural expiry.
- [ ] **Token Revocation**: Admin ability to revoke all tokens for a specific user (e.g., in case of compromise).

### Phase 2: User Account Management
- [ ] **Forgot Password Flow**: Implement `initiate-reset` (sends email) and `confirm-reset` (updates DB) endpoints.
- [ ] **Email Verification**: Complete the loop for `MFAOptions.EMAIL` registration. Currently, it sends an email but the specific endpoint to verify the link/token and activate the user is required.
- [ ] **Profile Management**: Endpoints to change password or update email.

### Phase 3: Reliability & Monitoring
- [ ] **Rate Limiting**: Implement global API rate limiting (e.g., Bucket4j + Redis) to protect the `/login` and `/register` endpoints from brute force, beyond the current OTP attempt counter.
- [ ] **Distributed Tracing**: Integrate Micrometer/Zipkin for tracing requests across the reactive chain.
- [ ] **Health Checks**: Add custom Spring Actuator health indicators for Redis Bloom Filter and SMTP connectivity.

### Phase 4: Expansion
- [ ] **OAuth2 / OIDC Support**: Add support for "Login with Google/GitHub" to act as an Identity Provider wrapper.
- [ ] **Role-Based Access Control (RBAC)**: Expand the `User` model and JWT claims to include Roles/Authorities for downstream services to consume.
- [ ] **HTML Email Templates**: Replace the current hardcoded string bodies in `SmtpNotificationAdapter` with Thymeleaf or FreeMarker templates for professional emails.

## 🧪 Testing

The project maintains high test coverage using:
*   **Unit Tests**: `Mockk` for service layer isolation.
*   **Integration Tests**: `Testcontainers` (PostgreSQL) for repository layer verification.
*   **WebFlux Tests**: `WebTestClient` for controller endpoints.

Run tests via:
```bash
./gradlew test
```

---
*Built with ❤️ by the Ethyllium Team.*