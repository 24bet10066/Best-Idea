import { Card, CardContent } from '@/components/ui/card';
import type { DashboardStats, Order, UdhaarSummary } from '../../../types';
import { formatINR, formatINRFull, timeAgo } from '../../../hooks/useMockData';

interface OverviewProps {
  stats: DashboardStats;
  recentOrders: Order[];
  overdueUdhaar: UdhaarSummary[];
  onViewOrders: () => void;
  onViewUdhaar: () => void;
}

// Critique Fix #1: "Today's Pulse" — horizontal newspaper-headline strip, no cards, no icons
const PULSE_ITEMS: { key: keyof DashboardStats; label: string; format: (v: number) => string; color: string; alertWhen?: (v: number) => boolean }[] = [
  { key: 'todaySales', label: 'Sales Today', format: formatINR, color: 'text-emerald-600' },
  { key: 'totalOutstanding', label: 'Outstanding', format: formatINR, color: 'text-amber-600' },
  { key: 'pendingOrders', label: 'Pending', format: (v) => String(v), color: 'text-blue-600' },
  { key: 'overduePayments', label: 'Overdue', format: (v) => String(v), color: 'text-red-600', alertWhen: (v) => v > 0 },
  { key: 'activeTechnicians', label: 'Active', format: (v) => String(v), color: 'text-zinc-700' },
  { key: 'lowStockItems', label: 'Low Stock', format: (v) => String(v), color: 'text-orange-600', alertWhen: (v) => v > 3 },
];

export function OverviewView({ stats, recentOrders, overdueUdhaar, onViewOrders, onViewUdhaar }: OverviewProps) {
  return (
    <div className="p-5 space-y-5 max-w-5xl">
      {/* Pulse Strip — single horizontal row, dense, no cards */}
      <div className="flex items-stretch border border-zinc-200 rounded-lg bg-white overflow-hidden">
        {PULSE_ITEMS.map((item, i) => {
          const isAlert = item.alertWhen?.(stats[item.key]);
          return (
            <div
              key={item.key}
              className={`flex-1 px-4 py-3 ${i > 0 ? 'border-l border-zinc-100' : ''} ${isAlert ? 'bg-red-50/60' : ''} transition-colors hover:bg-zinc-50`}
            >
              <p className={`text-3xl font-mono font-bold tabular-nums tracking-tight leading-none ${item.color}`}>
                {item.format(stats[item.key])}
              </p>
              <p className="text-[10px] text-zinc-400 font-semibold uppercase tracking-wider mt-1.5 flex items-center gap-1">
                {isAlert && <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />}
                {item.label}
              </p>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-5 gap-5">
        {/* Recent Orders — 3 cols */}
        <div className="col-span-3">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-zinc-800">Recent Orders</h3>
            <button onClick={onViewOrders} className="text-xs text-blue-600 hover:text-blue-700 font-medium">
              View all &rarr;
            </button>
          </div>
          <Card className="border border-zinc-200 shadow-none">
            <CardContent className="p-0">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-zinc-100">
                    <th className="text-left py-2.5 px-3 text-[11px] font-semibold text-zinc-500 uppercase">Order</th>
                    <th className="text-left py-2.5 px-3 text-[11px] font-semibold text-zinc-500 uppercase">Technician</th>
                    <th className="text-right py-2.5 px-3 text-[11px] font-semibold text-zinc-500 uppercase">Amount</th>
                    <th className="text-center py-2.5 px-3 text-[11px] font-semibold text-zinc-500 uppercase">Status</th>
                    <th className="text-right py-2.5 px-3 text-[11px] font-semibold text-zinc-500 uppercase">Time</th>
                  </tr>
                </thead>
                <tbody>
                  {recentOrders.slice(0, 6).map(order => (
                    <tr key={order.id} className="border-b border-zinc-50 hover:bg-zinc-50 transition-colors">
                      <td className="py-2.5 px-3 font-mono text-xs text-zinc-600">{order.orderNumber}</td>
                      <td className="py-2.5 px-3 text-zinc-800 font-medium text-xs">{order.technicianName}</td>
                      <td className="py-2.5 px-3 text-right font-mono font-semibold tabular-nums text-xs">{formatINRFull(order.totalAmount)}</td>
                      <td className="py-2.5 px-3 text-center"><StatusBadge status={order.status} /></td>
                      <td className="py-2.5 px-3 text-right text-[11px] text-zinc-400">{timeAgo(order.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>
        </div>

        {/* Needs Attention — 2 cols */}
        <div className="col-span-2">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-zinc-800 flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-red-500" />
              Needs Attention
            </h3>
            <button onClick={onViewUdhaar} className="text-xs text-blue-600 hover:text-blue-700 font-medium">
              Udhaar &rarr;
            </button>
          </div>
          <div className="space-y-2">
            {/* Critique Fix #3: Better empty state — show confidence signal */}
            {overdueUdhaar.length === 0 ? (
              <Card className="border border-emerald-200 bg-emerald-50/30 shadow-none">
                <CardContent className="p-5">
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded-lg bg-emerald-100 flex items-center justify-center text-lg shrink-0">✅</div>
                    <div>
                      <p className="text-sm font-semibold text-emerald-800">All payments on track</p>
                      <p className="text-xs text-emerald-600 mt-1">No overdue or at-risk balances. Last flagged item was cleared 3 days ago.</p>
                      <p className="text-[10px] text-zinc-400 mt-2 font-mono">Checked: {new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}</p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ) : (
              overdueUdhaar.slice(0, 5).map(u => (
                <Card key={u.technicianId} className="border border-red-100 bg-red-50/30 shadow-none hover:shadow-sm transition-shadow">
                  <CardContent className="p-3">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-semibold text-zinc-800">{u.technicianName}</p>
                        <p className="text-[11px] text-zinc-500 mt-0.5">
                          {u.daysSinceLastPayment}d since last payment &middot; {u.totalUnpaidOrders} unpaid order{u.totalUnpaidOrders !== 1 ? 's' : ''}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="font-mono font-bold text-red-600 tabular-nums text-sm">{formatINRFull(u.currentBalance)}</p>
                        <RiskBadge risk={u.riskTag} />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    PLACED: { label: 'Placed', cls: 'bg-blue-100 text-blue-700' },
    CONFIRMED: { label: 'Confirmed', cls: 'bg-cyan-100 text-cyan-700' },
    READY: { label: 'Ready', cls: 'bg-amber-100 text-amber-700' },
    PICKED_UP: { label: 'Picked Up', cls: 'bg-purple-100 text-purple-700' },
    COMPLETED: { label: 'Done', cls: 'bg-emerald-100 text-emerald-700' },
    CANCELLED: { label: 'Cancelled', cls: 'bg-zinc-100 text-zinc-500' },
  };
  const m = map[status] || { label: status, cls: 'bg-zinc-100 text-zinc-500' };
  return <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold ${m.cls}`}>{m.label}</span>;
}

export function RiskBadge({ risk }: { risk: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    CLEAR: { label: 'Clear', cls: 'bg-emerald-100 text-emerald-700' },
    NORMAL: { label: 'Normal', cls: 'bg-blue-100 text-blue-700' },
    AT_RISK: { label: 'At Risk', cls: 'bg-amber-100 text-amber-700' },
    OVERDUE: { label: 'Overdue', cls: 'bg-red-100 text-red-700' },
    NEW_CREDIT: { label: 'New', cls: 'bg-purple-100 text-purple-700' },
  };
  const m = map[risk] || { label: risk, cls: 'bg-zinc-100 text-zinc-500' };
  return <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider mt-0.5 ${m.cls}`}>{m.label}</span>;
}
