import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import type { Technician, UdhaarSummary, Order } from '../../../types';
import { formatINRFull, timeAgo } from '../../../lib/format';
import { RiskBadge } from './OverviewView';

interface TechniciansViewProps {
  technicians: Technician[];
  udhaarSummaries: UdhaarSummary[];
  orders: Order[];
}

export function TechniciansView({ technicians, udhaarSummaries, orders }: TechniciansViewProps) {
  const [search, setSearch] = useState('');
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const filtered = technicians.filter(t =>
    t.fullName.toLowerCase().includes(search.toLowerCase()) ||
    t.phone.includes(search) ||
    t.specializations.some(s => s.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="p-5 max-w-5xl">
      {/* Header + Search */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-zinc-800">Technicians</h2>
          <p className="text-xs text-zinc-500 mt-0.5">{technicians.length} registered, {technicians.filter(t => t.isActive).length} active</p>
        </div>
        <Input
          placeholder="Search by name, phone, or type..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-72 h-9 text-sm"
        />
      </div>

      {/* Technician List */}
      <div className="space-y-2">
        {filtered.length === 0 ? (
          <Card className="border border-zinc-200 shadow-none">
            <CardContent className="p-8 text-center">
              <p className="text-2xl mb-2">🔍</p>
              <p className="text-sm text-zinc-500">No technicians match "{search}"</p>
            </CardContent>
          </Card>
        ) : (
          filtered.map(tech => {
            const isExpanded = expandedId === tech.id;
            const udhaar = udhaarSummaries.find(u => u.technicianId === tech.id);
            const techOrders = orders.filter(o => o.technicianId === tech.id);

            return (
              <Card
                key={tech.id}
                className={`border shadow-none transition-all duration-200 cursor-pointer ${isExpanded ? 'border-blue-200 bg-blue-50/20' : 'border-zinc-200 hover:border-zinc-300 hover:shadow-sm'}`}
                onClick={() => setExpandedId(isExpanded ? null : tech.id)}
              >
                <CardContent className="p-0">
                  {/* Main Row */}
                  <div className="flex items-center gap-4 px-4 py-3">
                    {/* Avatar */}
                    <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-sm font-bold text-white shrink-0 ${getTrustColor(tech.trustScore).bg}`}>
                      {tech.fullName.split(' ').map(n => n[0]).join('').slice(0, 2)}
                    </div>

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm text-zinc-800">{tech.fullName}</span>
                        {tech.isActive && <span className="text-[10px] text-emerald-600 font-bold">● ACTIVE</span>}
                        {!tech.isActive && <span className="text-[10px] text-zinc-400 font-bold">○ INACTIVE</span>}
                      </div>
                      <div className="flex items-center gap-3 mt-0.5">
                        <span className="text-[11px] text-zinc-500">{tech.phone}</span>
                        <span className="text-[11px] text-zinc-400">|</span>
                        <div className="flex gap-1">
                          {tech.specializations.map(s => (
                            <span key={s} className="text-[9px] font-semibold px-1.5 py-0.5 rounded bg-zinc-100 text-zinc-600 uppercase">{s.replace('_', ' ')}</span>
                          ))}
                        </div>
                      </div>
                    </div>

                    {/* Trust Score Bar */}
                    <div className="w-32 shrink-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-[10px] text-zinc-500 uppercase font-semibold">Trust</span>
                        <span className={`text-sm font-mono font-bold tabular-nums ${getTrustColor(tech.trustScore).text}`}>{tech.trustScore}</span>
                      </div>
                      <div className="h-2 bg-zinc-100 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${getTrustColor(tech.trustScore).bar}`}
                          style={{ width: `${tech.trustScore}%` }}
                        />
                      </div>
                    </div>

                    {/* Credit Limit */}
                    <div className="text-right shrink-0 w-24">
                      <p className="text-[10px] text-zinc-500 uppercase font-semibold">Credit Limit</p>
                      <p className="font-mono font-bold text-sm tabular-nums text-zinc-700">{formatINRFull(tech.creditLimit)}</p>
                    </div>

                    {/* Transactions */}
                    <div className="text-right shrink-0 w-16">
                      <p className="text-[10px] text-zinc-500 uppercase font-semibold">Txns</p>
                      <p className="font-mono font-bold text-sm tabular-nums text-zinc-700">{tech.totalTransactions}</p>
                    </div>

                    {/* Expand icon */}
                    <div className={`text-zinc-400 transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 9l6 6 6-6" /></svg>
                    </div>
                  </div>

                  {/* Expanded Detail */}
                  {isExpanded && (
                    <div className="border-t border-zinc-100 px-4 py-4 bg-zinc-50/50" onClick={e => e.stopPropagation()}>
                      <div className="grid grid-cols-2 gap-5">
                        {/* Udhaar Summary */}
                        <div>
                          <h4 className="text-xs font-bold text-zinc-600 uppercase tracking-wider mb-2">Udhaar Balance</h4>
                          {udhaar ? (
                            <div className="space-y-2">
                              <div className="flex justify-between items-center">
                                <span className="text-sm text-zinc-600">Total Credit</span>
                                <span className="font-mono font-semibold text-sm">{formatINRFull(udhaar.totalCredit)}</span>
                              </div>
                              <div className="flex justify-between items-center">
                                <span className="text-sm text-zinc-600">Total Paid</span>
                                <span className="font-mono font-semibold text-sm text-emerald-600">{formatINRFull(udhaar.totalPaid)}</span>
                              </div>
                              <div className="h-px bg-zinc-200 my-1" />
                              <div className="flex justify-between items-center">
                                <span className="text-sm font-semibold text-zinc-800">Outstanding</span>
                                <div className="flex items-center gap-2">
                                  <span className={`font-mono font-bold text-base tabular-nums ${udhaar.currentBalance > 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                                    {formatINRFull(udhaar.currentBalance)}
                                  </span>
                                  <RiskBadge risk={udhaar.riskTag} />
                                </div>
                              </div>
                              {udhaar.daysSinceLastPayment != null && udhaar.daysSinceLastPayment > 0 && (
                                <p className="text-[11px] text-zinc-500 mt-1">Last payment: {udhaar.daysSinceLastPayment} days ago</p>
                              )}
                            </div>
                          ) : (
                            <p className="text-sm text-zinc-400">No credit history</p>
                          )}
                        </div>

                        {/* Recent Orders */}
                        <div>
                          <h4 className="text-xs font-bold text-zinc-600 uppercase tracking-wider mb-2">Recent Orders ({techOrders.length})</h4>
                          {techOrders.length === 0 ? (
                            <p className="text-sm text-zinc-400">No orders yet</p>
                          ) : (
                            <div className="space-y-1.5">
                              {techOrders.slice(0, 4).map(o => (
                                <div key={o.id} className="flex items-center justify-between text-xs bg-white rounded-md px-2.5 py-2 border border-zinc-100">
                                  <span className="font-mono text-zinc-600">{o.orderNumber}</span>
                                  <span className="font-mono font-semibold tabular-nums">{formatINRFull(o.totalAmount)}</span>
                                  <StatusMini status={o.status} />
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>

                      {/* Quick Stats */}
                      <div className="flex gap-4 mt-4 pt-3 border-t border-zinc-200">
                        <Stat label="Avg Payment Days" value={`${tech.avgPaymentDays}d`} />
                        <Stat label="Member Since" value={new Date(tech.registeredAt).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })} />
                        <Stat label="Last Active" value={timeAgo(tech.lastActiveAt)} />
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })
        )}
      </div>
    </div>
  );
}

function getTrustColor(score: number) {
  if (score >= 80) return { text: 'text-emerald-600', bar: 'bg-emerald-500', bg: 'bg-emerald-600' };
  if (score >= 60) return { text: 'text-green-600', bar: 'bg-green-500', bg: 'bg-green-600' };
  if (score >= 40) return { text: 'text-amber-600', bar: 'bg-amber-500', bg: 'bg-amber-600' };
  return { text: 'text-red-600', bar: 'bg-red-500', bg: 'bg-red-600' };
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-[10px] text-zinc-500 uppercase font-semibold">{label}</p>
      <p className="text-sm font-semibold text-zinc-700 mt-0.5">{value}</p>
    </div>
  );
}

function StatusMini({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PLACED: 'bg-blue-500', CONFIRMED: 'bg-cyan-500', READY: 'bg-amber-500',
    PICKED_UP: 'bg-purple-500', COMPLETED: 'bg-emerald-500', CANCELLED: 'bg-zinc-400',
  };
  return <span className={`w-2 h-2 rounded-full shrink-0 ${colors[status] || 'bg-zinc-400'}`} />;
}
