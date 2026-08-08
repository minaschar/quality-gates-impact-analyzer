import { useMemo, useState } from 'react';
import { Check, KeyRound, RefreshCw, SlidersHorizontal, ToggleLeft } from 'lucide-react';
import { Card, CardHeader } from '@/components/common/Card';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import { ErrorState } from '@/components/common/ErrorState';
import { SettingsSkeleton } from '@/components/common/skeletons';
import { useConfiguration, useRefreshConfigurationCache, useUpdateConfiguration } from '@/hooks/useConfiguration';
import { CONFIG_CATEGORY_DISPLAY_NAMES } from '@/utils/constants';
import { classifyError } from '@/utils/errors';
import type { ConfigCategory, ConfigurationEntity } from '@/types';

const CATEGORY_ICON: Record<ConfigCategory, React.ReactNode> = {
  API: <KeyRound className="h-4 w-4" />,
  LIMITS: <SlidersHorizontal className="h-4 w-4" />,
  FEATURES: <ToggleLeft className="h-4 w-4" />,
};

function ConfigRow({ config }: { config: ConfigurationEntity }) {
  const [value, setValue] = useState(config.configValue);
  const [saved, setSaved] = useState(false);
  const updateMutation = useUpdateConfiguration();
  const isSensitive = config.configKey.includes('TOKEN') || config.configKey.includes('SECRET');
  const dirty = value !== config.configValue;

  function handleSave() {
    updateMutation.mutate(
      { key: config.configKey, value },
      {
        onSuccess: () => {
          setSaved(true);
          setTimeout(() => setSaved(false), 1500);
        },
      }
    );
  }

  return (
    <div className="flex flex-col gap-2 border-b border-slate-100 py-4 last:border-0 sm:flex-row sm:items-center sm:justify-between dark:border-slate-700">
      <div className="sm:w-1/2">
        <p className="font-mono text-sm font-medium text-slate-800 dark:text-slate-100">{config.configKey}</p>
        {config.description && <p className="text-xs text-slate-500 dark:text-slate-400">{config.description}</p>}
      </div>
      <div className="flex items-center gap-2 sm:w-1/2">
        <Input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          type={isSensitive ? 'password' : config.dataType === 'INTEGER' || config.dataType === 'DOUBLE' ? 'number' : 'text'}
          placeholder={isSensitive ? 'Enter new value to replace' : undefined}
          aria-label={config.configKey}
        />
        <Button
          size="sm"
          variant={dirty ? 'primary' : 'secondary'}
          disabled={!dirty}
          isLoading={updateMutation.isPending}
          onClick={handleSave}
        >
          {saved ? <Check className="h-4 w-4" /> : updateMutation.isPending ? 'Saving…' : 'Save'}
        </Button>
      </div>
    </div>
  );
}

export function Settings() {
  const configQuery = useConfiguration();
  const refreshMutation = useRefreshConfigurationCache();

  const grouped = useMemo(() => {
    const configs = configQuery.data ?? [];
    const byCategory = new Map<ConfigCategory, ConfigurationEntity[]>();
    for (const c of configs) {
      const list = byCategory.get(c.category) ?? [];
      list.push(c);
      byCategory.set(c.category, list);
    }
    return byCategory;
  }, [configQuery.data]);

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-slate-100">Settings</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Runtime configuration for GitHub access, analysis limits, and feature flags.
          </p>
        </div>
        <Button variant="secondary" size="sm" isLoading={refreshMutation.isPending} onClick={() => refreshMutation.mutate()}>
          {!refreshMutation.isPending && <RefreshCw className="h-3.5 w-3.5" />}
          {refreshMutation.isPending ? 'Refreshing…' : 'Refresh Cache'}
        </Button>
      </div>

      {configQuery.isLoading && <SettingsSkeleton />}
      {configQuery.isError &&
        (() => {
          const { title, message } = classifyError(configQuery.error);
          return <ErrorState title={title} message={message} onRetry={() => configQuery.refetch()} />;
        })()}

      {!configQuery.isLoading &&
        !configQuery.isError &&
        (['API', 'LIMITS', 'FEATURES'] as ConfigCategory[]).map((category) => {
          const configs = grouped.get(category) ?? [];
          if (configs.length === 0) return null;
          return (
            <Card key={category}>
              <CardHeader title={CONFIG_CATEGORY_DISPLAY_NAMES[category]} icon={CATEGORY_ICON[category]} />
              <div>
                {configs.map((c) => (
                  <ConfigRow key={c.configKey} config={c} />
                ))}
              </div>
            </Card>
          );
        })}
    </div>
  );
}
