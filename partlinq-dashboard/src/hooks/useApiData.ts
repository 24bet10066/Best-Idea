import { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../services/api';
import type { Technician, Order, PartsShop, UdhaarSummary, TickerEvent, DashboardStats } from '../types';

/**
 * Live data hook — PartLinQ API only. No mock fallback.
 *
 * Design:
 *   loading === true       → show skeleton
 *   error !== null         → show error banner with retry
 *   shops.length === 0     → show empty-state onboarding
 *   isLive === true        → everything wired up, auto-refresh every 30s
 *
 * Why no mock fallback: silent fallback hides production failures. Users
 * can't distinguish "API down" from "nothing registered yet". Broken trust.
 * Real product must fail loudly and tell the user what to do.
 */
export function useApiData(shopId?: string) {
	const [isLive, setIsLive] = useState(false);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState<string | null>(null);

	// Start empty. No seed, no mocks.
	const [technicians, setTechnicians] = useState<Technician[]>([]);
	const [orders, setOrders] = useState<Order[]>([]);
	const [shops, setShops] = useState<PartsShop[]>([]);
	const [udhaarSummaries, setUdhaarSummaries] = useState<UdhaarSummary[]>([]);
	const [tickerEvents, setTickerEvents] = useState<TickerEvent[]>([]);
	const [stats, setStats] = useState<DashboardStats>({
		todaySales: 0,
		totalOutstanding: 0,
		pendingOrders: 0,
		overduePayments: 0,
		activeTechnicians: 0,
		lowStockItems: 0,
	});

	const activeShopId = useRef<string | undefined>(shopId);

	const fetchLiveData = useCallback(async () => {
		try {
			const liveShops = await api.getShops();
			setShops(liveShops);

			if (liveShops.length === 0) {
				// Reachable API, zero shops. Not an error — onboarding state.
				setTechnicians([]);
				setOrders([]);
				setUdhaarSummaries([]);
				setTickerEvents([]);
				setStats({
					todaySales: 0,
					totalOutstanding: 0,
					pendingOrders: 0,
					overduePayments: 0,
					activeTechnicians: 0,
					lowStockItems: 0,
				});
				setIsLive(true);
				setError(null);
				return;
			}

			const sid = activeShopId.current || liveShops[0].id;

			const [liveTechs, liveOrders, outstandingData] = await Promise.all([
				api.getTechnicians(),
				api.getOrdersByShop(sid),
				api.getOutstandingForShop(sid),
			]);

			setTechnicians(liveTechs);
			setOrders(liveOrders);
			setUdhaarSummaries(outstandingData.accounts);

			// Compute stats from live data
			const todayStr = new Date().toISOString().slice(0, 10);
			const todayOrders = liveOrders.filter(o =>
				o.createdAt && o.createdAt.slice(0, 10) === todayStr
			);
			const todaySales = todayOrders.reduce((sum, o) => sum + o.totalAmount, 0);
			const pendingOrders = liveOrders.filter(o =>
				['PLACED', 'CONFIRMED', 'READY'].includes(o.status)
			).length;

			setStats({
				todaySales,
				totalOutstanding: outstandingData.totalOutstanding,
				pendingOrders,
				overduePayments: outstandingData.overdueCount,
				activeTechnicians: liveTechs.filter(t => t.isActive).length,
				lowStockItems: 0, // TODO: wire /inventory/low-stock endpoint
			});

			// Build ticker from real events
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

			outstandingData.accounts.slice(0, 5).forEach((u, idx) => {
				if (u.lastPaymentDate) {
					recentEvents.push({
						id: `evt-pay-${idx}`,
						type: 'payment',
						technicianName: u.technicianName,
						amount: u.totalPaid,
						message: `\u20B9${u.totalPaid.toLocaleString('en-IN')} paid — ${u.riskTag}`,
						timestamp: u.lastPaymentDate,
					});
				}
				if (u.riskTag === 'OVERDUE' || u.riskTag === 'AT_RISK') {
					recentEvents.push({
						id: `evt-alert-${idx}`,
						type: 'alert',
						technicianName: u.technicianName,
						amount: u.currentBalance,
						message: `\u20B9${u.currentBalance.toLocaleString('en-IN')} overdue — ${u.daysSinceLastPayment}d`,
						timestamp: u.lastCreditDate || new Date().toISOString(),
					});
				}
			});

			recentEvents.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
			setTickerEvents(recentEvents);

			setIsLive(true);
			setError(null);
		} catch (err: unknown) {
			const message = err instanceof Error ? err.message : 'API unavailable';
			console.error('PartLinQ API call failed:', message);
			setIsLive(false);
			setError(message);
			// Leave existing state in place if we had one — avoids flashes on transient errors.
		} finally {
			setLoading(false);
		}
	}, []);

	// Initial fetch
	useEffect(() => {
		fetchLiveData();
	}, [fetchLiveData]);

	// Auto-refresh every 30s while live
	useEffect(() => {
		if (!isLive) return;
		const interval = setInterval(fetchLiveData, 30000);
		return () => clearInterval(interval);
	}, [isLive, fetchLiveData]);

	// Action handlers — fail loud if API unreachable
	const confirmOrder = useCallback(async (orderId: string) => {
		const updated = await api.confirmOrder(orderId);
		setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
		return updated;
	}, []);

	const markOrderReady = useCallback(async (orderId: string) => {
		const updated = await api.markReady(orderId);
		setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
		return updated;
	}, []);

	const markOrderPickedUp = useCallback(async (orderId: string) => {
		const updated = await api.markPickedUp(orderId);
		setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
		return updated;
	}, []);

	const completeOrder = useCallback(async (orderId: string) => {
		const updated = await api.completeOrder(orderId);
		setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
		return updated;
	}, []);

	const cancelOrder = useCallback(async (orderId: string) => {
		const updated = await api.cancelOrder(orderId);
		setOrders(prev => prev.map(o => o.id === orderId ? updated : o));
		return updated;
	}, []);

	const recordPayment = useCallback(async (
		technicianId: string, amount: number, paymentMode: string,
		referenceNumber?: string, notes?: string
	) => {
		const sid = activeShopId.current || shops[0]?.id;
		if (!sid) throw new Error('No shop registered. Register a shop first.');

		const result = await api.recordPayment({
			technicianId, shopId: sid, amount, paymentMode,
			referenceNumber, notes, recordedBy: shops[0]?.ownerName || 'Shop Owner',
		});
		const outstandingData = await api.getOutstandingForShop(sid);
		setUdhaarSummaries(outstandingData.accounts);
		return result;
	}, [shops]);

	return {
		technicians,
		orders,
		shops,
		udhaarSummaries,
		stats,
		tickerEvents,

		isLive,
		loading,
		error,

		confirmOrder,
		markOrderReady,
		markOrderPickedUp,
		completeOrder,
		cancelOrder,
		recordPayment,
		refresh: fetchLiveData,
	};
}
