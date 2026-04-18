/**
 * Indian Rupee + relative-time formatters.
 *
 * formatINR — short form: ₹4.2L, ₹47K, ₹950
 * formatINRFull — full form with Indian thousands: ₹4,20,000
 * timeAgo — "5m ago", "3h ago", "2d ago"
 *
 * Centralized so the data layer (mock vs live) and the view layer
 * stay decoupled. No data dependencies — pure functions only.
 */

export function formatINR(amount: number): string {
	if (amount >= 100000) return `\u20B9${(amount / 100000).toFixed(1)}L`;
	if (amount >= 1000) return `\u20B9${(amount / 1000).toFixed(amount >= 10000 ? 0 : 1)}K`;
	return `\u20B9${amount.toLocaleString('en-IN')}`;
}

export function formatINRFull(amount: number): string {
	return `\u20B9${amount.toLocaleString('en-IN')}`;
}

export function timeAgo(dateStr: string): string {
	if (!dateStr) return '—';
	const now = new Date();
	const d = new Date(dateStr);
	if (Number.isNaN(d.getTime())) return '—';
	const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000);
	if (diffMin < 1) return 'just now';
	if (diffMin < 60) return `${diffMin}m ago`;
	const diffHr = Math.floor(diffMin / 60);
	if (diffHr < 24) return `${diffHr}h ago`;
	const diffDay = Math.floor(diffHr / 24);
	return `${diffDay}d ago`;
}
