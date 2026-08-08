import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Layout } from '@/components/layout/Layout';
import { Loading } from '@/components/common/Loading';

// Route-level code splitting: each page ships as its own chunk, fetched on first
// navigation rather than bundled into the initial load.
const Dashboard = lazy(() => import('@/pages/Dashboard').then((m) => ({ default: m.Dashboard })));
const Repositories = lazy(() => import('@/pages/Repositories').then((m) => ({ default: m.Repositories })));
const RepositoryDetail = lazy(() => import('@/pages/RepositoryDetail').then((m) => ({ default: m.RepositoryDetail })));
const Analyze = lazy(() => import('@/pages/Analyze').then((m) => ({ default: m.Analyze })));
const Settings = lazy(() => import('@/pages/Settings').then((m) => ({ default: m.Settings })));
const NotFound = lazy(() => import('@/pages/NotFound').then((m) => ({ default: m.NotFound })));

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route
            index
            element={
              <Suspense fallback={<Loading label="Loading dashboard…" />}>
                <Dashboard />
              </Suspense>
            }
          />
          <Route
            path="repositories"
            element={
              <Suspense fallback={<Loading label="Loading repositories…" />}>
                <Repositories />
              </Suspense>
            }
          />
          <Route
            path="repository/:owner/:repo"
            element={
              <Suspense fallback={<Loading label="Loading repository…" />}>
                <RepositoryDetail />
              </Suspense>
            }
          />
          <Route
            path="analyze"
            element={
              <Suspense fallback={<Loading label="Loading…" />}>
                <Analyze />
              </Suspense>
            }
          />
          <Route
            path="settings"
            element={
              <Suspense fallback={<Loading label="Loading settings…" />}>
                <Settings />
              </Suspense>
            }
          />
          <Route
            path="*"
            element={
              <Suspense fallback={<Loading />}>
                <NotFound />
              </Suspense>
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
