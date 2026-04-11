import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../services/api';
import { useMockData } from './useMockData';
import type { Technician, Order, PartsShop, UdhaarSummary, TickerEvent, DashboardStats } from '../types';

/**
 * Smart data hook that tries the live API first, falls back to mock data.
 * This ensures the dashboard always works — even during development without backend.
 *
 * Pattern: API-first with graceful degradation
 * - First load: tries API, falls back to mock on failure
 * - Subsequent refreshes: retries API periodically
 * - Shows "LIVE" or "DEMO" badge based on data source
 */
export function useApiData(shopId?: string) {
  const mock = useMockData();
  // Stable ref to avoid re-creating callbacks when mock data hasn't actually changed
  const mockRef = useRef(mock);
  mockRef.current = mock;

  const [isLive, setIsLive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Live data state
  const [technicians, setTechnicians] = useState<Technician[]>(mock.technicians);
  const [orders, setOrders] = useState<Order[]>(mock.orders);
  const [shops, setShops] = useState<PartsShop[]>(mock.shops);
  const [udhaarSummaries, setUdhaarSummaries] = useState<UdhaarSummary[]>(mock.udhaarSummaries);
  const [stats, setStats] = useState<DashboardStats>(mock.stats);
  const [tickerEvents, setTickerEvents] = useState<TickerEvent[]>(mock.tickerEvents);

  // Track active shop
  const activeShopId = useRef<string | undefined>(shopId);

  const fetchLiveData = useCallback(async () => {
    try {
      // Fetch shops first to get the active shop ID
      const liveShops = await api.getShops();
      if (liveShops.length === 0) {
        throw new Error('No shops registered');
      }
      setShops(liveShops);

      const sid = activeShopId.current || liveShops[0].id;

      // Parallel fetch all data
      const [liveTechs, liveOrders, outstandingData] = await Promise.all([
        api.getTechnicians(),
        api.getOrdersByShop(sid),
        api.getOutstandingForShop(sid),
      ]);

      setTechnicians(liveTechs);
      setOrders(liveOrders);
      setUdhaarSummaries(outstandingData.accounts);

      // Compute stats from live data
      const now = new Date();
      const todayStr = now.toISOString().slice(0, 10);
      const todayOrders = liveOrders.filter(o =>
        o.createdAt && o.createdAt.slice(0, 10) === todayStr
      );
      const todaySales = todayOrders.reduce((sum, o) => sum + o.totalAmount, 0);
      const pendingOrders = liveOrders.filter(o =>
        ['PLACED', 'CONFIRMED', 'READY'].includes(o.status)
      ).length;
      const overduePayments = outstandingData.overdueCount;

      setStats({
        todaySales,
        totalOutstanding: outstandingData.totalOutstanding,
        pendingOrders,
        overduePayments,
        activeTechnicians: liveTechs.filter(t => t.isActive).length,
        lowStockItems: 0, // TODO: fetch from inventory endpoint
      });

      // Build ticker events from recent orders and payments
      const recentEvents: TickerEvent[] = [];
      liveOrders.slice(0, 10).forEach((o, idx) => {
        recentEvents.push({
          id: `evt-order-${idx}`,
          type: 'order',
          technicianName: o.technicianName,
          amount: o.totalAmount,
          status: o.status,
          message: `Order ${o.orderNumber} — ${o.status}`,
          timestamp: o.updatedAt || o.createdAt,
        });
      });

      // Add payment events from udhaar
      outstandingData.accounts.slice(0, 5).forEach((u, idx) => {
        if (u.lastPaymentDate) {
          recentEvents.push({
            id: `evt-pay-${idx}`,
            type: 'payment',
            technicianName: u.technicianName,
            amount: u.totalPaid,
            message: `₹${u.totalPaid.toLocaleString('en-IN')} paid — ${u.riskTag}`,
            timestamp: u.lastPaymentDate,
          });
        }
        if (u.riskTag === 'OVERDUE' || u.riskTag === 'AT_RISK') {
          recentEvents.push({
            id: `evt-alert-${idx}`,
            type: 'alert',
            technicianName: u.technicianName,
            amount: u.currentBalance,
            message: `₹${u.currentBalance.toLocaleString('en-IN')} overdue — ${u.daysSinceLastPayment}d`,
            timestamp: u.lastCreditDate || new Date().toISOString(),
          });
        }
      });

      // Sort by timestamp desc
      recentEvents.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
      setTickerEvents(recentEvents.length > 0 ? recentEvents : mockRef.current.tickerEvents);

      setIsLive(true);
      setError(null);
    } catch (err: unknown) {
      console.warn('PartLinQ API unavailable, using demo data:', err instanceof Error ? err.message : err);
      // Keep mock data as fallback
      setTechnicians(mockRef.current.technicians);
      setOrders(mockRef.current.orders);
      setShops(mockRef.current.shops);
      setUdhaarSummaries(mockRef.current.udhaarSummaries);
      setStats(mockRef.current.stats);
      setTickerEvents(mockRef.current.tickerEvents);
      setIsLive(false);
      setError(err instanceof Error ? err.message : 'API unavailable');
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial fetch
  useEffect(() => {
    fetchLiveData();
  }, [fetchLiveData]);

  // Auto-refresh every 30 seconds if live
  useEffect(() => {
    if (!isLive) return;
    const interval = setInterval(fetchLiveData, 30000);
    return () => clearInterval(interval);
  }, [isLive, fetchLiveData]);

  // Order action handlers that call the real API
  const confirmOrder = useCallback(async (orderId: string) => {
    if (isLive) {
      const updated = await api.confirmOrder(orderId);
      setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
      return updated;
    }
    // Mock fallback
    setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: 'CONFIRMED' as const } : o));
  }, [isLive]);

  const markOrderReady = useCallback(async (orderId: string) => {
    if (isLive) {
      const updated = await api.markReady(orderId);
      setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
      return updated;
    }
    setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: 'READY' as const } : o));
  }, [isLive]);

  const markOrderPickedUp = useCallback(async (orderId: string) => {
    if (isLive) {
      const updated = await api.markPickedUp(orderId);
      setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
      return updated;
    }
    setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: 'PICKED_UP' as const } : o));
  }, [isLive]);

  const completeOrder = useCallback(async (orderId: string) => {
    if (isLive) {
      const updated = await api.completeOrder(orderId);
      setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
      return updated;
    }
    setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: 'COMPLETED' as const } : o));
  }, [isLive]);

  const cancelOrder = useCallback(async (orderId: string) => {
    if (isLive) {
      const updated = await api.cancelOrder(orderId);
      setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
      return updated;
    }
    setOrders(prev => prev.map(o => o.id === orderId ? { ...o, status: 'CANCELLED' as const } : o));
  }, [isLive]);

  const recordPayment = useCallback(async (
    technicianId: string, amount: number, paymentMode: string,
    referenceNumber?: string, notes?: string
  ) => {
    const sid = activeShopId.current || shops[0]?.id;
    if (!sid) return;

    if (isLive) {
      const result = await api.recordPayment({
        technicianId, shopId: sid, amount, paymentMode,
        referenceNumber, notes, recordedBy: shops[0]?.ownerName || 'Shop Owner',
      });
      // Refresh udhaar data
      const outstandingData = await api.getOutstandingForShop(sid);
      setUdhaarSummaries(outstandingData.accounts);
      return result;
    }
  }, [isLive, shops]);

  return {
    // Data
    technicians,
    orders,
    shops,
    udhaarSummaries,
    stats,
    tickerEvents,

    // State
    isLive,
    loading,
    error,

    // Actions
    confirmOrder,
    markOrderReady,
    markOrderPickedUp,
    completeOrder,
    cancelOrder,
    recordPayment,
    refresh: fetchLiveData,

  };
}
