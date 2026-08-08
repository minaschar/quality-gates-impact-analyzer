import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Github, Loader2, Search } from 'lucide-react';
import { Card, CardHeader } from '@/components/common/Card';
import { Input } from '@/components/common/Input';
import { Checkbox } from '@/components/common/Checkbox';
import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useDetectQualityGate } from '@/hooks/useRepositories';
import { useConfirmDialog } from '@/hooks/useConfirmDialog';
import { validateGithubUrl } from '@/utils/validators';
import { classifyError } from '@/utils/errors';

const ANALYSIS_STEPS = [
  'Scanning workflow files & build configs',
  'Checking branch protection rules',
  'Verifying enforcement against recent PRs',
  'Reconstructing quality gate introduction history',
];

export function Analyze() {
  const navigate = useNavigate();
  const [repoUrl, setRepoUrl] = useState('');
  const [touched, setTouched] = useState(false);
  const [forceNewAnalysis, setForceNewAnalysis] = useState(false);

  const detectMutation = useDetectQualityGate();
  const { requestConfirm, dialogProps } = useConfirmDialog();

  const validation = validateGithubUrl(repoUrl);
  const showValidation = touched || repoUrl.length > 0;

  function runAnalysis() {
    detectMutation.mutate(
      { repositoryUrl: repoUrl.trim(), forceNewDetection: forceNewAnalysis },
      { onSuccess: (result) => navigate(`/repository/${result.owner}/${result.repo}`) }
    );
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!validation.valid) return;

    if (forceNewAnalysis) {
      requestConfirm({
        title: 'Force a new analysis?',
        message:
          'This bypasses any cached detection and re-runs the full analysis from scratch, including fresh GitHub API calls. This can take longer and uses more of your rate limit.',
        confirmLabel: 'Run Anyway',
        onConfirm: runAnalysis,
      });
      return;
    }

    runAnalysis();
  }

  const errorInfo = detectMutation.isError ? classifyError(detectMutation.error) : null;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100">Analyze a Repository</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Detect quality gate tools, enforcement, and (once computed) their impact on code quality.
        </p>
      </div>

      <Card>
        <CardHeader title="Repository" icon={<Github className="h-5 w-5" />} />
        <form onSubmit={handleSubmit} noValidate className="space-y-4">
          <Input
            name="repoUrl"
            label="GitHub Repository URL"
            placeholder="https://github.com/owner/repository"
            value={repoUrl}
            onChange={(e) => setRepoUrl(e.target.value)}
            onBlur={() => setTouched(true)}
            error={showValidation && !validation.valid ? validation.error : undefined}
            valid={showValidation && validation.valid}
            disabled={detectMutation.isPending}
            required
            autoFocus
          />

          <Checkbox
            label="Force new analysis"
            description="Bypass the cache and re-run detection even if this repo was analyzed before"
            checked={forceNewAnalysis}
            onChange={(e) => setForceNewAnalysis(e.target.checked)}
            disabled={detectMutation.isPending}
          />

          <Button
            type="submit"
            isLoading={detectMutation.isPending}
            disabled={showValidation && !validation.valid}
            className="w-full"
          >
            {!detectMutation.isPending && <Search className="h-4 w-4" />}
            {detectMutation.isPending ? 'Analyzing…' : 'Analyze Repository'}
          </Button>

          {errorInfo && (
            <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <div>
                <p className="font-medium">{errorInfo.title}</p>
                <p className="text-red-600 dark:text-red-400">{errorInfo.message}</p>
              </div>
            </div>
          )}
        </form>
      </Card>

      {detectMutation.isPending && (
        <Card>
          <div className="flex items-center gap-3">
            <Loader2 className="h-5 w-5 shrink-0 animate-spin text-blue-500" />
            <div>
              <p className="font-medium text-slate-800 dark:text-slate-100">Running detection…</p>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                This can take a little while for larger repositories with long commit histories.
              </p>
            </div>
          </div>
          <ul className="mt-4 space-y-2 border-t border-slate-100 pt-4 text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
            {ANALYSIS_STEPS.map((step) => (
              <li key={step} className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-blue-400" />
                {step}
              </li>
            ))}
          </ul>
        </Card>
      )}

      <ConfirmDialog {...dialogProps} />
    </div>
  );
}
