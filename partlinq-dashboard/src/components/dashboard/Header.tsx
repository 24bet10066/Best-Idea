import type { DashboardStats } from '../../types';
import { formatINR } from '../../lib/format';

interface HeaderProps {
  shopName: string;
  stats: DashboardStats;
  isLive?: boolean;
}

export function Header({ shopName, stats, isLive = false }: HeaderProps) {
  const today = new Date();
  const dateStr = today.toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric', weekday: 'long' });

  return (
    <header className="h-14 bg-zinc-900 border-b border-zinc-800 flex items-center justify-between px-5 shrink-0">
      {/* Left: Logo + Shop + Connection Badge */}
      <div className="flex items-center gap-3">
        <div className="flex items-baseline gap-1.5">
          <span className="text-lg font-bold text-white tracking-tight">Part</span>
          <span className="text-lg font-bold text-amber-400 tracking-tight">LinQ</span>
        </div>
        <div className="h-5 w-px bg-zinc-700" />
        <span className="text-sm text-zinc-400">{shopName}</span>
        {/* Connection status */}
        <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${
          isLive
            ? 'bg-emerald-900/60 text-emerald-300 border border-emerald-700'
            : 'bg-zinc-800 text-zinc-500 border border-zinc-700'
        }`}>
          <span className={`w-1.5 h-1.5 rounded-full ${isLive ? 'bg-emerald-400 animate-pulse' : 'bg-zinc-600'}`} />
          {isLive ? 'LIVE' : 'DEMO'}
        </span>
      </div>

      {/* Center: Date */}
      <div className="text-sm text-zinc-500 hidden md:block">{dateStr}</div>

      {/* Right: KPI Pills */}
      <div className="flex items-center gap-2">
        <KpiPill label="Today" value={formatINR(stats.todaySales)} variant="success" />
        <KpiPill label="Outstanding" value={formatINR(stats.totalOutstanding)} variant="warning" />
        <KpiPill label="Pending" value={String(stats.pendingOrders)} variant="default" />
        <KpiPill label="Overdue" value={String(stats.overduePayments)} variant={stats.overduePayments > 0 ? 'danger' : 'success'} />
      </div>
    </header>
  );
}

function KpiPill({ label, value, variant }: { label: string; value: string; variant: 'success' | 'warning' | 'danger' | 'default' }) {
  const colors = {
    success: 'bg-emerald-900/50 text-emerald-300 border-emerald-800',
    warning: 'bg-amber-900/50 text-amber-300 border-amber-800',
    danger: 'bg-red-900/50 text-red-300 border-red-800',
    default: 'bg-zinc-800 text-zinc-300 border-zinc-700',
  };

  return (
    <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-xs ${colors[variant]}`}>
      <span className="text-zinc-500 font-medium">{label}</span>
      <span className="font-mono font-bold text-sm tabular-nums">{value}</span>
    </div>
  );
}
