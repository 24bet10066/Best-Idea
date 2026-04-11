import { ScrollArea } from '@/components/ui/scroll-area';
import type { Technician, Order, UdhaarSummary, DashboardStats } from '../../types';
import { OverviewView } from './views/OverviewView';
import { TechniciansView } from './views/TechniciansView';
import { OrdersView } from './views/OrdersView';
import { UdhaarView } from './views/UdhaarView';

export type ViewType = 'overview' | 'technicians' | 'orders' | 'udhaar';

interface WorkspaceRouterProps {
  activeView: ViewType;
  onChangeView: (view: ViewType) => void;
  technicians: Technician[];
  orders: Order[];
  udhaarSummaries: UdhaarSummary[];
  stats: DashboardStats;
  isLive?: boolean;
  // Order action callbacks — wired to API when live
  onConfirmOrder?: (id: string) => Promise<unknown>;
  onMarkReady?: (id: string) => Promise<unknown>;
  onMarkPickedUp?: (id: string) => Promise<unknown>;
  onCompleteOrder?: (id: string) => Promise<unknown>;
  onCancelOrder?: (id: string) => Promise<unknown>;
  onRecordPayment?: (technicianId: string, amount: number, paymentMode: string, referenceNumber?: string, notes?: string) => Promise<unknown>;
}

// Visual mode icons — color-coded, mental-model aligned
const MODES: { id: ViewType; label: string; color: string; activeBg: string; activeText: string; svg: string }[] = [
  {
    id: 'overview', label: 'Dashboard', color: 'text-zinc-500',
    activeBg: 'bg-zinc-900', activeText: 'text-white',
    svg: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-4 0h4',
  },
  {
    id: 'technicians', label: 'People', color: 'text-indigo-500',
    activeBg: 'bg-indigo-600', activeText: 'text-white',
    svg: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z',
  },
  {
    id: 'orders', label: 'Orders', color: 'text-blue-500',
    activeBg: 'bg-blue-600', activeText: 'text-white',
    svg: 'M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4',
  },
  {
    id: 'udhaar', label: 'Money', color: 'text-amber-500',
    activeBg: 'bg-amber-600', activeText: 'text-white',
    svg: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  },
];

export function WorkspaceRouter({
  activeView, onChangeView, technicians, orders, udhaarSummaries, stats,
  onConfirmOrder, onMarkReady, onMarkPickedUp, onCompleteOrder, onCancelOrder, onRecordPayment,
}: WorkspaceRouterProps) {
  return (
    <div className="flex-1 flex flex-col min-w-0 bg-zinc-50">
      {/* Mode Bar */}
      <div className="h-12 border-b border-zinc-200 bg-white flex items-end px-3 gap-0.5 shrink-0">
        {MODES.map(mode => {
          const isActive = activeView === mode.id;
          return (
            <button
              key={mode.id}
              onClick={() => onChangeView(mode.id)}
              className={`relative flex items-center gap-2 px-4 py-2 rounded-t-lg text-sm font-medium transition-all ${
                isActive
                  ? `${mode.activeBg} ${mode.activeText} shadow-sm`
                  : `text-zinc-500 hover:text-zinc-700 hover:bg-zinc-50`
              }`}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" className="shrink-0">
                <path d={mode.svg} />
              </svg>
              <span>{mode.label}</span>
              {isActive && (
                <span className="absolute -bottom-px left-2 right-2 h-0.5 rounded-t-full bg-current" />
              )}
            </button>
          );
        })}
      </div>

      {/* View Content */}
      <ScrollArea className="flex-1">
        {activeView === 'overview' && (
          <OverviewView
            stats={stats}
            recentOrders={orders}
            overdueUdhaar={udhaarSummaries.filter(u => u.riskTag === 'OVERDUE' || u.riskTag === 'AT_RISK')}
            onViewOrders={() => onChangeView('orders')}
            onViewUdhaar={() => onChangeView('udhaar')}
          />
        )}
        {activeView === 'technicians' && (
          <TechniciansView
            technicians={technicians}
            udhaarSummaries={udhaarSummaries}
            orders={orders}
          />
        )}
        {activeView === 'orders' && (
          <OrdersView
            orders={orders}
            onConfirmOrder={onConfirmOrder}
            onMarkReady={onMarkReady}
            onMarkPickedUp={onMarkPickedUp}
            onCompleteOrder={onCompleteOrder}
            onCancelOrder={onCancelOrder}
          />
        )}
        {activeView === 'udhaar' && (
          <UdhaarView
            summaries={udhaarSummaries}
            onRecordPayment={onRecordPayment}
          />
        )}
      </ScrollArea>
    </div>
  );
}
