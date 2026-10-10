<div align="center">
<h1 align="center">🚕 RideLink</h1>
<p align="center">
  <img src="assets/ridelink-banner.png" alt="RideLink - A dispatch board for your city" width="800">
</p>
<h3 align="center">Microservices behind a fictional ride-sharing platform — built, broken, and rebuilt until it actually worked.</h3>
 
<p align="center"><em>IT3130 — Application Development · SLIIT</em></p>
 
[![CI](https://github.com/ImalkaDevHub/Ridelink/actions/workflows/ci.yml/badge.svg)](https://github.com/ImalkaDevHub/Ridelink/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-47A248?logo=mongodb&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-Frontend-black?logo=nextdotjs&logoColor=white)
![Deployed](https://img.shields.io/badge/Deployed-Render%20%2B%20Vercel-purple)
 
</div>
---
 
## 🧭 What is RideLink?
 
RideLink is a fictional ride-sharing platform — the plumbing behind a taxi-booking app, minus the taxis. No real drivers, no real payments, no real maps. What *is* real: four independent microservices, four independent databases, honest HTTP calls between them, and honest failure handling when one of them falls over.
 
A passenger requests a ride. A driver goes online and gets matched. A fare gets calculated. A payment gets simulated and recorded. Nobody's card is ever touched, and nobody's database is ever touched by a service that doesn't own it.
 
> 🎁 **Bonus round:** what started as a backend-only assignment grew a Next.js frontend, a Dockerized local stack, and a live cloud deployment (Render + Vercel + MongoDB Atlas) — none of which was required, all of which was too fun to leave out.
 
---
 
## 🏗️ Architecture
 
Four services. Four databases. Zero shared tables. Every arrow below is a real REST call over the network, not a shared method call in disguise.
 
```
                        ┌─────────────────────┐
                        │   Account Service    │   :8081
                        │  auth · roles · JWT  │
                        └──────────┬───────────┘
                                   │ issues tokens (shared secret)
                                   ▼
        ┌──────────────────────────────────────────────┐
        │                                                │
┌───────┴────────┐   GET /available    ┌────────────────┴───┐
│  Ride Service   │ ──────────────────▶ │  Driver & Vehicle    │
│     :8083       │ ◀────────────────── │      Service          │
│  ride lifecycle │  PATCH availability │       :8082           │
└───────┬─────────┘                     └───────────────────────┘
        │
        │ POST /api/payments/finalize
        ▼
┌────────────────────┐
│ Fare & Payment      │   :8084
│ estimate · receipts │
└─────────────────────┘
 
        ┌─────────────────────┐         ┌──────────────────────┐
        │  MongoDB (Atlas or   │◀───────▶│  4 databases:         │
        │  local via Docker)   │         │  account_db · driver_db│
        └─────────────────────┘         │  ride_db · payment_db │
                                          └──────────────────────┘
```
 
Each box owns its own data — literally its own MongoDB database, not just its own collection. Nobody reaches into a neighbor's database. If a service goes down mid-flow, the caller gets a clean `503` and a clear message, not a hang, not a corrupted ride sitting half-paid.
 
---
 
## 🧩 The Services
 
| # | Service | Port | Owns |
|---|---------|:----:|------|
| 1 | 🔐 **Account Service** | `8081` | Registration, login, JWT issuance, roles, profile, account status |
| 2 | 🚗 **Driver & Vehicle Service** | `8082` | Driver profiles, vehicles, availability, eligible-driver lookup |
| 3 | 🧭 **Ride Management Service** | `8083` | Ride requests, driver assignment, ride lifecycle |
| 4 | 💳 **Fare & Payment Service** | `8084` | Fare estimates, final fare calc, simulated payments, receipts |
 
---
 
## ⚙️ Tech Stack
 
| Layer | Choice |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 4.1.1 |
| **Build tool** | Maven |
| **Database** | MongoDB — one database per service (`account_db`, `driver_db`, `ride_db`, `payment_db`). Local via Docker Compose, cloud via MongoDB Atlas |
| **Interservice comms** | Synchronous REST over HTTP (`RestTemplate`) |
| **Auth** | JWT, issued by Account Service, validated by every protected endpoint via a shared signing secret |
| **API docs** | springdoc-openapi → live Swagger UI per service |
| **Containerization** | Docker (multi-stage builds) + Docker Compose for the whole stack |
| **CI** | GitHub Actions — matrix build across all 4 services on every push |
| **Frontend** *(bonus)* | Next.js — passenger & driver dashboards |
| **Cloud hosting** *(bonus)* | Render (backend services) · Vercel (frontend) · MongoDB Atlas (database) |
 
---
 
## 🚀 Getting Started
 
### Option A — Docker Compose (recommended, one command)
 
Spins up MongoDB and all four services together, with data that survives restarts.
 
```bash
git clone https://github.com/ImalkaDevHub/Ridelink.git
cd Ridelink
docker compose up -d --build
```
 
| Service | URL |
|---|---|
| Account Service | http://localhost:8081 |
| Driver & Vehicle Service | http://localhost:8082 |
| Ride Management Service | http://localhost:8083 |
| Fare & Payment Service | http://localhost:8084 |
| MongoDB | `mongodb://localhost:27017` |
 
Stop everything with `docker compose down` (data persists in a named volume — add `-v` to wipe it).
 
### Option B — Run each service manually
 
Start a MongoDB instance first (either `docker run -d -p 27017:27017 mongo:7`, or point at an Atlas cluster), then run each service **in this order** so interservice calls have something to talk to:
 
```bash
# 1. Account Service — auth for everything else
cd account-service/account-service
./mvnw spring-boot:run          # → http://localhost:8081
 
# 2. Driver & Vehicle Service — no dependencies
cd driver-service/driver-service
./mvnw spring-boot:run          # → http://localhost:8082
 
# 3. Ride Management Service — depends on Driver Service
cd ride-service/ride-service
./mvnw spring-boot:run          # → http://localhost:8083
 
# 4. Fare & Payment Service — depends on Ride Service
cd fare-payment-service/fare-payment-service
./mvnw spring-boot:run          # → http://localhost:8084
```
 
> 📁 Each service's Maven project lives one folder deeper than its top-level directory name (a leftover from the original Spring Initializr scaffold) — adjust the `cd` path if yours has been flattened since.
 
By default each service connects to `mongodb://localhost:27017/<service>_db`. Set the `SPRING_DATA_MONGODB_URI` environment variable to override this with an Atlas connection string — no code changes needed either way.
 
### Frontend (optional)
 
```bash
cd frontend
npm install
npm run dev          # → http://localhost:3000
```
 
Create a `.env.local` pointing at whichever backend you're running:
 
```bash
NEXT_PUBLIC_ACCOUNT_API_URL=http://localhost:8081
NEXT_PUBLIC_DRIVER_API_URL=http://localhost:8082
NEXT_PUBLIC_RIDE_API_URL=http://localhost:8083
NEXT_PUBLIC_FARE_API_URL=http://localhost:8084
```

### Running the project with scripts

Bash scripts in `scripts/` run the four services and the frontend locally, without Docker. They work in Git Bash on Windows, Linux and macOS. Start MongoDB first, for example with `docker compose up -d mongodb`.

```bash
./scripts/start-all.sh            # start everything in dependency order, waiting for each port
./scripts/health-check.sh         # one-shot UP/DOWN table (exit 1 if anything is down)
./scripts/health-check.sh --watch # live dashboard, alerts on UP/DOWN changes (--interval N)
./scripts/stop-all.sh             # stop all five components and free their ports
```

Output from each component goes to `logs/<service>.log`. Ports, folders and start order are set in `scripts/config.sh`.

### 📚 Swagger UI
 
Once a service is running, its live API docs are at:
 
```
http://localhost:<port>/swagger-ui/index.html
```
 
---
 
## ☁️ Live Deployment (bonus, not graded)
 
The full stack is also deployed and reachable online — Render for the four Spring Boot services, Vercel for the frontend, MongoDB Atlas for the database.
 
| Component | Hosted on |
|---|---|
| Backend services (×4) | Render (Docker) |
| Frontend | Vercel |
| Database | MongoDB Atlas |
 
> ⏳ Render's free tier sleeps a service after 15 minutes of inactivity — the first request after a while can take 30–50 seconds to wake it back up. Perfectly normal, not a bug.
 
---
 
## 📡 API Overview
 
### 🔐 Account Service — `:8081`
 
| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/auth/register` | Register a passenger or driver account |
| `POST` | `/api/auth/login` | Log in, receive a JWT |
| `GET` | `/api/accounts/{id}` | View a profile (password never returned) |
| `PATCH` | `/api/accounts/{id}` | Update a profile |
| `PATCH` | `/api/accounts/{id}/status?status=` | Admin-only: activate/suspend an account |
 
### 🚗 Driver & Vehicle Service — `:8082`
 
| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/drivers` | Register a new driver/vehicle profile |
| `GET` | `/api/drivers/available` | List currently available drivers |
| `PATCH` | `/api/drivers/{id}/availability` | Toggle availability (also used internally to reserve a driver on assignment) |
 
### 🧭 Ride Management Service — `:8083`
 
| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/rides` | Request a ride (auto-assigns an available driver) |
| `GET` | `/api/rides/{id}` | View a ride's details |
| `PATCH` | `/api/rides/{id}/complete` | Complete a ride — triggers final fare calculation |
 
### 💳 Fare & Payment Service — `:8084`
 
| Method | Endpoint | Description |
|--------|----------|--------------|
| `POST` | `/api/fares/estimate` | Estimate a fare for a pickup/destination (public) |
| `POST` | `/api/fares/final` | Store a ride's final fare with its breakdown — idempotent per `rideId` (DRIVER/ADMIN) |
| `POST` | `/api/payments` | Pay a ride's final fare: `{rideId, method: CARD\|CASH, cardLast4?}` (PASSENGER/ADMIN) |
| `GET` | `/api/payments/{id}` | One payment |
| `GET` | `/api/payments/ride/{rideId}` | Every payment attempt for a ride |
| `GET` | `/api/payments/{id}/receipt` | Receipt (receipt number, breakdown, method, status, timestamp). Also accepts a `rideId` |
| `POST` | `/api/payments/finalize` | Interservice (legacy): final fare + a simulated, already-collected payment (DRIVER/ADMIN) |
| `GET` | `/api/payments/{rideId}/status` | The ride's paid payment, or its latest attempt |

#### Fare rule

All amounts are in **LKR**, calculated with `BigDecimal` and stored as MongoDB `Decimal128`:

```
fare = max(minimumFare, baseFare + perKm × distanceKm + perMin × durationMin) × multiplier[vehicleType]
```

rounded to 2 decimal places (half-up). Defaults, all configurable in `fare-payment-service/.../application.properties` (`fare.*`):

| Setting | Default | | Vehicle type | Multiplier |
|---|---:|---|---|---:|
| `fare.base-fare` | 300 | | `SEDAN` | 1.0 |
| `fare.per-km` | 120 | | `HATCHBACK` | 0.9 |
| `fare.per-min` | 10 | | `VAN` | 1.5 |
| `fare.minimum-fare` | 500 | | `TUK` | 0.8 |
| | | | `CAR` / `BIKE` *(kept for existing clients)* | 1.0 / 0.6 |

Example: a 5 km, 12 min sedan ride is `max(500, 300 + 600 + 120) × 1.0 = 1020.00 LKR`; the same trip by van is `1530.00 LKR`.

For an **estimate**, the distance comes from `distanceKm` if given, else from pickup/destination coordinates (Haversine, straight-line), else from a small built-in table of place names (SLIIT, Malabe Junction, Colombo Fort, Kaduwela, Kandy, …). Without `durationMin`, the duration is estimated at `fare.average-speed-kmh` (25 km/h).

**Payments** always charge the stored final fare — the client never sends an amount. Paying before the fare is finalized returns `409 FARE_NOT_FINALIZED`; a card ending in `0000` is a simulated decline (`402 PAYMENT_FAILED`, the attempt is saved as `FAILED` and can be retried); a second payment for a paid ride returns `409 PAYMENT_ALREADY_EXISTS`.
 
---
 
## 🧪 Testing the Full Flow
 
The whole point is proving the services actually *talk* to each other. Here's the golden path:
 
```
1. Register a passenger and a driver     → Account Service
2. Driver goes online                     → Driver & Vehicle Service
3. Passenger requests a ride              → Ride Service calls Driver Service, assigns a driver
4. Driver completes the ride              → Ride Service calls Fare & Payment Service
5. Final fare is calculated & recorded    → Fare & Payment Service
6. Receipt is retrieved                    → Fare & Payment Service
```
 
...and the negative scenarios, all returning the same clean error shape:
 
```json
{ "code": "SOME_STABLE_ERROR_CODE", "message": "A human-readable explanation, no stack traces." }
```
 
| Scenario | Response |
|---|---|
| No available driver | `409` — `NO_DRIVER_AVAILABLE` |
| Driver Service unreachable | `503` — `DRIVER_SERVICE_UNAVAILABLE` |
| Invalid ride status transition | `409` — `INVALID_STATUS_TRANSITION` |
| Ride already paid | `409` — `DUPLICATE_PAYMENT` |
| Fare & Payment Service unreachable during completion | `503` — `PAYMENT_FAILED` (ride stays unpaid, never silently marked complete) |
 
---
 
## 🐳 Project Layout
 
```
Ridelink/
├── account-service/          # Account Service (auth, JWT, roles)
├── driver-service/           # Driver & Vehicle Service
├── ride-service/             # Ride Management Service
├── fare-payment-service/     # Fare & Payment Service
├── frontend/                 # Next.js dashboards (bonus)
├── docker-compose.yml        # Spins up MongoDB + all 4 services
└── .github/workflows/ci.yml  # CI: build + test all 4 services on every push
```
 
---
 
## 👥 Team
 
| Service | Owner |
|---------|-------|
| Account Service | *IT24102603 – Rajapaksha K.M.P* |
| Driver & Vehicle Service | *IT24102673 – Sathsarani R.D.S* |
| Ride Management Service | *IT24102531 - Nishshanka N.P.I.M* |
| Fare & Payment Service | *IT24102402 - Munasinghe M.S.D* |
 
---
 
## 📌 Notes
 
- A frontend, Docker deployment, and cloud hosting are all **optional enhancements** per the assignment brief and carry no separate marks — the four Spring Boot services, their tests, and their CI pipeline are what's actually assessed. Everything here is built regardless because we couldn't help ourselves.
- No real payments, maps, or third-party services are involved anywhere. Everything is simulated, on purpose.
- Each service maintains its **own** database — no table, collection, or connection is shared across services, by design.
<div align="center">
*Made with ☕, `docker compose up`, and a deadline that kept moving closer.*
 
</div>
 
