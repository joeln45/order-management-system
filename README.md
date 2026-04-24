# Order Management System

A full-stack drop-shipping order management platform: Spring Boot REST API on the back, Next.js 16 App Router on the front, Postgres underneath, all wired up with Docker Compose and CI.

Originally a CSCU9YW Web Services coursework assignment — this repo is the portfolio upgrade: JWT auth, HATEOAS responses, external wholesaler integration, containerised deployment, and a typed UI that consumes it all.

---

## Stack

| Layer | Tech |
| --- | --- |
| Backend | Spring Boot 3.3, Java 17, Spring Security, Spring Data JPA, Flyway, Hibernate, springdoc-openapi |
| Database | PostgreSQL 16 (prod), H2 in-memory (dev profile) |
| Frontend | Next.js 16 (App Router), React 19, TypeScript 5, Tailwind CSS 4 |
| Auth | JWT access tokens (15 min) + opaque refresh tokens (7 d), RFC 7807 problem responses |
| External | Wholesaler REST API (mocked with WireMock in tests) |
| Tests | JUnit 5, Spring `@WebMvcTest` slices, Testcontainers Postgres, WireMock, JaCoCo coverage gate |
| CI/CD | GitHub Actions (parallel backend + frontend jobs), multi-stage Dockerfiles, docker-compose |

---

## Architecture

```mermaid
flowchart LR
    Browser[Browser]
    subgraph Frontend["Next.js (port 3000)"]
        RSC[Server Components<br/>+ Route Handlers]
        Proxy["/api/proxy/[...path]<br/>attaches Bearer cookie"]
    end
    subgraph Backend["Spring Boot (port 8080)"]
        Sec[Security Filter Chain<br/>+ JwtAuthFilter]
        Ctrl[Controllers<br/>Auth · Customer · Operator · Product]
        Svc[Services<br/>Order · Auth · Wholesaler]
    end
    DB[(PostgreSQL)]
    Whl[[Wholesaler API]]

    Browser -->|cookies only| RSC
    Browser -->|fetch /api/proxy/...| Proxy
    RSC -->|apiFetch + Bearer| Sec
    Proxy -->|forward + Bearer| Sec
    Sec --> Ctrl --> Svc
    Svc --> DB
    Svc -.->|HTTP| Whl
```

The browser never holds the access token — it lives in an `oms_access` cookie that only server-side code reads. Client components hit `/api/proxy/<backend-path>`; the Next.js route handler attaches the Bearer header and forwards to Spring. No CORS, no token-in-localStorage, no token in request URLs.

---

## Order flow

```mermaid
sequenceDiagram
    actor Customer
    participant UI as Next.js UI
    participant Proxy as /api/proxy
    participant API as Spring OrderController
    participant Svc as OrderService
    participant Whl as Wholesaler API
    participant DB as PostgreSQL

    Customer->>UI: Submit new-order form
    UI->>Proxy: POST /api/proxy/orders
    Proxy->>API: POST /orders (Bearer)
    API->>Svc: placeOrder(request)
    Svc->>DB: load Products + Customer
    Svc->>Whl: GET /stock/{id} per line
    alt all items in stock & profitable
        Svc->>Whl: POST /order/{id}
        Svc->>DB: save Order(PENDING)
        API-->>Proxy: 201 Created + HATEOAS links
        Proxy-->>UI: 201 Created
        UI-->>Customer: Redirect to /orders
    else any item out of stock or loss-making
        Svc-->>API: BusinessRuleException
        API-->>Proxy: 409 ProblemDetail
        Proxy-->>UI: 409 with `detail`
        UI-->>Customer: Inline error message
    end
```

The backend is the source of truth on stock + profitability — the UI doesn't pre-validate, it just surfaces the `ProblemDetail.detail` on rejection.

---

## Project layout

```
order-management-system/
├── backend/                    # Spring Boot app + Maven wrapper
│   ├── src/main/java/com/joel/ordermanagement/
│   │   ├── auth/               # login, JWT filter, refresh rotation
│   │   ├── customer/           # customer + user entities, register
│   │   ├── order/              # OrderController, OrderService, DTOs
│   │   ├── operator/           # operator-only endpoints (status, pricing, revenue)
│   │   ├── product/            # product catalogue
│   │   ├── wholesaler/         # external API client (WebClient + retry)
│   │   ├── exception/          # GlobalExceptionHandler → RFC 7807
│   │   └── config/             # SecurityConfig, OpenApiConfig
│   ├── src/main/resources/
│   │   ├── application.yml              # shared config
│   │   ├── application-dev.yml          # H2 in-memory
│   │   ├── application-prod.yml         # Postgres + Flyway
│   │   └── db/migration/                # V1__*.sql, V2__*.sql
│   ├── src/test/java/...                # slice tests + integration test
│   ├── Dockerfile                       # multi-stage JDK build → JRE runtime
│   └── pom.xml                          # JaCoCo check bound to verify
│
├── frontend/                   # Next.js 16 App Router
│   ├── src/app/
│   │   ├── api/
│   │   │   ├── auth/{login,logout}/route.ts   # sets/clears cookies
│   │   │   └── proxy/[...path]/route.ts       # authed backend proxy
│   │   ├── login/                             # client form
│   │   ├── orders/                            # list + new + cancel (customer)
│   │   ├── operator/                          # all-orders, pricing, revenue
│   │   ├── products/                          # public catalogue
│   │   └── layout.tsx                         # shared nav + palette
│   ├── src/lib/
│   │   ├── api.ts            # apiFetch + ApiError class
│   │   ├── auth.ts           # cookie constants + getSession()
│   │   └── types.ts          # mirrors backend DTOs
│   ├── Dockerfile            # standalone bundle → node:alpine
│   └── next.config.ts        # output: "standalone"
│
├── docker-compose.yml        # postgres + backend + frontend
└── .github/workflows/ci.yml  # parallel Maven verify + npm build
```

---

## Running locally

### Option 1 — full stack in Docker

```bash
docker compose up --build
```

Then visit:

- UI:       http://localhost:3000
- API docs: http://localhost:8080/swagger-ui.html
- Postgres: `localhost:5432` (user `postgres` / db `oms`)

### Option 2 — just the database, app in your IDE

```bash
docker compose up -d postgres
```

**Backend** (from `backend/`):
```bash
./mvnw spring-boot:run
# or with the prod profile + real Postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

**Frontend** (from `frontend/`):
```bash
cp .env.local.example .env.local    # point API_URL at http://localhost:8080
npm install
npm run dev
```

### Seeded accounts (dev profile only)

| Role | Username | Password |
| --- | --- | --- |
| Operator | `operator` | `password123` |
| Customer | `customer` | `password123` |

The seeder is gated by `app.seed.enabled: true` in dev; `prod` disables it.

---

## Testing

```bash
# Backend: unit + slice + integration tests + JaCoCo check
cd backend && ./mvnw verify
```

Testcontainers starts a Postgres instance on the fly for the integration test — Docker Desktop must be running. JaCoCo `check` fails the build below 55 % line / 30 % branch coverage. The thresholds ratchet up once more integration tests land.

```bash
# Frontend: TypeScript + lint + production build
cd frontend && npm run lint && npm run build
```

`next build` performs the TypeScript compile pass — a clean build is the type-safety gate.

---

## Security notes

- Access tokens are **HS256**, 15 min TTL, readable by server code (cookie `oms_access`, not HttpOnly — server components need to forward it).
- Refresh tokens are **opaque + HttpOnly** (cookie `oms_refresh`), stored hashed in Postgres and rotated on every refresh. Compromise of the DB doesn't grant a valid refresh token.
- `APP_JWT_SECRET` must be ≥ 32 chars in production. The compose default is clearly flagged as dev-only; real deployments should inject via env file or secrets manager.
- Client JS never sees the access token — the authenticated-proxy pattern at `/api/proxy/[...path]` keeps it server-side.
- All endpoints under `/operator/**` require `ROLE_OPERATOR`; `/customers/{id}/orders` enforces `customerId == principal.customerId`.

---

## CI

Every push + PR to `main` runs two parallel GitHub Actions jobs:

- **backend**: `./mvnw -B verify` (unit + slice + Testcontainers integration + JaCoCo gate). Uploads the JaCoCo HTML report + surefire output on failure.
- **frontend**: `npm ci && npm run lint && npm run build`.

Both must be green before merge.

---

## License

Private portfolio project. Not intended for redistribution.
