# Quality Gate Analyzer

A Spring Boot application for analyzing quality gate enforcement in open-source GitHub repositories,
and whether those quality gates actually improved code quality over time.

A React frontend for this API lives in [`../frontend`](../frontend/README.md).

## 🚀 Features

- **PostgreSQL Database Integration** - Analysis results are persisted
- **Analysis Caching** - Subsequent requests return cached data unless explicitly forced fresh
  (`forceNewDetection=true` for detection, `forceNewAnalysis=true` for impact analysis)
- **E2E Quality Impact Analysis** - Compares SonarQube quality metrics before/after each quality
  gate tool's introduction to determine whether it actually improved code quality
- **Runtime Configuration** - All limits and settings configurable via API/UI
- **Flyway Migrations** - Schema versioning and automatic migrations
- **Caffeine Caching** - In-memory cache for configuration values

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Docker & Docker Compose
- Maven 3.8+
- GitHub Personal Access Token (for higher rate limits)

### 1. Start PostgreSQL

```bash
cd ..   # repo root, where docker-compose.yml lives

# Start PostgreSQL only
docker-compose up -d

# Or with pgAdmin (for database management)
docker-compose --profile admin up -d
```

**Database Access:**
- PostgreSQL: `localhost:5433`
- Database: `quality_gate_analyzer_db`
- Username: `postgres`
- Password: `postgres`
- pgAdmin: `http://localhost:5050` (admin@thesis.com / admin)

### 2. Configure GitHub Token

Option A: Environment variable
```bash
export GITHUB_API_TOKEN=ghp_your_token_here
```

Option B: Via API (after starting the app)
```bash
curl -X PUT http://localhost:8080/api/v1/configuration/GITHUB_TOKEN \
  -H "Content-Type: application/json" \
  -d '{"value": "ghp_your_token_here"}'
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

### 4. Analyze a Repository

```bash
# First analysis (runs full analysis, saves to DB)
curl -X POST http://localhost:8080/api/v1/quality-gate/detect \
  -H "Content-Type: application/json" \
  -d '{"repositoryUrl": "https://github.com/apache/superset"}'

# Second call (returns cached data instantly)
curl -X POST http://localhost:8080/api/v1/quality-gate/detect \
  -H "Content-Type: application/json" \
  -d '{"repositoryUrl": "https://github.com/apache/superset"}'

# Force fresh detection (bypasses the cache)
curl -X POST "http://localhost:8080/api/v1/quality-gate/detect?forceNewDetection=true" \
  -H "Content-Type: application/json" \
  -d '{"repositoryUrl": "https://github.com/apache/superset"}'
```

## 📡 API Endpoints

### Quality Gate Detection

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/quality-gate/detect` | Detect quality gate (with caching) |
| POST | `/api/v1/quality-gate/detect?forceNewDetection=true` | Force fresh detection |
| GET | `/api/v1/quality-gate/{owner}/{repo}` | Get cached quality gate detection |
| GET | `/api/v1/quality-gate` | List all analyzed repositories |
| GET | `/api/v1/quality-gate?hasQualityGate=true` | List repos with QG |
| DELETE | `/api/v1/quality-gate/{owner}/{repo}` | Delete cached quality gate detection |

### Impact Analysis

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/impact-analysis?owner=..&repo=..` | Run impact analysis (with caching) |
| POST | `/api/v1/impact-analysis?owner=..&repo=..&forceNewAnalysis=true` | Force recomputation of the before/after comparison (re-ingests metrics; reuses cached detection/commits) |
| POST | `/api/v1/impact-analysis/refresh?owner=..&repo=..` | Force a fresh detection + commit history fetch + metrics re-ingestion (does not recompute the comparison itself) |
| GET | `/api/v1/impact-analysis/{owner}/{repo}` | Get a computed impact analysis |
| GET | `/api/v1/impact-analysis` | List all analyzed repositories with summary |

### Configuration

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/configuration` | Get all configuration |
| GET | `/api/v1/configuration/{key}` | Get specific config |
| PUT | `/api/v1/configuration/{key}` | Update config value |
| PUT | `/api/v1/configuration/batch` | Batch update |
| POST | `/api/v1/configuration/refresh` | Refresh cache |
| GET | `/api/v1/configuration/limits` | Get current limits |
| GET | `/api/v1/configuration/features` | Get feature flags |

### Utilities

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/rate-limit` | Check GitHub API rate limit |
| GET | `/api/v1/health` | Health check |

## ⚙️ Configuration Options

### API Settings

| Key | Default | Description |
|-----|---------|-------------|
| `GITHUB_TOKEN` | (empty) | GitHub Personal Access Token |

### Limits

| Key | Default | Description |
|-----|---------|-------------|
| `SAMPLE_PRS_LIMIT` | 100 | Max PRs to analyze for enforcement |
| `WORKFLOW_RUNS_LIMIT` | 100 | Max workflow runs to fetch |
| `PR_ANALYSIS_LIMIT` | 100 | Max PRs to analyze in detail |
| `CHECK_RUNS_PER_COMMIT` | 100 | Max check runs per commit |
| `MAX_BINARY_SEARCH_ITERATIONS` | 50 | Max iterations for PR binary search |
| `LINEAR_SEARCH_THRESHOLD` | 10 | Switch to linear when range < this |
| `SAMPLE_PRS_TO_RETURN` | 5 | Sample PRs in response |

### Feature Flags

| Key | Default | Description |
|-----|---------|-------------|
| `ENABLE_PR_FALLBACK` | true | Enable PR-based binary search fallback |
| `ENABLE_HISTORY_ANALYSIS` | true | Enable git-blame-based history analysis |
| `ENABLE_EXTERNAL_CHECK_DETECTION` | true | Enable external app detection |

## 🗄️ Database Schema

```
┌─────────────────────┐
│   configuration     │ ─── Runtime config values
└─────────────────────┘

┌─────────────────────┐
│    repositories     │ ─── Main analysis results
└─────────┬───────────┘
          │
    ┌─────┴─────┬──────────────┬─────────────────┐
    │           │              │                 │
    ▼           ▼              ▼                 ▼
┌─────────┐ ┌─────────┐ ┌─────────────────┐ ┌─────────────┐
│qg_detec │ │qg_work  │ │tool_introductions│ │ enforcement │
│ tions   │ │ flows   │ └────────┬────────┘ └──────┬──────┘
└─────────┘ └─────────┘          │                 │
                                 ▼                 ▼
                          ┌───────────┐    ┌────────────┐
                          │file_intros│    │pr_samples  │
                          └───────────┘    └─────┬──────┘
                                                 │
                                    ┌────────────┼────────────┐
                                    ▼            ▼            ▼
                              ┌──────────┐ ┌──────────┐ ┌──────────┐
                              │workflow  │ │check_runs│ │commit_   │
                              │_runs     │ │          │ │statuses  │
                              └──────────┘ └──────────┘ └──────────┘
```

Impact analysis is keyed independently by `(owner, repo)` rather than FK'd to a specific
detection row, so re-running detection never leaves a prior impact analysis with a dangling
foreign key. It's not left stale either: if a (re)detection via `/quality-gate/detect` or
`/impact-analysis/refresh` finds the quality gate gone, any stored impact analysis for that
repo is proactively deleted, so a now-inaccurate before/after comparison from before the tool
was removed doesn't keep being served by `/impact-analysis`:

```
┌─────────────────────┐
│  t_impact_analysis   │ ─── One row per (owner, repo)
└─────────┬────────────┘
          │
    ┌─────┴──────┐
    ▼            ▼
┌──────────────┐ ┌────────────────────────┐
│t_impact_     │ │t_impact_metrics_        │
│comparison    │ │snapshot                 │
│(per-tool     │ │(each before/after       │
│ before/after)│ │ metric snapshot)        │
└──────────────┘ └────────────────────────┘
```

## 🔄 Analysis Flow

```
POST /api/v1/quality-gate/detect
        │
        ▼
┌───────────────────────┐
│ forceNewDetection?    │
└───────────┬───────────┘
            │
    ┌───────┴───────┐
    │ NO            │ YES
    ▼               ▼
┌─────────┐    ┌─────────────┐
│Check DB │    │Run Analysis │
│for cache│    │(E2E)        │
└────┬────┘    └──────┬──────┘
     │                │
  Found?              │
     │                │
 ┌───┴───┐            │
 │       │            │
YES     NO            │
 │       │            │
 ▼       ▼            ▼
Return  Run        Save as
cached  Analysis   new version
        & Save     
```

## 📊 Supported Quality Gate Tools

### Code Quality
SonarQube, SonarCloud, Codacy, Code Climate, Qodana, DeepSource

### Code Style  
Checkstyle, PMD, SpotBugs, ESLint, Prettier, Pylint, Flake8, Ruff, Black, MyPy, GolangCI-Lint, RuboCop, PHPStan, Clippy, ktlint, Detekt, SwiftLint

### Coverage
Codecov, Coveralls, JaCoCo, Cobertura, Istanbul/NYC, Coverage.py

### Security
Snyk, Trivy, Dependabot, Semgrep

## 🧪 Testing

```bash
# Run unit tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 📁 Project Structure

```
backend/
├── pom.xml                     # Maven dependencies
├── src/main/
│   ├── java/com/thesis/qualitygateanalyzer/
│   │   ├── config/                  # Spring configuration
│   │   ├── constant/                # Extracted constants
│   │   ├── controller/v1/{feature}/ # REST controllers (implement ApiV1Controller for the /api/v1 prefix)
│   │   ├── service/{feature}/       # Service interfaces + impls (business logic)
│   │   ├── repository/{feature}/    # Spring Data repositories
│   │   ├── entity/{feature}/        # JPA entities
│   │   ├── domain/{feature}/        # Domain models & enums
│   │   ├── dto/{request,response}/  # API request/response DTOs
│   │   ├── mapper/                  # MapStruct domain <-> entity/DTO mappers
│   │   └── exception/               # Exception hierarchy + global handler
│   └── resources/
│       ├── application.yml         # App configuration
│       └── db/migration/           # Flyway migrations
│           ├── V1__initial_schema.sql
│           ├── V2__initial_data.sql
│           ├── V3__commit_history.sql
│           ├── V4__quality_metric_snapshots.sql
│           ├── V5__drop_quality_metrics.sql
│           └── V6__impact_analysis.sql
```

## 🐳 Docker Commands

```bash
# Start PostgreSQL
docker-compose up -d

# Start with pgAdmin
docker-compose --profile admin up -d

# Stop all
docker-compose down

# Stop and delete data
docker-compose down -v

# View logs
docker-compose logs -f postgres

# Connect to database
docker exec -it qga-postgres psql -U postgres -d quality_gate_analyzer_db
```

## 📄 License

Thesis Project - All Rights Reserved
