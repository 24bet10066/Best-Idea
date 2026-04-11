import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import type { Order, OrderStatus } from '../../../types';
import { formatINRFull, timeAgo } from '../../../hooks/useMockData';

interface OrdersViewProps {
  orders: Order[];
  onConfirmOrder?: (id: string) => Promise<unknown>;
  onMarkReady?: (id: string) => Promise<unknown>;
  onMarkPickedUp?: (id: string) => Promise<unknown>;
  onCompleteOrder?: (id: string) => Promise<unknown>;
  onCancelOrder?: (id: string) => Promise<unknown>;
}

const STATUS_FLOW: { status: OrderStatus; label: string; color: string; bgLight: string }[] = [
  { status: 'PLACED', label: 'Placed', color: 'bg-blue-500', bgLight: 'bg-blue-50 border-blue-200' },
  { status: 'CONFIRMED', label: 'Confirmed', color: 'bg-cyan-500', bgLight: 'bg-cyan-50 border-cyan-200' },
  { status: 'READY', label: 'Ready', color: 'bg-amber-500', bgLight: 'bg-amber-50 border-amber-200' },
  { status: 'PICKED_UP', label: 'Picked Up', color: 'bg-purple-500', bgLight: 'bg-purple-50 border-purple-200' },
  { status: 'COMPLETED', label: 'Completed', color: 'bg-emerald-500', bgLight: 'bg-emerald-50 border-emerald-200' },
];

export function OrdersView({ orders, onConfirmOrder, onMarkReady, onMarkPickedUp, onCompleteOrder, onCancelOrder }: OrdersViewProps) {
  const [activeFilter, setActiveFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [expandedOrder, setExpandedOrder] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const counts = STATUS_FLOW.map(sf => ({
    ...sf,
    count: orders.filter(o => o.status === sf.status).length,
  }));

  const filtered = activeFilter === 'ALL'
    ? orders.filter(o => o.status !== 'CANCELLED')
    : orders.filter(o => o.status === activeFilter);

  const handleAction = async (orderId: string, status: string) => {
    setActionLoading(orderId);
    try {
      switch (status) {
        case 'PLACED': await onConfirmOrder?.(orderId); break;
        case 'CONFIRMED': await onMarkReady?.(orderId); break;
        case 'READY': await onMarkPickedUp?.(orderId); break;
        case 'PICKED_UP': await onCompleteOrder?.(orderId); break;
      }
    } catch (err) {
      console.error('Order action failed:', err);
    } finally {
      setActionLoading(null);
    }
  };

  const ACTION_MAP: Record<string, string> = {
    PLACED: 'Confirm Order',
    CONFIRMED: 'Mark Ready',
    READY: 'Mark Picked Up',
    PICKED_UP: 'Complete',
  };

  return (
    <div className="p-5 max-w-5xl">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-zinc-800">Orders</h2>
          <p className="text-xs text-zinc-500 mt-0.5">{orders.length} total orders</p>
        </div>
      </div>

      {/* Status Pipeline */}
      <div className="flex items-center gap-1 mb-5 p-1 bg-zinc-100 rounded-lg">
        <PipelineButton
          active={activeFilter === 'ALL'}
          onClick={() => setActiveFilter('ALL')}
          label="All"
          count={orders.filter(o => o.status !== 'CANCELLED').length}
          color="bg-zinc-500"
        />
        {counts.map(c => (
          <PipelineButton
            key={c.status}
            active={activeFilter === c.status}
            onClick={() => setActiveFilter(c.status)}
            label={c.label}
            count={c.count}
            color={c.color}
          />
        ))}
      </div>

      {/* Orders List */}
      <div className="space-y-2">
        {filtered.length === 0 ? (
          <Card className="border border-zinc-200 shadow-none">
            <CardContent className="p-8 text-center">
              <p className="text-2xl mb-2">📭</p>
              <p className="text-sm text-zinc-500">No {activeFilter !== 'ALL' ? activeFilter.toLowerCase().replace('_', ' ') : ''} orders</p>
            </CardContent>
          </Card>
        ) : (
          filtered.map(order => {
            const isExpanded = expandedOrder === order.id;
            const actionLabel = ACTION_MAP[order.status];
            const sfConfig = STATUS_FLOW.find(sf => sf.status === order.status);
            const isLoading = actionLoading === order.id;

            return (
              <Card
                key={order.id}
                className={`border shadow-none transition-all duration-200 ${isExpanded ? `${sfConfig?.bgLight || 'border-zinc-200'}` : 'border-zinc-200 hover:shadow-sm'}`}
              >
                <CardContent className="p-0">
                  <div
                    className="flex items-center gap-4 px-4 py-3 cursor-pointer"
                    onClick={() => setExpandedOrder(isExpanded ? null : order.id)}
                  >
                    {/* Status Dot */}
                    <div className={`w-3 h-3 rounded-full shrink-0 ${sfConfig?.color || 'bg-zinc-400'}`} />

                    {/* Order Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-xs font-semibold text-zinc-700">{order.orderNumber}</span>
                        {order.creditUsed && (
                          <span className="text-[9px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 uppercase">Udhaar</span>
                        )}
                      </div>
                      <p className="text-xs text-zinc-500 mt-0.5">
                        {order.technicianName} &middot; {order.items.length} item{order.items.length > 1 ? 's' : ''}
                      </p>
                    </div>

                    {/* Amount */}
                    <div className="text-right shrink-0">
                      <p className="font-mono font-bold text-sm tabular-nums text-zinc-800">{formatINRFull(order.totalAmount)}</p>
                      <p className="text-[10px] text-zinc-400 mt-0.5">{timeAgo(order.createdAt)}</p>
                    </div>

                    {/* Action Button */}
                    {actionLabel && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-xs h-7 px-3 shrink-0"
                        disabled={isLoading}
                        onClick={(e) => { e.stopPropagation(); handleAction(order.id, order.status); }}
                      >
                        {isLoading ? '...' : actionLabel}
                      </Button>
                    )}

                    {/* Expand */}
                    <div className={`text-zinc-400 transition-transform duration-200 shrink-0 ${isExpanded ? 'rotate-180' : ''}`}>
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 9l6 6 6-6" /></svg>
                    </div>
                  </div>

                  {/* Expanded items */}
                  {isExpanded && (
                    <div className="border-t border-zinc-100 px-4 py-3" onClick={e => e.stopPropagation()}>
                      <div className="space-y-1.5">
                        {order.items.map(item => (
                          <div key={item.id} className="flex items-center justify-between py-1.5 px-2.5 bg-zinc-50 rounded-md">
                            <span className="text-xs text-zinc-700">
                              <span className="font-semibold">{item.quantity}x</span>{' '}
                              {item.partName}
                            </span>
                            <span className="font-mono font-semibold text-xs tabular-nums text-zinc-800">{formatINRFull(item.lineTotal)}</span>
                          </div>
                        ))}
                        <div className="flex items-center justify-between pt-2 mt-1 border-t border-zinc-200 px-2.5">
                          <span className="text-xs font-semibold text-zinc-700">Total</span>
                          <span className="font-mono font-bold text-sm tabular-nums text-zinc-900">{formatINRFull(order.totalAmount)}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-3 mt-3 pt-2 border-t border-zinc-100">
                        <span className="text-[11px] text-zinc-500">Shop: <span className="font-medium text-zinc-700">{order.shopName}</span></span>
                        <span className="text-[11px] text-zinc-400">|</span>
                        <span className="text-[11px] text-zinc-500">{new Date(order.createdAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}</span>
                        {order.status !== 'COMPLETED' && order.status !== 'CANCELLED' && onCancelOrder && (
                          <>
                            <span className="text-[11px] text-zinc-400">|</span>
                            <button
                              className="text-[11px] text-red-500 hover:text-red-700 font-medium"
                              onClick={() => onCancelOrder(order.id)}
                            >
                              Cancel Order
                            </button>
                          </>
                        )}
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

function PipelineButton({ active, onClick, label, count, color }: { active: boolean; onClick: () => void; label: string; count: number; color: string }) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-all ${active ? 'bg-white shadow-sm text-zinc-800' : 'text-zinc-500 hover:text-zinc-700 hover:bg-zinc-50'}`}
    >
      <span className={`w-2 h-2 rounded-full ${color}`} />
      {label}
      <span className={`font-mono text-[10px] tabular-nums ${active ? 'text-zinc-600' : 'text-zinc-400'}`}>{count}</span>
    </button>
  );
}
