# Contributing

This is a personal portfolio project. Issues and pull requests are welcome if you spot something worth fixing.

## Getting started

```bash
# 1. Clone the repo
git clone https://github.com/your-username/order-management-system.git
cd order-management-system

# 2. Start everything with Docker
docker compose up --build

# 3. Or run the DB only and start each app in your IDE / terminal
docker compose up -d postgres

# Backend (from backend/)
./mvnw spring-boot:run          # dev profile, H2 in-memory
# or against the real Postgres:
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Frontend (from frontend/)
cp .env.local.example .env.local
npm install && npm run dev
```

## Running tests

```bash
# Backend: unit + slice + integration tests + JaCoCo coverage gate
cd backend && ./mvnw verify

# Frontend: lint + TypeScript check + production build
cd frontend && npm run lint && npm run build
```

Docker Desktop must be running for the Testcontainers integration test.

## Code style

- **Backend**: standard Java conventions. Lombok annotations preferred over manual getters/setters.
- **Frontend**: ESLint config is already set up; run `npm run lint` before committing.
- **Commits**: short imperative subject line, blank line, then a body if needed.

## Branching

Work on a feature branch, open a PR against `main`. CI must be green before merge.
