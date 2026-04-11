import { useMemo } from 'react';
import type { Technician, Order, PartsShop, UdhaarSummary, TickerEvent, DashboardStats, OrderStatus, RiskTag } from '../types';

const TECHNICIANS: Technician[] = [
  { id: 't1', fullName: 'Rajesh Sharma', phone: '+91-9000000001', specializations: ['AC'], trustScore: 75.5, creditLimit: 25000, currentBalance: 4200, totalTransactions: 42, avgPaymentDays: 7, isActive: true, registeredAt: '2024-03-15T10:00:00', lastActiveAt: '2026-04-10T14:30:00', profileImageUrl: '' },
  { id: 't2', fullName: 'Amit Verma', phone: '+91-9000000002', specializations: ['AC', 'REFRIGERATOR'], trustScore: 45.2, creditLimit: 12000, currentBalance: 8500, totalTransactions: 18, avgPaymentDays: 21, isActive: true, registeredAt: '2024-06-20T10:00:00', lastActiveAt: '2026-04-09T11:00:00', profileImageUrl: '' },
  { id: 't3', fullName: 'Vikram Singh', phone: '+91-9000000003', specializations: ['WASHING_MACHINE'], trustScore: 82.0, creditLimit: 30000, currentBalance: 1200, totalTransactions: 67, avgPaymentDays: 5, isActive: true, registeredAt: '2023-11-01T10:00:00', lastActiveAt: '2026-04-10T16:00:00', profileImageUrl: '' },
  { id: 't4', fullName: 'Deepak Patel', phone: '+91-9000000004', specializations: ['AC', 'MICROWAVE'], trustScore: 38.5, creditLimit: 8000, currentBalance: 6800, totalTransactions: 11, avgPaymentDays: 32, isActive: false, registeredAt: '2025-01-10T10:00:00', lastActiveAt: '2026-04-08T09:00:00', profileImageUrl: '' },
  { id: 't5', fullName: 'Sanjay Gupta', phone: '+91-9000000005', specializations: ['REFRIGERATOR', 'AC'], trustScore: 91.3, creditLimit: 50000, currentBalance: 0, totalTransactions: 103, avgPaymentDays: 3, isActive: true, registeredAt: '2023-05-15T10:00:00', lastActiveAt: '2026-04-10T17:00:00', profileImageUrl: '' },
  { id: 't6', fullName: 'Manoj Tiwari', phone: '+91-9000000006', specializations: ['AC'], trustScore: 62.8, creditLimit: 18000, currentBalance: 3500, totalTransactions: 29, avgPaymentDays: 12, isActive: true, registeredAt: '2024-08-05T10:00:00', lastActiveAt: '2026-04-10T10:00:00', profileImageUrl: '' },
  { id: 't7', fullName: 'Suresh Yadav', phone: '+91-9000000007', specializations: ['WASHING_MACHINE', 'MICROWAVE'], trustScore: 55.0, creditLimit: 15000, currentBalance: 7200, totalTransactions: 22, avgPaymentDays: 15, isActive: true, registeredAt: '2024-04-12T10:00:00', lastActiveAt: '2026-04-09T16:00:00', profileImageUrl: '' },
  { id: 't8', fullName: 'Pradeep Kumar', phone: '+91-9000000008', specializations: ['REFRIGERATOR'], trustScore: 71.4, creditLimit: 22000, currentBalance: 2800, totalTransactions: 38, avgPaymentDays: 8, isActive: true, registeredAt: '2024-01-20T10:00:00', lastActiveAt: '2026-04-10T13:00:00', profileImageUrl: '' },
  { id: 't9', fullName: 'Rahul Mishra', phone: '+91-9000000009', specializations: ['AC', 'REFRIGERATOR', 'WASHING_MACHINE'], trustScore: 88.7, creditLimit: 45000, currentBalance: 0, totalTransactions: 89, avgPaymentDays: 4, isActive: true, registeredAt: '2023-07-01T10:00:00', lastActiveAt: '2026-04-10T15:00:00', profileImageUrl: '' },
  { id: 't10', fullName: 'Arun Pandey', phone: '+91-9000000010', specializations: ['MICROWAVE'], trustScore: 33.2, creditLimit: 5000, currentBalance: 4800, totalTransactions: 7, avgPaymentDays: 45, isActive: false, registeredAt: '2025-09-01T10:00:00', lastActiveAt: '2026-04-05T11:00:00', profileImageUrl: '' },
  { id: 't11', fullName: 'Naveen Dubey', phone: '+91-9000000011', specializations: ['AC', 'WASHING_MACHINE'], trustScore: 67.9, creditLimit: 20000, currentBalance: 5100, totalTransactions: 34, avgPaymentDays: 10, isActive: true, registeredAt: '2024-02-14T10:00:00', lastActiveAt: '2026-04-10T12:00:00', profileImageUrl: '' },
  { id: 't12', fullName: 'Ravi Chauhan', phone: '+91-9000000012', specializations: ['REFRIGERATOR', 'MICROWAVE'], trustScore: 58.3, creditLimit: 14000, currentBalance: 9200, totalTransactions: 19, avgPaymentDays: 18, isActive: true, registeredAt: '2024-10-01T10:00:00', lastActiveAt: '2026-04-09T14:00:00', profileImageUrl: '' },
  { id: 't13', fullName: 'Karan Saxena', phone: '+91-9000000013', specializations: ['AC'], trustScore: 79.1, creditLimit: 28000, currentBalance: 1500, totalTransactions: 51, avgPaymentDays: 6, isActive: true, registeredAt: '2023-12-01T10:00:00', lastActiveAt: '2026-04-10T11:00:00', profileImageUrl: '' },
  { id: 't14', fullName: 'Ajay Srivastava', phone: '+91-9000000014', specializations: ['WASHING_MACHINE'], trustScore: 42.6, creditLimit: 10000, currentBalance: 8000, totalTransactions: 13, avgPaymentDays: 25, isActive: false, registeredAt: '2025-04-01T10:00:00', lastActiveAt: '2026-04-07T09:00:00', profileImageUrl: '' },
  { id: 't15', fullName: 'Mohit Rastogi', phone: '+91-9000000015', specializations: ['AC', 'REFRIGERATOR'], trustScore: 85.4, creditLimit: 40000, currentBalance: 0, totalTransactions: 76, avgPaymentDays: 4, isActive: true, registeredAt: '2023-09-15T10:00:00', lastActiveAt: '2026-04-10T16:30:00', profileImageUrl: '' },
];

const SHOPS: PartsShop[] = [
  { id: 's1', shopName: 'Raj Spare Parts', ownerName: 'Raj Kumar', phone: '+91-8000000001', address: '23, Hazratganj Market', city: 'Lucknow', pincode: '226001', gstNumber: '09AABCR1234A1Z5', upiId: 'rajspare@upi', isActive: true, registeredAt: '2023-01-01T10:00:00' },
  { id: 's2', shopName: 'Krishna Electronics & Parts', ownerName: 'Krishna Agarwal', phone: '+91-8000000002', address: '45, Aminabad Road', city: 'Lucknow', pincode: '226002', gstNumber: '09AABCK5678B2Z3', upiId: 'krishnaparts@upi', isActive: true, registeredAt: '2023-06-15T10:00:00' },
  { id: 's3', shopName: 'Sharma Appliance Center', ownerName: 'Dinesh Sharma', phone: '+91-8000000003', address: '12, Civil Lines', city: 'Kanpur', pincode: '208001', gstNumber: '09AABCS9012C3Z1', upiId: 'sharmaappliance@upi', isActive: true, registeredAt: '2024-01-10T10:00:00' },
];

const STATUSES: OrderStatus[] = ['PLACED', 'CONFIRMED', 'READY', 'PICKED_UP', 'COMPLETED', 'CANCELLED'];

function makeOrders(): Order[] {
  const items = [
    { name: 'Compressor 1.5T Voltas', pn: 'CMP-VOL-15T', price: 4200 },
    { name: 'Fan Motor Indoor Daikin', pn: 'FM-DAI-IND', price: 1800 },
    { name: 'PCB Board Samsung WM', pn: 'PCB-SAM-WM7', price: 3500 },
    { name: 'Thermostat Whirlpool Fridge', pn: 'TH-WP-FR220', price: 850 },
    { name: 'Capacitor 35MF AC', pn: 'CAP-35MF-AC', price: 320 },
    { name: 'Gas Charging Pipe Set', pn: 'GAS-PIPE-SET', price: 450 },
    { name: 'Door Gasket LG Fridge', pn: 'DG-LG-260L', price: 1200 },
    { name: 'Drain Pump IFB WM', pn: 'DP-IFB-WM6', price: 1650 },
    { name: 'Remote PCB Carrier AC', pn: 'RPCB-CAR-15', price: 950 },
    { name: 'Condenser Coil Blue Star', pn: 'CC-BS-1T', price: 5800 },
  ];

  const orders: Order[] = [];
  const now = new Date('2026-04-10T17:00:00');
  for (let i = 0; i < 25; i++) {
    const tech = TECHNICIANS[i % TECHNICIANS.length];
    const shop = SHOPS[i % SHOPS.length];
    const status = STATUSES[i < 3 ? 0 : i < 6 ? 1 : i < 9 ? 2 : i < 12 ? 3 : i < 22 ? 4 : 5];
    const hoursAgo = i * 4 + Math.floor(Math.random() * 3);
    const createdAt = new Date(now.getTime() - hoursAgo * 3600000).toISOString();
    const orderItems = [1, 2].slice(0, (i % 3) + 1).map((_, j) => {
      const item = items[(i + j) % items.length];
      const qty = (j + 1);
      return { id: `oi-${i}-${j}`, sparePartId: `sp-${(i + j) % items.length}`, partName: item.name, partNumber: item.pn, quantity: qty, unitPrice: item.price, lineTotal: qty * item.price };
    });
    const total = orderItems.reduce((s, x) => s + x.lineTotal, 0);
    orders.push({
      id: `ord-${i + 1}`,
      orderNumber: `ORD-2026-${String(i + 1).padStart(4, '0')}`,
      technicianId: tech.id,
      technicianName: tech.fullName,
      shopId: shop.id,
      shopName: shop.shopName,
      status,
      totalAmount: total,
      creditUsed: i % 3 !== 0,
      createdAt,
      items: orderItems,
    });
  }
  return orders;
}

function makeUdhaarSummaries(): UdhaarSummary[] {
  const riskTags: RiskTag[] = ['CLEAR', 'NORMAL', 'AT_RISK', 'OVERDUE', 'NEW_CREDIT'];
  return TECHNICIANS.map((t, i) => {
    const totalCredit = (i + 1) * 3200 + (i % 3) * 1500;
    const totalPaid = Math.floor(totalCredit * (0.3 + (t.trustScore / 200)));
    const balance = totalCredit - totalPaid;
    const risk: RiskTag = balance === 0 ? 'CLEAR' : t.trustScore > 75 ? 'NORMAL' : t.trustScore > 50 ? 'AT_RISK' : t.avgPaymentDays > 30 ? 'OVERDUE' : riskTags[i % riskTags.length];
    return {
      technicianId: t.id,
      technicianName: t.fullName,
      technicianPhone: t.phone,
      shopId: 's1',
      shopName: 'Raj Spare Parts',
      totalCredit,
      totalPaid,
      currentBalance: balance,
      lastCreditDate: '2026-04-08T10:00:00',
      lastPaymentDate: balance === 0 ? '2026-04-09T14:00:00' : i > 10 ? '2026-03-20T10:00:00' : '2026-04-05T10:00:00',
      daysSinceLastPayment: balance === 0 ? 1 : i > 10 ? 21 : 5,
      totalUnpaidOrders: balance === 0 ? 0 : Math.ceil(balance / 3000),
      riskTag: risk,
      recentEntries: [],
    };
  });
}

function makeTickerEvents(orders: Order[]): TickerEvent[] {
  const events: TickerEvent[] = [];
  const now = new Date('2026-04-10T17:00:00');

  // Order events
  orders.slice(0, 5).forEach((o, i) => {
    events.push({ id: `te-ord-${i}`, type: 'order', technicianName: o.technicianName, amount: o.totalAmount, status: o.status, message: `Order ${o.orderNumber} placed`, timestamp: new Date(now.getTime() - i * 15 * 60000).toISOString() });
  });

  // Payment events
  TECHNICIANS.slice(0, 4).forEach((t, i) => {
    events.push({ id: `te-pay-${i}`, type: 'payment', technicianName: t.fullName, amount: [5000, 12000, 3500, 8200][i], message: `Payment received via ${['UPI', 'Cash', 'UPI', 'Bank Transfer'][i]}`, timestamp: new Date(now.getTime() - (i * 25 + 10) * 60000).toISOString() });
  });

  // Credit events
  TECHNICIANS.slice(4, 7).forEach((t, i) => {
    events.push({ id: `te-cred-${i}`, type: 'credit', technicianName: t.fullName, amount: [4200, 7800, 2300][i], message: `Credit extended for parts`, timestamp: new Date(now.getTime() - (i * 30 + 20) * 60000).toISOString() });
  });

  // Alert events
  [TECHNICIANS[3], TECHNICIANS[9], TECHNICIANS[13]].forEach((t, i) => {
    events.push({ id: `te-alert-${i}`, type: 'alert', technicianName: t.fullName, amount: [8500, 15200, 6700][i], message: `Payment overdue by ${[15, 25, 18][i]} days`, timestamp: new Date(now.getTime() - (i * 45 + 5) * 60000).toISOString() });
  });

  // Trust milestone events
  [TECHNICIANS[4], TECHNICIANS[8]].forEach((t, i) => {
    events.push({ id: `te-trust-${i}`, type: 'trust', technicianName: t.fullName, message: `Trust score crossed ${[90, 85][i]} — credit limit increased`, timestamp: new Date(now.getTime() - (i * 60 + 30) * 60000).toISOString() });
  });

  return events.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
}

export function useMockData() {
  const orders = useMemo(() => makeOrders(), []);
  const udhaarSummaries = useMemo(() => makeUdhaarSummaries(), []);
  const tickerEvents = useMemo(() => makeTickerEvents(orders), [orders]);

  const stats: DashboardStats = useMemo(() => ({
    todaySales: 47250,
    totalOutstanding: 182400,
    pendingOrders: orders.filter(o => ['PLACED', 'CONFIRMED', 'READY'].includes(o.status)).length,
    overduePayments: udhaarSummaries.filter(u => u.riskTag === 'OVERDUE').length,
    activeTechnicians: 12,
    lowStockItems: 5,
  }), [orders, udhaarSummaries]);

  return { technicians: TECHNICIANS, shops: SHOPS, orders, udhaarSummaries, tickerEvents, stats };
}

export function formatINR(amount: number): string {
  if (amount >= 100000) return `\u20B9${(amount / 100000).toFixed(1)}L`;
  if (amount >= 1000) return `\u20B9${(amount / 1000).toFixed(amount >= 10000 ? 0 : 1)}K`;
  return `\u20B9${amount.toLocaleString('en-IN')}`;
}

export function formatINRFull(amount: number): string {
  return `\u20B9${amount.toLocaleString('en-IN')}`;
}

export function timeAgo(dateStr: string): string {
  const now = new Date();
  const d = new Date(dateStr);
  const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000);
  if (diffMin < 1) return 'just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  const diffDay = Math.floor(diffHr / 24);
  return `${diffDay}d ago`;
}
