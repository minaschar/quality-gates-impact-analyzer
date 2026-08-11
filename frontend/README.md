# Quality Gate Analyzer — Frontend

React + TypeScript UI for the Quality Gate Analyzer backend: detect quality gate tools in a
GitHub repository, check how strictly they're enforced, and compare code quality metrics
before/after each tool was introduced.

## Stack

Vite, React 18, TypeScript, Tailwind CSS, React Router, TanStack Query, Axios, Recharts,
Lucide React, react-hot-toast.

## Getting Started

```bash
npm install
npm run dev
```

The dev server runs on `http://localhost:5173` and talks to the backend at `http://localhost:8080/api/v1`
by default (see `.env.example` — copy to `.env` to override via `VITE_API_BASE_URL`). Make sure the backend
(and its Postgres database) are running first — see the [root README](../README.md).

## Features

- **Dashboard** — summary stats, recent analyses, quick-analyze form, trend distribution chart
- **Repositories list** — search + quality-gate filter, sortable columns, pagination
  (configurable page size, default 10), empty/no-match states
- **Repository detail** — Overview / Quality Gates / Enforcement / Quality Impact tabs, each with
  its own charts (timeline, before/after bars, enforcement donut). Header's "Re-run Full Analysis"
  is the complete end-to-end action -- fresh detection, commit history, quality metrics, and the
  before/after comparison, all in one call to `POST /e2e-analysis`; the Quality Gates tab's
  "Redetect" forces detection only; the Quality Impact tab's "Recompute" forces
  the metrics + comparison only, reusing whatever's already cached for detection/commits. Each
  narrower action stays independently useful (and independently callable via the API) -- see
  [backend/README.md](../backend/README.md)'s API Endpoints table for what each endpoint touches
  on its own. Header's "Delete" also calls `DELETE /e2e-analysis`, not the narrower
  `DELETE /quality-gate/{owner}/{repo}` -- it removes detection, commit history, quality
  metrics, and impact analysis together, leaving no orphaned data behind
- **Analyze** — GitHub URL form with real-time validation and a confirmation step for forced
  re-analysis
- **Settings** — view/edit runtime configuration (GitHub token, limits, feature flags)
- **Dark mode** — toggle in the header, persisted to `localStorage`
- **Loading UX** — skeleton loaders (not spinners) for every content area, a top-of-page loading
  bar tied to in-flight queries/mutations, and route-level code splitting via `React.lazy`/`Suspense`
- **Error handling** — errors are classified (not-found / rate-limited / server-unavailable /
  network / validation) so messaging is specific instead of generic; a global `ErrorBoundary`
  catches render crashes; toast notifications confirm/report every mutation; an offline banner
  appears when connectivity drops (TanStack Query auto-resumes queued requests on reconnect)
- **Validation & confirmation** — real-time GitHub URL validation mirrors the backend's own regex;
  destructive or cache-bypassing actions (re-run full analysis, redetect quality gates, recompute
  impact analysis, delete repository) go through a confirmation dialog first

## Structure

```
src/
  api/            axios client (client.ts unwraps the backend's ApiResponse envelope) +
                   one wrapper file per backend controller (repositories, impactAnalysis, configuration)
  types/          TypeScript interfaces mirroring the backend's actual DTOs/domain objects
  hooks/          React Query hooks (data fetching/mutations) + useTheme, useOnlineStatus, useConfirmDialog
  components/
    common/       generic UI primitives — Button, Card, Badge, Input, Checkbox, Tabs, Pagination,
                   Skeleton/skeletons, Loading, EmptyState, ErrorState, ErrorBoundary, ConfirmDialog
    charts/       Recharts-based visualizations (timeline, before/after, enforcement donut, trend distribution)
    layout/       Header/Footer/Layout shell, TopLoadingBar, OfflineBanner
    repository/   repo-domain components — badges, table, and the four detail-page tabs
  pages/          route-level components (lazy-loaded in App.tsx)
  utils/          formatters, constants (enum/tool display names), validators, error classification,
                   toast wrapper, repoSort (comparator behind the Repositories table's sortable columns)
```

## Scripts

- `npm run dev` — start the dev server
- `npm run build` — type-check (`tsc --noEmit`) and build for production
- `npm run preview` — preview the production build locally
- `npm run lint` — run ESLint

## Testing

No automated frontend test suite exists yet — `npm run build` (type-check + build) and
`npm run lint` are the current checks. Manual end-to-end testing is done against a running
backend + Postgres (see the root README's "How to test" notes).
