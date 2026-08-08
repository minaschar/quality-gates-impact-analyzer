export function Footer() {
  return (
    <footer className="border-t border-slate-200 py-6 dark:border-slate-800">
      <div className="mx-auto max-w-7xl px-4 text-center text-sm text-slate-500 sm:px-6 lg:px-8 dark:text-slate-400">
        © {new Date().getFullYear()} Quality Gate Analyzer — Thesis Project
      </div>
    </footer>
  );
}
