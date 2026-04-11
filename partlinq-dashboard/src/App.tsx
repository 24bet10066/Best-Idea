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

  return (
    <div className="h-screen flex flex-col bg-zinc-50 overflow-hidden">
      <Header
        shopName={data.shops[0]?.shopName || 'PartLinQ'}
        stats={data.stats}
        isLive={data.isLive}
      />
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
