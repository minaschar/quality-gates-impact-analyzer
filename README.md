# Quality Gate Analyzer

Monorepo for the Quality Gate Analyzer project: a Spring Boot backend that detects quality gate
tools (linters, coverage, code-quality scanners) in GitHub repositories, checks how strictly
they're enforced, and measures their impact on code quality over time — plus a React frontend
for exploring the results.

## Layout

```
.
├── backend/             Spring Boot API (see backend/README.md)
├── frontend/            React + TypeScript UI (see frontend/README.md)
├── scripts/              Standalone Python tooling for Sonar/PR cadence analysis
└── docker-compose.yml    Shared infrastructure (Postgres, pgAdmin) — not the app itself
```

`docker-compose.yml` only runs infrastructure. The backend and frontend both run as regular
local processes (`mvn spring-boot:run` / `npm run dev`), not as containers.

## Getting started

Three things, in order — database, backend, frontend:

```bash
# 1. Database (Postgres on :5433, + optional pgAdmin on :5050)
docker-compose up -d
# or: docker-compose --profile admin up -d

# 2. Backend (:8080 — runs Flyway migrations automatically)
cd backend
mvn spring-boot:run

# 3. Frontend (:5173)
cd frontend
npm install
npm run dev
```

Then open `http://localhost:5173`. See [backend/README.md](backend/README.md) and
[frontend/README.md](frontend/README.md) for feature details, API reference, and project structure.

## How to test

- **Backend**: `cd backend && mvn test` — full unit/integration test suite.
- **Frontend**: `cd frontend && npm run build && npm run lint` — type-check + lint (no
  automated test suite yet).
- **End-to-end**: with all three services running, use the UI at `localhost:5173` — paste a
  GitHub URL on the Dashboard or Analyze page, walk through the Repository Detail tabs
  (Overview / Quality Gates / Enforcement / Quality Impact), and try the cache-bypassing actions
  (header's Re-run Full Analysis, Quality Gates tab's Redetect, Quality Impact tab's Recompute,
  and Delete) to confirm the confirmation dialogs and toasts fire correctly.

## Shutting down without losing data

```bash
# Frontend / backend: Ctrl+C in their respective terminals — both are stateless.

# Database: stop or remove containers, but keep the named volume (postgres_data)
docker-compose stop      # keeps containers too, for a fast restart via `docker-compose start`
# or
docker-compose down      # removes containers, keeps volumes — `docker-compose up -d` recreates them

# Never run `docker-compose down -v` unless you actually want to wipe the database —
# the -v flag deletes the named volumes along with the containers.
```

## Scripts

Standalone Python utilities, unrelated to the backend's runtime — see [scripts/README.md](scripts/README.md).
