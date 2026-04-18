import { useState, useCallback } from 'react';
import { Header } from './components/dashboard/Header';
import { Ticker } from './components/dashboard/Ticker';
import { WorkspaceRouter, type ViewType } from './components/dashboard/WorkspaceRouter';
import { useApiData } from './hooks/useApiData';
import type { TickerEvent } from './types';

export default function App() {
  const data = useApiData();
  const [activeView, setActiveView] = useState<ViewType>('overview');
  const [tickerCollapsed, setTickerCollapsed] = useState(false);

  const handleTickerEvent = useCallback((event: TickerEvent) => {
    switch (event.type) {
      case 'order':
        setActiveView('orders');
        break;
      case 'payment':
      case 'credit':
      case 'alert':
        setActiveView('udhaar');
        break;
      case 'trust':
        setActiveView('technicians');
        break;
    }
  }, []);

  // Loading skeleton — first paint, API not resolved yet
  if (data.loading) {
    return (
      <div className="h-screen flex items-center justify-center bg-zinc-50">
        <div className="flex items-center gap-3 text-zinc-500">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
          <span className="text-sm font-medium">Connecting to PartLinQ…</span>
        </div>
      </div>
    );
  }

  // Hard error — API unreachable. No silent mocks. Tell the user.
  if (data.error && data.shops.length === 0) {
    return (
      <div className="h-screen flex items-center justify-center bg-zinc-50 p-6">
        <div className="max-w-md w-full bg-white border border-red-200 rounded-lg p-6 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <span className="w-2 h-2 rounded-full bg-red-500" />
            <h2 className="text-base font-bold text-zinc-800">Cannot reach PartLinQ API</h2>
          </div>
          <p className="text-sm text-zinc-600 leading-relaxed mb-4">
            {data.error}
          </p>
          <div className="text-xs text-zinc-500 space-y-1.5 bg-zinc-50 rounded p-3 mb-4">
            <p className="font-mono">VITE_API_URL = <span className="text-zinc-700">{import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1'}</span></p>
            <p>• Check that the backend is running</p>
            <p>• Verify CORS_ALLOWED_ORIGINS includes this domain</p>
            <p>• Confirm VITE_API_URL env var is set in the deploy</p>
          </div>
          <button
            onClick={data.refresh}
            className="w-full px-4 py-2 rounded-md bg-zinc-900 text-white text-sm font-medium hover:bg-zinc-800 transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  // Onboarding — API reachable but nothing registered yet
  if (data.isLive && data.shops.length === 0) {
    return (
      <div className="h-screen flex items-center justify-center bg-zinc-50 p-6">
        <div className="max-w-lg w-full bg-white border border-zinc-200 rounded-lg p-8 shadow-sm">
          <div className="flex items-baseline gap-1.5 mb-2">
            <span className="text-2xl font-bold text-zinc-900">Part</span>
            <span className="text-2xl font-bold text-amber-500">LinQ</span>
          </div>
          <h2 className="text-base font-semibold text-zinc-800 mb-1">Welcome. Let's get set up.</h2>
          <p className="text-sm text-zinc-500 mb-5">
            No shops registered yet. Register a parts shop to start tracking orders, udhaar, and trust.
          </p>
          <div className="bg-zinc-50 border border-zinc-200 rounded p-4 text-xs font-mono text-zinc-700 leading-relaxed">
            <p className="text-[10px] uppercase tracking-wider text-zinc-400 mb-2">Quick start</p>
            <p>POST /api/v1/shops</p>
            <p className="text-zinc-500">  with JSON body (see docs)</p>
          </div>
          <p className="text-[11px] text-zinc-400 mt-4">
            See USER-GUIDE.md for a full walkthrough, or Swagger UI at /api/swagger-ui.html
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-zinc-50 overflow-hidden">
      <Header
        shopName={data.shops[0]?.shopName || 'PartLinQ'}
        stats={data.stats}
        isLive={data.isLive}
      />
      {data.error && data.isLive === false && (
        <div className="bg-amber-50 border-b border-amber-200 px-5 py-2 text-xs text-amber-800 flex items-center gap-2">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
          <span className="font-medium">Offline — last data shown.</span>
          <span className="text-amber-600">{data.error}</span>
          <button
            onClick={data.refresh}
            className="ml-auto text-amber-800 hover:text-amber-900 font-semibold underline"
          >
            Retry
          </button>
        </div>
      )}
      <div className="flex-1 flex overflow-hidden">
        <Ticker
          events={data.tickerEvents}
          collapsed={tickerCollapsed}
          onToggle={() => setTickerCollapsed(c => !c)}
          onEventClick={handleTickerEvent}
        />
        <WorkspaceRouter
          activeView={activeView}
          onChangeView={setActiveView}
          technicians={data.technicians}
          orders={data.orders}
          udhaarSummaries={data.udhaarSummaries}
          stats={data.stats}
          onConfirmOrder={data.confirmOrder}
          onMarkReady={data.markOrderReady}
          onMarkPickedUp={data.markOrderPickedUp}
          onCompleteOrder={data.completeOrder}
          onCancelOrder={data.cancelOrder}
          onRecordPayment={data.recordPayment}
          isLive={data.isLive}
        />
      </div>
    </div>
  );
}
