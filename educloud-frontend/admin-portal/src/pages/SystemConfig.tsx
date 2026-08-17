import { useEffect, useState } from 'react';
import { Save, Settings2, Mail, Server, Shield, CheckCircle } from 'lucide-react';
import ConfigForm, { type ConfigSection } from '../components/ConfigForm';
import { useSystemStore } from '../stores/useSystemStore';
import { defaultConfig } from '../services/api';
import type { SystemConfig as SystemConfigType } from '../types';
import { cn } from '../utils/cn';

const tabs: { key: ConfigSection; label: string; icon: typeof Settings2 }[] = [
  { key: 'basic', label: '基本设置', icon: Settings2 },
  { key: 'email', label: '邮件配置', icon: Mail },
  { key: 'storage', label: '存储配置', icon: Server },
  { key: 'security', label: '安全设置', icon: Shield },
];

export default function SystemConfig() {
  const { config, loading, saving, fetchConfig, saveConfig, fetchStats, stats } = useSystemStore();
  const [active, setActive] = useState<ConfigSection>('basic');
  const [form, setForm] = useState<SystemConfigType>(defaultConfig);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    void fetchConfig();
    void fetchStats();
  }, [fetchConfig, fetchStats]);

  useEffect(() => {
    if (config) setForm(config);
  }, [config]);

  const handleSave = async () => {
    const ok = await saveConfig(form);
    if (ok) {
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 animate-fade-up opacity-0">
        <div>
          <div className="section-label mb-2">系统</div>
          <h1 className="display-heading text-3xl md:text-4xl">系统配置</h1>
          <p className="text-ink-500 mt-2">管理平台基础信息、邮件、存储与安全设置</p>
        </div>
        <button onClick={handleSave} disabled={saving} className="btn-primary self-start">
          {saving ? (
            '保存中...'
          ) : saved ? (
            <>
              <CheckCircle size={16} />
              已保存
            </>
          ) : (
            <>
              <Save size={16} />
              保存配置
            </>
          )}
        </button>
      </div>

      {/* System status bar */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 animate-fade-up opacity-0 animation-delay-100">
          <StatusItem label="CPU 使用率" value={`${stats.cpuUsage}%`} percent={stats.cpuUsage} />
          <StatusItem label="内存使用率" value={`${stats.memoryUsage}%`} percent={stats.memoryUsage} />
          <StatusItem label="磁盘使用率" value={`${stats.diskUsage}%`} percent={stats.diskUsage} />
          <div className="stat-card">
            <div className="text-xs uppercase tracking-widest text-ink-400 mb-2">服务状态</div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 bg-green-500 rounded-full animate-pulse" />
              <span className="font-display text-xl font-700 text-green-700">运行中</span>
            </div>
            <div className="text-xs text-ink-400 mt-2">运行时长 {stats.uptime} · {stats.nodeCount} 节点</div>
          </div>
        </div>
      )}

      <div className="flex flex-col lg:flex-row gap-6">
        {/* Tab sidebar */}
        <div className="lg:w-56 shrink-0 animate-fade-up opacity-0 animation-delay-200">
          <div className="card-editorial p-2 lg:sticky lg:top-4">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActive(tab.key)}
                className={cn(
                  'w-full flex items-center gap-3 px-4 py-3 text-sm text-left transition-colors',
                  active === tab.key
                    ? 'bg-indigo-50 text-indigo-800 font-medium border-l-2 border-amber-600'
                    : 'text-ink-500 hover:text-indigo-800 hover:bg-ink-50 border-l-2 border-transparent',
                )}
              >
                <tab.icon size={17} />
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        {/* Form content */}
        <div className="flex-1 animate-fade-up opacity-0 animation-delay-300">
          {loading && !config ? (
            <div className="text-center py-16 text-ink-400">加载配置中...</div>
          ) : (
            <ConfigForm value={form} onChange={setForm} section={active} />
          )}
        </div>
      </div>
    </div>
  );
}

function StatusItem({ label, value, percent }: { label: string; value: string; percent: number }) {
  const color = percent > 80 ? 'bg-red-500' : percent > 60 ? 'bg-amber-500' : 'bg-indigo-800';
  return (
    <div className="stat-card">
      <div className="text-xs uppercase tracking-widest text-ink-400 mb-2">{label}</div>
      <div className="font-display text-2xl font-700 text-ink-900 mb-3">{value}</div>
      <div className="progress-track">
        <div className={cn('progress-fill', color)} style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
