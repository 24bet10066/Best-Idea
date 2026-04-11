import { ScrollArea } from '@/components/ui/scroll-area';
import type { TickerEvent } from '../../types';
import { formatINRFull, timeAgo } from '../../hooks/useMockData';

interface TickerProps {
  events: TickerEvent[];
  collapsed: boolean;
  onToggle: () => void;
  onEventClick: (event: TickerEvent) => void;
}

const EVENT_CONFIG: Record<TickerEvent['type'], { icon: string; color: string; bg: string }> = {
  order: { icon: '📦', color: 'text-blue-400', bg: 'bg-blue-500/10' },
  payment: { icon: '💰', color: 'text-emerald-400', bg: 'bg-emerald-500/10' },
  credit: { icon: '📋', color: 'text-amber-400', bg: 'bg-amber-500/10' },
  alert: { icon: '⚠️', color: 'text-red-400', bg: 'bg-red-500/10' },
  trust: { icon: '⭐', color: 'text-purple-400', bg: 'bg-purple-500/10' },
};

export function Ticker({ events, collapsed, onToggle, onEventClick }: TickerProps) {
  if (collapsed) {
    return (
      <div className="w-12 bg-zinc-950 border-r border-zinc-800 flex flex-col items-center py-3 gap-3 shrink-0 transition-all duration-300">
        <button
          onClick={onToggle}
          className="w-8 h-8 rounded-md bg-zinc-800 hover:bg-zinc-700 flex items-center justify-center text-zinc-400 hover:text-white transition-colors"
          title="Expand ticker"
        >
          <ChevronRight />
        </button>
        <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
        <span className="text-[10px] text-zinc-600 font-mono -rotate-90 whitespace-nowrap mt-4">{events.length} events</span>
        {/* Mini event type indicators */}
        <div className="flex flex-col gap-1.5 mt-2">
          {['order', 'payment', 'credit', 'alert', 'trust'].map(type => {
            const count = events.filter(e => e.type === type).length;
            if (count === 0) return null;
            const cfg = EVENT_CONFIG[type as TickerEvent['type']];
            return (
              <div key={type} className={`w-6 h-6 rounded flex items-center justify-center text-[10px] ${cfg.bg} ${cfg.color}`}>
                {count}
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <div className="w-80 bg-zinc-950 border-r border-zinc-800 flex flex-col shrink-0 transition-all duration-300">
      {/* Ticker Header */}
      <div className="h-11 flex items-center justify-between px-3 border-b border-zinc-800">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span className="text-xs font-semibold text-zinc-300 uppercase tracking-wider">Live Feed</span>
          <span className="text-[10px] text-zinc-600 font-mono">{events.length}</span>
        </div>
        <button
          onClick={onToggle}
          className="w-7 h-7 rounded-md hover:bg-zinc-800 flex items-center justify-center text-zinc-500 hover:text-white transition-colors"
          title="Collapse ticker"
        >
          <ChevronLeft />
        </button>
      </div>

      {/* Events Feed */}
      <ScrollArea className="flex-1">
        <div className="p-2 space-y-1">
          {events.map(event => (
            <TickerItem key={event.id} event={event} onClick={() => onEventClick(event)} />
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}

function TickerItem({ event, onClick }: { event: TickerEvent; onClick: () => void }) {
  const cfg = EVENT_CONFIG[event.type];

  return (
    <button
      onClick={onClick}
      className={`w-full text-left px-3 py-2.5 rounded-lg hover:bg-zinc-900 transition-colors group ${cfg.bg} border border-transparent hover:border-zinc-800`}
    >
      <div className="flex items-start gap-2.5">
        <span className="text-base mt-0.5 shrink-0">{cfg.icon}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <span className={`text-xs font-semibold truncate ${cfg.color}`}>{event.technicianName}</span>
            <span className="text-[10px] text-zinc-600 font-mono shrink-0">{timeAgo(event.timestamp)}</span>
          </div>
          <p className="text-[11px] text-zinc-500 mt-0.5 leading-snug">{event.message}</p>
          {event.amount != null && (
            <span className="text-xs font-mono font-bold text-zinc-300 mt-1 inline-block tabular-nums">
              {formatINRFull(event.amount)}
            </span>
          )}
        </div>
      </div>
    </button>
  );
}

function ChevronLeft() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M15 18l-6-6 6-6" />
    </svg>
  );
}

function ChevronRight() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 18l6-6-6-6" />
    </svg>
  );
}
