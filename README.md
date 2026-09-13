<div align="center">

# 🚕 RideLink

### Backend microservices for a fictional ride-sharing platform

*Built for IT3130 — Application Development · SLIIT*

</div>

---

## 🧭 What is RideLink?

RideLink is a fictional ride-sharing service — think of it as the plumbing behind a taxi-booking app. No real drivers, no real payments, no real maps. Just a clean, honest implementation of microservices done *properly*: independent services, independent databases, and honest-to-god HTTP calls between them (with honest-to-god failure handling when one of them goes down).

Passengers request rides. Drivers go online and get assigned. Fares get calculated. Payments get recorded. Nobody's actual credit card is ever touched.

---

## 🏗️ Architecture

Four services. Four databases. Zero shared tables. Every arrow below is a real REST call, not a shortcut.

```
                        ┌─────────────────────┐
                        │   Account Service    │   :8081
                        │  auth · roles · JWT  │
                        └──────────┬───────────┘
                                   │ issues tokens
                                   ▼
        ┌──────────────────────────────────────────────┐
        │                                                │
┌───────┴────────┐   GET /available    ┌────────────────┴───┐
│  Ride Service   │ ──────────────────▶ │  Driver & Vehicle   │
│     :8083       │ ◀────────────────── │      Service         │
│  ride lifecycle │   PATCH availability│       :8082          │
└───────┬─────────┘                     └──────────────────────┘
        │
        │ triggers fare calc
        ▼
┌────────────────────┐
│ Fare & Payment      │   :8084
│ estimate · receipts │
└─────────────────────┘
```

Each box owns its own data. Nobody reaches into anyone else's database. If a service goes down, its neighbors notice and fail *gracefully* — a clean error and a clear message, not a crash or a hang.

---

## 🧩 The Services

| # | Service | Port | Owns |
|---|---------|:----:|------|
| 1 | 🔐 **Account Service** | `8081` | Registration, login, JWT, roles, profile, account status |
| 2 | 🚗 **Driver & Vehicle Service** | `8082` | Driver profiles, vehicles, availability, service area |
| 3 | 🧭 **Ride Management Service** | `8083` | Ride requests, driver assignment, ride lifecycle |
| 4 | 💳 **Fare & Payment Service** | `8084` | Fare estimates, final fare, simulated payments, receipts |

---

## ⚙️ Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4.1.1
- **Build tool:** Maven
- **Database:** H2 (in-memory, one instance per service)
- **Communication:** Synchronous REST over HTTP (`RestTemplate`)
- **Auth:** JWT, issued by Account Service, validated by each protected service
- **API testing:** Postman / Swagger UI (no frontend required — this is a backend-only assignment)
- **IDE:** IntelliJ IDEA

---

## 🚀 Getting Started

Each service is its own independent Spring Boot app. Start them **in this order** so interservice calls have something to talk to:

```bash
# 1. Account Service — auth for everything else
cd account-service
mvn spring-boot:run          # → http://localhost:8081

# 2. Driver & Vehicle Service — no dependencies
cd driver-service
mvn spring-boot:run          # → http://localhost:8082

# 3. Ride Management Service — depends on Driver Service
cd ride-service
mvn spring-boot:run          # → http://localhost:8083

# 4. Fare & Payment Service — depends on Ride Service
cd fare-payment-service
mvn spring-boot:run          # → http://localhost:8084
```

Each service spins up its own H2 console at `http://localhost:<port>/h2-console` for peeking at the data (default JDBC URL: `jdbc:h2:mem:testdb`).

---

## 📡 API Overview

### 🔐 Account Service — `:8081`

| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/auth/register` | Register a passenger or driver account |
| `POST` | `/api/auth/login` | Log in, receive a JWT |
| `GET` | `/api/accounts/{id}` | View a profile |
| `PATCH` | `/api/accounts/{id}` | Update a profile |
| `PATCH` | `/api/accounts/{id}/status` | Admin-only: activate/suspend an account |

### 🚗 Driver & Vehicle Service — `:8082`

| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/drivers` | Register a new driver |
| `GET` | `/api/drivers/available` | List all available drivers |
| `PATCH` | `/api/drivers/{id}/availability` | Toggle a driver's availability |

### 🧭 Ride Management Service — `:8083`

| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/rides` | Request a ride (auto-assigns an available driver) |
| `GET` | `/api/rides/{id}` | View a ride's details |
| `PATCH` | `/api/rides/{id}/complete` | Mark a ride as completed |

### 💳 Fare & Payment Service — `:8084`

| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/fares/estimate` | Get a fare estimate for a pickup/destination |
| `POST` | `/api/payments` | Record a simulated payment |
| `GET` | `/api/payments/{id}/receipt` | Retrieve a receipt |

---

## 🧪 Testing the Full Flow

The whole point is proving the services actually *talk* to each other. Here's the golden path:

```
1. Register a passenger and a driver    → Account Service + Driver & Vehicle Service
2. Request a ride                        → Ride Service calls Driver Service, assigns a driver
3. Complete the ride                      → Ride Service marks it COMPLETED
4. Calculate the final fare               → Fare & Payment Service
5. Record the payment                      → Fare & Payment Service, receipt generated
```

...and the two required failure paths:

```
❌ No drivers available    → 409 CONFLICT — { "code": "NO_DRIVER_AVAILABLE" }
❌ Driver Service is down  → 503 SERVICE UNAVAILABLE — { "code": "DRIVER_SERVICE_UNAVAILABLE" }
```

Every error response follows the same shape across all four services:
```json
{
  "code": "SOME_STABLE_ERROR_CODE",
  "message": "A human-readable explanation, no stack traces."
}
```

---

## 👥 Team

| Service | Owner |
|---------|-------|
| Account Service | *Member 1* |
| Driver & Vehicle Service | *Member 2* |
| Ride Management Service | *Member 3* |
| Fare & Payment Service | *Member 4* |

---

## 📌 Notes

- No frontend is required for this assignment — Swagger UI and Postman are the official interfaces.
- No real payments, maps, or third-party services are involved anywhere. Everything is simulated, on purpose.

<div align="center">

*Made with ☕, `mvn spring-boot:run`, and a deadline.*

</div>
