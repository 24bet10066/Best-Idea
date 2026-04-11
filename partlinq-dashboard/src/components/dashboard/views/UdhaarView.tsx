import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import type { UdhaarSummary, RiskTag } from '../../../types';
import { formatINRFull } from '../../../hooks/useMockData';
import { RiskBadge } from './OverviewView';

interface UdhaarViewProps {
  summaries: UdhaarSummary[];
  onRecordPayment?: (technicianId: string, amount: number, paymentMode: string, referenceNumber?: string, notes?: string) => Promise<unknown>;
}

type SortKey = 'balance' | 'risk' | 'days';

const RISK_ORDER: Record<RiskTag, number> = {
  OVERDUE: 0, AT_RISK: 1, NEW_CREDIT: 2, NORMAL: 3, CLEAR: 4,
};

export function UdhaarView({ summaries, onRecordPayment }: UdhaarViewProps) {
  const [sortBy, setSortBy] = useState<SortKey>('risk');
  const [search, setSearch] = useState('');
  const [paymentDialog, setPaymentDialog] = useState<UdhaarSummary | null>(null);
  const [paymentAmount, setPaymentAmount] = useState('');
  const [paymentMode, setPaymentMode] = useState('UPI');
  const [paymentRef, setPaymentRef] = useState('');
  const [paymentLoading, setPaymentLoading] = useState(false);

  const filtered = summaries
    .filter(s => s.technicianName.toLowerCase().includes(search.toLowerCase()))
    .sort((a, b) => {
      if (sortBy === 'balance') return b.currentBalance - a.currentBalance;
      if (sortBy === 'days') return (b.daysSinceLastPayment || 0) - (a.daysSinceLastPayment || 0);
      return RISK_ORDER[a.riskTag] - RISK_ORDER[b.riskTag];
    });

  const totalOutstanding = summaries.reduce((s, u) => s + u.currentBalance, 0);
  const overdue = summaries.filter(u => u.riskTag === 'OVERDUE').length;
  const atRisk = summaries.filter(u => u.riskTag === 'AT_RISK').length;

  const handleRecordPayment = async () => {
    if (!paymentDialog || !paymentAmount) return;
    const amt = parseFloat(paymentAmount);
    if (isNaN(amt) || amt <= 0) return;

    setPaymentLoading(true);
    try {
      await onRecordPayment?.(paymentDialog.technicianId, amt, paymentMode, paymentRef || undefined);
      setPaymentDialog(null);
      setPaymentAmount('');
      setPaymentRef('');
    } catch (err) {
      console.error('Payment recording failed:', err);
    } finally {
      setPaymentLoading(false);
    }
  };

  return (
    <div className="p-5 max-w-5xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-zinc-800">Udhaar / Credit Ledger</h2>
          <p className="text-xs text-zinc-500 mt-0.5">Track outstanding credit balances</p>
        </div>
        <Input
          placeholder="Search technician..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-60 h-9 text-sm"
        />
      </div>

      {/* Summary Strip */}
      <div className="flex gap-4 mb-5">
        <SummaryCard label="Total Outstanding" value={formatINRFull(totalOutstanding)} color="text-red-600" />
        <SummaryCard label="Overdue" value={String(overdue)} color="text-red-600" />
        <SummaryCard label="At Risk" value={String(atRisk)} color="text-amber-600" />
        <SummaryCard label="Clear" value={String(summaries.filter(u => u.riskTag === 'CLEAR').length)} color="text-emerald-600" />
      </div>

      {/* Sort Controls */}
      <div className="flex items-center gap-2 mb-3">
        <span className="text-xs text-zinc-500">Sort by:</span>
        {(['risk', 'balance', 'days'] as SortKey[]).map(key => (
          <button
            key={key}
            onClick={() => setSortBy(key)}
            className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${sortBy === key ? 'bg-zinc-800 text-white' : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200'}`}
          >
            {key === 'risk' ? 'Risk Level' : key === 'balance' ? 'Amount' : 'Days Since Payment'}
          </button>
        ))}
      </div>

      {/* Udhaar Cards */}
      <div className="space-y-2">
        {filtered.length === 0 ? (
          <Card className="border border-zinc-200 shadow-none">
            <CardContent className="p-8 text-center">
              <p className="text-2xl mb-2">📖</p>
              <p className="text-sm text-zinc-500">No matching records</p>
            </CardContent>
          </Card>
        ) : (
          filtered.map(u => (
            <Card
              key={u.technicianId}
              className={`border shadow-none hover:shadow-sm transition-all ${riskBorderColor(u.riskTag)}`}
            >
              <CardContent className="p-4">
                <div className="flex items-center gap-4">
                  {/* Risk Indicator */}
                  <div className={`w-1.5 h-12 rounded-full shrink-0 ${riskBarColor(u.riskTag)}`} />

                  {/* Name + Details */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-sm text-zinc-800">{u.technicianName}</span>
                      <RiskBadge risk={u.riskTag} />
                    </div>
                    <div className="flex items-center gap-3 mt-1">
                      <span className="text-[11px] text-zinc-500">{u.technicianPhone}</span>
                      {u.daysSinceLastPayment != null && u.daysSinceLastPayment > 0 && (
                        <>
                          <span className="text-[11px] text-zinc-400">|</span>
                          <span className={`text-[11px] font-medium ${u.daysSinceLastPayment > 15 ? 'text-red-600' : 'text-zinc-500'}`}>
                            Last paid: {u.daysSinceLastPayment}d ago
                          </span>
                        </>
                      )}
                      {u.totalUnpaidOrders > 0 && (
                        <>
                          <span className="text-[11px] text-zinc-400">|</span>
                          <span className="text-[11px] text-zinc-500">{u.totalUnpaidOrders} unpaid order{u.totalUnpaidOrders > 1 ? 's' : ''}</span>
                        </>
                      )}
                    </div>
                  </div>

                  {/* Stacked paid/credit numbers */}
                  <div className="shrink-0 flex items-center gap-3">
                    <div className="text-right">
                      <p className="text-[10px] text-zinc-400 uppercase font-semibold">Paid</p>
                      <p className="font-mono text-xs font-semibold tabular-nums text-emerald-600">{formatINRFull(u.totalPaid)}</p>
                    </div>
                    <div className="w-px h-8 bg-zinc-200" />
                    <div className="text-right">
                      <p className="text-[10px] text-zinc-400 uppercase font-semibold">Credit</p>
                      <p className="font-mono text-xs font-semibold tabular-nums text-zinc-500">{formatINRFull(u.totalCredit)}</p>
                    </div>
                  </div>

                  {/* Outstanding Amount */}
                  <div className="text-right shrink-0 w-24">
                    <p className="text-[10px] text-zinc-500 uppercase font-semibold">Balance</p>
                    <p className={`font-mono font-bold text-base tabular-nums ${u.currentBalance > 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                      {formatINRFull(u.currentBalance)}
                    </p>
                  </div>

                  {/* Action */}
                  {u.currentBalance > 0 && (
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-xs h-8 px-3 shrink-0 border-emerald-300 text-emerald-700 hover:bg-emerald-50"
                      onClick={() => { setPaymentDialog(u); setPaymentAmount(''); setPaymentRef(''); }}
                    >
                      Record Payment
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {/* Payment Dialog */}
      <Dialog open={!!paymentDialog} onOpenChange={(open) => !open && setPaymentDialog(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-base">Record Payment — {paymentDialog?.technicianName}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 mt-2">
            <div>
              <p className="text-xs text-zinc-500 mb-1">Outstanding Balance</p>
              <p className="font-mono font-bold text-xl text-red-600 tabular-nums">{paymentDialog ? formatINRFull(paymentDialog.currentBalance) : ''}</p>
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700 block mb-1">Payment Amount</label>
              <Input
                type="number"
                placeholder="Enter amount in ₹"
                value={paymentAmount}
                onChange={e => setPaymentAmount(e.target.value)}
                className="font-mono"
              />
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700 block mb-1">Payment Mode</label>
              <Select value={paymentMode} onValueChange={setPaymentMode}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UPI">UPI</SelectItem>
                  <SelectItem value="CASH">Cash</SelectItem>
                  <SelectItem value="BANK_TRANSFER">Bank Transfer</SelectItem>
                  <SelectItem value="CHEQUE">Cheque</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-xs font-medium text-zinc-700 block mb-1">Reference (optional)</label>
              <Input
                placeholder="UPI ref / cheque no."
                value={paymentRef}
                onChange={e => setPaymentRef(e.target.value)}
                className="text-sm"
              />
            </div>
            <div className="flex gap-2 pt-2">
              <Button variant="outline" className="flex-1" onClick={() => setPaymentDialog(null)}>Cancel</Button>
              <Button
                className="flex-1 bg-emerald-600 hover:bg-emerald-700"
                disabled={paymentLoading}
                onClick={handleRecordPayment}
              >
                {paymentLoading ? 'Recording...' : 'Confirm Payment'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SummaryCard({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="flex-1 bg-white border border-zinc-200 rounded-lg px-3 py-2.5">
      <p className="text-[10px] text-zinc-500 uppercase font-semibold tracking-wider">{label}</p>
      <p className={`font-mono font-bold text-xl mt-0.5 tabular-nums ${color}`}>{value}</p>
    </div>
  );
}

function riskBorderColor(risk: RiskTag): string {
  const map: Record<RiskTag, string> = {
    OVERDUE: 'border-red-200 bg-red-50/20', AT_RISK: 'border-amber-200 bg-amber-50/20',
    NEW_CREDIT: 'border-purple-200', NORMAL: 'border-zinc-200', CLEAR: 'border-emerald-200 bg-emerald-50/20',
  };
  return map[risk] || 'border-zinc-200';
}

function riskBarColor(risk: RiskTag): string {
  const map: Record<RiskTag, string> = {
    OVERDUE: 'bg-red-500', AT_RISK: 'bg-amber-500', NEW_CREDIT: 'bg-purple-500', NORMAL: 'bg-blue-500', CLEAR: 'bg-emerald-500',
  };
  return map[risk] || 'bg-zinc-400';
}
