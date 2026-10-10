# RideLink — Gap Report

Read-only audit of `main` @ `c94759b` (branch `feature/gap-fixes`), 2026-10-10. No code was changed.
Findings come from reading the code. Builds and tests were **not** run.

**Path prefixes used in the Evidence column**

| Prefix | Expands to |
|---|---|
| `A/` | `account-service/account-service/src/main/java/com/ridelink/account_service/` |
| `D/` | `driver-service/driver-service/src/main/java/com/ridelink/driver_service/` |
| `R/` | `ride-service/ride-service/src/main/java/com/ridelink/ride_service/` |
| `F/` | `fare-payment-service/fare-payment-service/src/main/java/com/ridelink/fare_payment_service/` |
| `*/props` | that service's `src/main/resources/application.properties` |
| `*/test/` | that service's `src/test/java/com/ridelink/<pkg>/` |
| `FE/` | `frontend/` |

Status key: **Done** = meets the requirement · **Partial** = exists but incomplete or incorrect · **Missing** = not present.

---

## 1. Account Service (:8081)

| Item | Status | Evidence |
|---|---|---|
| Validation: name / email / password rule | Partial | `A/dto/RegisterRequest.java:9-18`: name `@NotBlank`, email `@Email`, password only `@Size(min=6)`. No max lengths, no password-strength rule, no name length limit. |
| Duplicate email → 409 | Partial | `A/service/AccountService.java:30-31` + `A/controller/AccountController.java:48-49` return 409 `DUPLICATE_EMAIL`. Check-then-insert can race: `@Indexed(unique=true)` (`A/model/Account.java:15`) isn't created because `spring.data.mongodb.auto-index-creation` is not enabled, and a `DuplicateKeyException` would become a 500 anyway. Email isn't normalised (case or whitespace). |
| Registration limited to PASSENGER/DRIVER | Done | `A/service/AccountService.java:113-124`, 400 `INVALID_ROLE` at `AccountController.java:50-51`. |
| Seeded admin | Missing | No seeder or `CommandLineRunner` creates an ADMIN, so `PATCH /api/accounts/{id}/status` (`AccountController.java:99-100`) can't be used. |
| Password-change endpoint (verify current, new ≠ old, owner only) | Missing | No such endpoint or DTO. `ProfileUpdateRequest.java:6-7` explicitly excludes passwords. |
| Generic login error | Done | `AccountService.java:49,52`: same "Invalid email or password." for unknown email and wrong password. |
| SUSPENDED blocked at login | Partial | `AccountService.java:55-57` → 403 `ACCOUNT_SUSPENDED`. Tokens issued before suspension stay valid until expiry, because no service checks status (`A/security/JwtAuthFilter.java:39-46`). |
| Ownership rules on profile endpoints | Missing | `AccountController.java:72-97`: `GET` and `PATCH /api/accounts/{id}` have no owner-or-admin check, so any authenticated user can read or edit any account. |
| `/me` endpoint | Missing | No `/api/accounts/me` mapping in `AccountController.java`. |
| JWT claims + expiry | Done | `A/security/JwtUtil.java:27-40`: sub=email, `accountId`, `email`, `role`, iat, exp. `jwt.expiration-ms=3600000` (`A/props:5`). |
| JWT secret from env | Missing | Secret hard-coded in `A/props:4`, and the same literal appears in D/R/F props. Needs `${JWT_SECRET:dev-only-…}`. |
| Mongo URI from `MONGODB_URI` | Missing | `A/props:7`: `spring.mongodb.uri=mongodb://localhost:27017/ridelink_account` is hard-coded. The database name is correct. |
| Unit tests | Missing | Only `A/test/AccountServiceApplicationTests.java:6-11` (`@SpringBootTest contextLoads`). No service or controller tests. |
| Leftover config | Partial | `/h2-console/**` permitted (`A/security/SecurityConfig.java:80`) with no H2 present. `MongoDbPrinter.java:15-17` uses `System.out`. |

## 2. Driver & Vehicle Service (:8082)

| Item | Status | Evidence |
|---|---|---|
| 404 `DRIVER_NOT_FOUND` (not 500) | Partial | Done for availability and `/me` (`D/controller/DriverController.java:58-59,85-86`, `D/service/DriverService.java:38-39`). There's no get-by-id endpoint where it would also apply, and no catch-all handler (`D/exception/GlobalExceptionHandler.java`), so any other exception becomes Boot's default 500 body. |
| Validation | Partial | `D/model/Driver.java:18-34`: `@NotBlank` on name, email, vehicleNumber, vehicleType, serviceArea. No `@Email`, no vehicleType whitelist, no lat/lng range. The entity is bound directly as the request body (`DriverController.java:29`), so a client can set `id`, `available` and `accountId` (accountId is overwritten). |
| Unique vehicle number | Missing | No index or check on `vehicleNumber` (`Driver.java:25-26`). The email `@Indexed(unique=true)` (`Driver.java:21`) has the same auto-index problem as Account. One account can create several driver profiles (`DriverService.java:16-18`). |
| Get-by-id | Missing | No `GET /api/drivers/{id}` in `DriverController.java`. |
| Update profile | Missing | No PUT/PATCH for profile fields. |
| Location update | Missing | `currentLat` and `currentLng` exist (`Driver.java:36-37`), but no endpoint sets them. |
| Service-area update | Missing | No endpoint. |
| Eligible filter | Partial | `DriverController.java:43-50` + `D/repository/DriverRepository.java:9-10`: filters by available and optional vehicle type only. No service-area or account-status filtering. |
| ATOMIC reserve + release (race-free) | Missing | `DriverService.java:37-42` is read-modify-write (`findById` → `setAvailable` → `save`). Ride picks the first available driver, then PATCHes `available=false` (`R/client/DriverClient.java:35-41`), so two concurrent requests can book the same driver. Needs `findAndModify` with `available:true` in the query plus dedicated `/reserve` and `/release` endpoints. |
| Roles | Partial | `GET /api/drivers` is ADMIN-only (`DriverController.java:36`) and `/me` is DRIVER-only (`:53`). `POST /api/drivers` accepts any role (`:28-33`). `PATCH /{id}/availability` allows anonymous calls (`:76`, `D/security/SecurityConfig.java:83`), and any DRIVER can change any other driver's availability (no owner check). |
| Unit tests need no MongoDB | Missing | `D/test/DriverServiceApplicationTests.java:11-17` and `D/test/repository/DriverRepositoryTest.java:29-35` use Testcontainers (Docker + Mongo), so `mvnw verify` fails locally without Docker. `DriverServiceTest.java` (Mockito) is fine. |

## 3. Ride Management Service (:8083)

| Item | Status | Evidence |
|---|---|---|
| Status enum | Missing | `R/model/Ride.java:36`: `String status` with the states listed only in a comment. |
| Allowed-transition map → 409 `INVALID_STATUS_TRANSITION` | Partial | Only an inline check in `completeRide` (`R/service/RideService.java:74-77`), mapped at `R/controller/RideController.java:104-105`. No central transition map. |
| accept / start / cancel (all 6 states reachable) | Missing | Only `POST /api/rides` (→ `ASSIGNED` directly, `RideService.java:45`) and `PATCH /{id}/complete` exist. `REQUESTED`, `ACCEPTED`, `IN_PROGRESS` and `CANCELLED` can't be reached. |
| Driver released on complete/cancel | Missing | `completeRide` (`RideService.java:71-100`) never calls Driver to set `available=true`, so after one ride the driver stays unavailable for good. |
| Interservice call to Fare: estimate | Missing | `R/client/FareClient.java` only has `finalizePayment` and `getPaymentStatus`. The client sends `fare` in the ride body (`FE/app/passenger/page.tsx:89-90`), and Ride stores whatever it's given. |
| Interservice call to Fare: final | Done | `FareClient.java:27-49`, used at `RideService.java:83-90`. |
| Duplicate-payment handling | Partial | `RideService.java:91-92` swallows `DuplicatePaymentException` and marks the ride COMPLETED. `PaymentAlreadyExistsException` is never thrown, so the documented 409 `DUPLICATE_PAYMENT` (`RideController.java:106-107`) can't happen. |
| passengerId from JWT | Done | `RideService.java:41-42`. Any role can create a ride, though, including DRIVER and ADMIN (no `@PreAuthorize` on `RideController.java:32-48`). |
| History endpoints | Partial | `GET /api/rides?passengerId=&driverId=&status=` (`RideController.java:62-84`). No ownership check, so any user can list anyone's rides. No `/me` variant. With no parameters it returns an empty list. Sorting is by `_id` (`R/repository/RideRepository.java:9-10`). |
| Client timeouts | Missing | `R/config/AppConfig.java:12-13`: `new RestTemplate(new JdkClientHttpRequestFactory())` sets no connect or read timeout. |
| `@RestControllerAdvice` | Partial | `R/exception/GlobalExceptionHandler.java` exists, but `RideController.java:57-58,110-111` catch every `RuntimeException` and return 404 `RIDE_NOT_FOUND`. `getRide` throws a bare `RuntimeException` (`RideService.java:51`). No catch-all for other exceptions. |
| Ride ownership on GET / complete | Missing | `GET /{id}` has no ownership check. `complete` checks the role only, and any DRIVER can complete any ride (acknowledged in `RideController.java:86-91`). |
| Unit tests | Partial | `R/test/service/RideServiceTest.java` covers request and complete with Mockito. No transition-map, cancel or release tests, because those features don't exist yet. |

## 4. Fare & Payment Service (:8084)

| Item | Status | Evidence |
|---|---|---|
| Documented fare rule | Partial | Formula documented in `F/util/FareCalculator.java:8-14`, with base rates in `F/props:10-13`. The Bike, Tuk and Van rates are hard-coded (`FareCalculator.java:44-56`, duplicated at `:69-81`). Unknown vehicle types silently use the default rates. |
| Configurable | Partial | Only the default tier is configurable. Per-vehicle rates aren't. |
| BigDecimal money | Missing | `double` everywhere: `FareCalculator.java:22-24,58-59`, `F/model/Payment.java:21`, `F/model/FareEstimate.java:23`, DTOs, and Ride's `Double fare` (`R/model/Ride.java:40`). |
| Estimate | Done | `POST /api/fares/estimate` (`F/controller/FareController.java:34-41`, `F/service/FareService.java:31-53`). The `FareEstimate.rideId` field (`FareEstimate.java:16`) is never set, so an estimate can't be linked to a ride. |
| Final fare | Partial | `FareService.java:57-77` recalculates from the distance and duration the **caller** supplies. Any authenticated user can call `POST /api/payments/finalize` directly (`FareController.java:46-57`, no role check) with any amounts. |
| Payment: one per ride | Partial | Check-then-insert (`FareService.java:58-60`). `@Indexed(unique=true)` on rideId (`Payment.java:16`) isn't auto-created, so it can race. |
| Payment amount from stored fare | Missing | The amount is recalculated from request parameters rather than taken from a stored estimate or final fare. |
| Simulated failure (card `0000`) | Missing | No card input. Every payment is set to COMPLETED (`FareService.java:72-73`). |
| Separate pay step | Missing | Payment is created only as a side effect of ride completion. There's no passenger-initiated pay endpoint. |
| Statuses | Partial | `PENDING`, `COMPLETED` and `FAILED` exist (`F/model/PaymentStatus.java:3-6`), but `PENDING` and `FAILED` are never assigned. |
| Receipt retrieval | Done | `GET /api/payments/{rideId}/receipt` (`FareController.java:71-81`, `FareService.java:83-102`). No ownership check: any authenticated user can read any receipt. |
| Unit tests | Done | `F/test/util/FareCalculatorTest.java` (normal, boundary, rounding and failure cases) and `F/test/service/FareServiceTest.java`. These will need extending for BigDecimal and payment failure. |

## 5. Cross-cutting (all services)

| Item | Status | Evidence |
|---|---|---|
| JWT validation in Driver/Ride/Fare | Done | `D|R|F/security/JwtAuthFilter.java` + `JwtUtil.parseClaims`. An invalid token is treated as anonymous. On Driver's anonymous-allowed availability PATCH, that means a bad token still gets through (`D/security/SecurityConfig.java:83`). |
| `@PreAuthorize` in Driver/Ride/Fare | Partial | Driver: `DriverController.java:36,53,76`. Ride: only `RideController.java:93`. Fare: none (the comment at `F/exception/GlobalExceptionHandler.java:17-20` confirms this). |
| JSON 401/403 | Done | Custom entry point and access-denied handler in every `SecurityConfig` (e.g. `A/security/SecurityConfig.java:82-89`), plus `AccessDeniedException` handlers in each `GlobalExceptionHandler`. |
| CORS for `http://localhost:3000` with Authorization + PATCH | Done | Every `SecurityConfig.corsConfigurationSource()` (e.g. `A/security/SecurityConfig.java:46-56`): allowed headers `*` and PATCH both included. Origins are hard-coded, including a Vercel URL. |
| Swagger UI + bearer scheme | Done | `*/config/OpenApiConfig.java` in all 4 services. `springdoc-openapi-starter-webmvc-ui` 3.1.1 is in each pom. |
| Actuator health | Missing | No `spring-boot-starter-actuator` in any pom. `scripts/health-check.sh:5-7` acknowledges this and probes `/` instead. |
| DTO validation | Partial | Account and Fare use DTOs. Driver (`D/model/Driver.java`) and Ride (`R/model/Ride.java`) bind entities directly, so clients can set server-owned fields such as `status`, `driverId`, `fare` and `available`. |
| Consistent error shape | Partial | `{code,message}` is used for known cases. No service has an `@ExceptionHandler(Exception.class)`, so unexpected errors (e.g. `DuplicateKeyException`, `IllegalArgumentException` from `FareCalculator`, a bad enum) go to Boot's `/error` page and come back as `{timestamp,status,error,path}` with a 500. No `details` field. |
| Meaningful unit tests (normal/boundary/failure) | Partial | Fare: good. Ride: fair. Driver: fair, but needs Docker. Account: none. |
| CI covers 4 services + frontend | Partial | `.github/workflows/ci.yml:16-21` matrix runs the 4 services. There's no frontend job (`npm ci && npm run lint && npm run build`). CI uses `mvn`, not `./mvnw`. |
| Postman collection + environment | Missing | No `*.postman_collection.json` or `*.postman_environment.json` anywhere in the repo. |
| Smoke-test script | Missing | `scripts/health-check.sh` only checks that each port answers HTTP. Nothing exercises register → ride → complete → receipt. |
| No committed secrets | Missing | The JWT secret literal is committed in all 4 property files (`A/props:4`, `D/props:7`, `R/props:8`, `F/props:7`). `FE/.env.local` exists on disk but isn't tracked (OK). |
| Each service uses its own Mongo DB | Done | `ridelink_account`, `ridelink_driver`, `ridelink_ride`, `ridelink_fare` (`*/props`). No cross-service repository access was found; services call each other only over REST. The README claims `account_db`/`driver_db`/… and `SPRING_DATA_MONGODB_URI` (`README.md:59,86,143`), which doesn't match the code. |
| Mongo URI from env `MONGODB_URI` | Missing | All 4 URIs are hard-coded. |
| docker-compose runs mongo + 4 services + frontend | Missing | `docker-compose.yml:2-10` defines only `mongodb`, even though the README (`README.md:101-106`) says it runs all 4 services. Per-service `Dockerfile`s exist. The frontend has no Dockerfile. |

## 6. Frontend (Next.js 16.3.4)

| Item | Status | Evidence |
|---|---|---|
| Login/register with validation messages | Partial | `FE/app/login/page.tsx:180` only disables the button when fields are empty. Server messages are shown (`:59`). No inline per-field messages for email format or password length. |
| Change password | Missing | No page or API call. The backend endpoint is also missing. |
| Passenger: estimate | Done | `FE/app/passenger/page.tsx:67-76,169-176`. |
| Passenger: 6-state stepper | Missing | Only a `StatusBadge` (`passenger/page.tsx:191`). |
| Passenger: cancel | Missing | No cancel action. The backend endpoint is also missing. |
| Passenger: history | Partial | `passenger/page.tsx:32,41`: last 10 completed or cancelled rides, using `?passengerId=`. |
| Passenger: pay | Missing | No pay step. Payment happens automatically when the driver completes the ride. |
| Passenger: receipt | Done | `FE/app/rides/[id]/page.tsx:37-42,94-113`. |
| Driver: availability | Done | `FE/app/driver/page.tsx:101`. |
| Driver: location | Missing | No UI. The backend endpoint is also missing. |
| Driver: accept / start | Missing | No UI. The backend endpoints are also missing. |
| Driver: complete | Done | `driver/page.tsx:124-135`. |
| Error-code → friendly message map | Missing | Raw `e.message` is shown everywhere (e.g. `login/page.tsx:59`). The only code-specific branch is `driver/page.tsx:83` (`DRIVER_NOT_FOUND`). |
| 401 → login redirect | Missing | `FE/lib/api.ts:40-44` throws `ApiError` without handling 401, and an expired token isn't cleared from the session (`FE/lib/auth.tsx`). |
| Role route guards | Partial | Client-side `useEffect` redirects per page: `passenger/page.tsx:35`, `driver/page.tsx:45`, `admin/dashboard/page.tsx:43-47`. `rides/[id]/page.tsx:24` checks login only. No `middleware.ts`. |
| Admin dashboard correctness (extra) | Partial | `admin/dashboard/page.tsx:53,99` hard-code `http://localhost:808x` instead of using `lib/api.ts`. It reads `driver.userId` and `driver.status`, neither of which exists on Driver (the field is `accountId`, and status is on Account), so suspend/activate sends `undefined` as the account id. |

---

## 7. Prioritized fix list

Ordered by risk (security and data integrity first), then by how much other work depends on each fix. Every item is additive: no existing endpoint, field, port or error code changes.

### P0 — Security and data integrity
1. **Secrets and config from env (all 4 services):** `jwt.secret=${JWT_SECRET:dev-only-change-me-dev-only-change-me}`, `spring.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/ridelink_<svc>}`. Keep the database names. Fix the README's database names and env var.
2. **Atomic driver reserve/release (Driver):** add `PATCH /api/drivers/{id}/reserve` and `/release` (or `POST /api/drivers/reserve-next?type=`) backed by `MongoTemplate.findAndModify` with an `available=true` precondition, returning 409 `DRIVER_NOT_AVAILABLE` on a lost race. Keep `/availability` as it is.
3. **Lock down `POST /api/payments/finalize` (Fare):** require a service caller or the assigned-driver/ADMIN role, and charge from the stored fare instead of trusting client-supplied distance.
4. **Ownership checks:** Account `GET`/`PATCH /api/accounts/{id}` (owner or ADMIN); Ride `GET /{id}` and history (passenger, assigned driver or ADMIN); Fare status and receipt (payer or ADMIN); Driver availability (own profile, or the anonymous interservice call until a service credential exists).
5. **Catch-all `@ExceptionHandler(Exception.class)` plus `DuplicateKeyException` → 409** in all 4 services, with `{code,message}` (+ optional `details`). Replace Ride's catch-`RuntimeException`-as-404 with a dedicated `RideNotFoundException`.
6. **Enable unique indexes:** `spring.data.mongodb.auto-index-creation=true` (or an explicit `IndexOperations` at startup) for account email, driver email and vehicleNumber, and payment rideId. Normalise emails to lower case.

### P1 — Core functional gaps (the brief's lifecycle)
7. **Ride state machine (Ride):** `RideStatus` enum (REQUESTED, ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED), stored as its string name so existing documents still load. Add a central `ALLOWED_TRANSITIONS` map → 409 `INVALID_STATUS_TRANSITION`, plus `PATCH /{id}/accept`, `/start` and `/cancel` with role and ownership rules.
8. **Release the driver on complete/cancel (Ride → Driver)**, and compensate (release) if saving the ride fails after a reserve.
9. **Ride → Fare estimate call** on ride creation: store `estimatedFare` (new nullable field) and stop trusting the client-sent `fare`.
10. **RestTemplate connect/read timeouts** (Ride), configurable through properties.
11. **Account:** `PATCH /api/accounts/me/password` (verify current password, new ≠ old, owner only), `GET /api/accounts/me`, and an admin seeder controlled by env (`ADMIN_EMAIL`/`ADMIN_PASSWORD`, dev defaults, skipped if one already exists). Tighten the password rule (e.g. 8–72 characters, letters and digits) for **new** registrations only.
12. **Driver:** `GET /api/drivers/{id}` (404 `DRIVER_NOT_FOUND`), `PATCH /api/drivers/{id}` (profile), `PATCH /{id}/location`, `PATCH /{id}/service-area`, an eligible filter by `serviceArea`, a unique vehicle number, one profile per account, and DRIVER role required on create.
13. **Fare & Payment:** move money to `BigDecimal` (scale 2, HALF_UP). Keep the JSON as numbers so clients don't break. Make per-vehicle rates configurable (`fare.rates.<type>.*`). Add `POST /api/payments/{rideId}/pay` taking a card number (`0000` → FAILED 402 `PAYMENT_DECLINED`, otherwise COMPLETED), with the amount taken from the stored fare, so PENDING and FAILED are actually used.

### P2 — Platform and verifiability
14. **Actuator** (`/actuator/health`, permitted in each `SecurityConfig`) in all 4 services. Point `scripts/health-check.sh` at it, falling back to `/`.
15. **Tests that run without MongoDB:** convert Driver's Testcontainers tests to a profile or `@Tag("integration")` excluded from `verify`, and make the `contextLoads` tests not depend on a live Mongo. Add normal, boundary and failure unit tests for each new feature, plus Account service tests.
16. **CI:** add a frontend job (`npm ci`, `npm run lint`, `npm run build`). Keep the 4-service matrix.
17. **docker-compose:** add the 4 services (with their existing Dockerfiles), a frontend Dockerfile and service, `MONGODB_URI`/`JWT_SECRET` env, and `depends_on` with healthchecks.
18. **Postman collection and environment** in `postman/`, covering every endpoint and the negative cases. Add `scripts/smoke-test.sh` for register → driver online → ride → accept/start/complete → pay → receipt, plus the old endpoints.

### P3 — Frontend
19. **API layer:** add an error-code → friendly-message map, and on 401 clear the session and redirect to `/login`. Route the admin dashboard through `lib/api.ts` and fix the `userId`/`status` field bug.
20. **Role guards:** a shared `useRequireRole` hook (or `middleware.ts`), applied to `rides/[id]` as well.
21. **Auth pages:** inline validation messages and a change-password page.
22. **Passenger:** 6-state stepper, cancel, a pay step with card input (`0000` demo), and full history.
23. **Driver:** location update, accept/start/complete actions, and the list of rides assigned to the driver.
