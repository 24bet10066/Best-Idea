// API service layer for PartLinQ backend
// In production (Vercel), set VITE_API_URL=https://your-render-app.onrender.com/api/v1
// Locally, falls back to http://localhost:8080/api/v1
const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${url}`);
  if (!res.ok) {
    const errBody = await res.text().catch(() => '');
    throw new Error(`API ${res.status}: ${errBody || res.statusText}`);
  }
  return res.json();
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const errBody = await res.text().catch(() => '');
    throw new Error(`API ${res.status}: ${errBody || res.statusText}`);
  }
  return res.json();
}

async function patchJson<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${url}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const errBody = await res.text().catch(() => '');
    throw new Error(`API ${res.status}: ${errBody || res.statusText}`);
  }
  return res.json();
}

import type { Technician, Order, PartsShop, SparePart, UdhaarSummary } from '../types';

// Response wrappers matching backend DTOs
interface OutstandingResponse {
  shopId: string;
  totalTechnicians: number;
  totalOutstanding: number;
  overdueCount: number;
  accounts: UdhaarSummary[];
}

interface TechnicianResponse {
  id: string;
  fullName: string;
  phone: string;
  specializations: string[];
  trustScore: number;
  creditLimit: number;
  currentBalance: number;
  avgPaymentDays: number;
  totalTransactions: number;
  registeredAt: string;
  lastActiveAt: string;
  profileImageUrl: string;
  isActive: boolean;
}

interface OrderResponse {
  orderId: string;
  orderNumber: string;
  technicianId: string;
  technicianName: string;
  shopId: string;
  shopName: string;
  status: string;
  totalAmount: number;
  creditUsed: boolean;
  paymentDueDate: string;
  paidAt: string;
  createdAt: string;
  updatedAt: string;
  notes: string;
  items: {
    itemId: string;
    sparePartId: string;
    partName: string;
    partNumber: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
  }[];
}

interface ShopResponse {
  id: string;
  shopName: string;
  ownerName: string;
  phone: string;
  address: string;
  city: string;
  pincode: string;
  gstNumber: string;
  upiId: string;
  isActive: boolean;
  registeredAt: string;
}

// Map backend DTOs to frontend types
function mapTechnician(t: TechnicianResponse): Technician {
  return {
    id: t.id,
    fullName: t.fullName,
    phone: t.phone,
    specializations: t.specializations || [],
    trustScore: t.trustScore,
    creditLimit: t.creditLimit,
    currentBalance: t.currentBalance || 0,
    avgPaymentDays: t.avgPaymentDays,
    totalTransactions: t.totalTransactions,
    registeredAt: t.registeredAt,
    lastActiveAt: t.lastActiveAt,
    profileImageUrl: t.profileImageUrl || '',
    isActive: t.isActive,
  };
}

function mapOrder(o: OrderResponse): Order {
  return {
    id: o.orderId,
    orderNumber: o.orderNumber,
    technicianId: o.technicianId,
    technicianName: o.technicianName,
    shopId: o.shopId,
    shopName: o.shopName,
    status: o.status as Order['status'],
    totalAmount: o.totalAmount,
    creditUsed: o.creditUsed,
    paymentDueDate: o.paymentDueDate,
    paidAt: o.paidAt,
    createdAt: o.createdAt,
    updatedAt: o.updatedAt,
    notes: o.notes || '',
    items: (o.items || []).map(i => ({
      id: i.itemId,
      sparePartId: i.sparePartId,
      partName: i.partName,
      partNumber: i.partNumber,
      quantity: i.quantity,
      unitPrice: i.unitPrice,
      lineTotal: i.lineTotal,
    })),
  };
}

function mapShop(s: ShopResponse): PartsShop {
  return {
    id: s.id,
    shopName: s.shopName,
    ownerName: s.ownerName,
    phone: s.phone,
    address: s.address,
    city: s.city,
    pincode: s.pincode,
    gstNumber: s.gstNumber || '',
    upiId: s.upiId || '',
    isActive: s.isActive,
    registeredAt: s.registeredAt,
  };
}

export const api = {
  // Technicians
  getTechnicians: async (): Promise<Technician[]> => {
    const data = await fetchJson<TechnicianResponse[]>('/technicians');
    return data.map(mapTechnician);
  },
  getTechnician: async (id: string): Promise<Technician> => {
    const data = await fetchJson<TechnicianResponse>(`/technicians/${id}`);
    return mapTechnician(data);
  },

  // Orders
  getOrdersByShop: async (shopId: string): Promise<Order[]> => {
    const data = await fetchJson<OrderResponse[]>(`/orders/shop/${shopId}`);
    return data.map(mapOrder);
  },
  getOrdersByTechnician: async (techId: string): Promise<Order[]> => {
    const data = await fetchJson<OrderResponse[]>(`/orders/technician/${techId}`);
    return data.map(mapOrder);
  },
  confirmOrder: async (id: string): Promise<Order> => {
    const data = await patchJson<OrderResponse>(`/orders/${id}/confirm`);
    return mapOrder(data);
  },
  markReady: async (id: string): Promise<Order> => {
    const data = await patchJson<OrderResponse>(`/orders/${id}/ready`);
    return mapOrder(data);
  },
  markPickedUp: async (id: string): Promise<Order> => {
    const data = await patchJson<OrderResponse>(`/orders/${id}/pickup`);
    return mapOrder(data);
  },
  completeOrder: async (id: string): Promise<Order> => {
    const data = await patchJson<OrderResponse>(`/orders/${id}/complete`);
    return mapOrder(data);
  },
  cancelOrder: async (id: string): Promise<Order> => {
    const data = await patchJson<OrderResponse>(`/orders/${id}/cancel`);
    return mapOrder(data);
  },

  // Shops
  getShops: async (): Promise<PartsShop[]> => {
    const data = await fetchJson<ShopResponse[]>('/shops');
    return data.map(mapShop);
  },

  // Parts
  searchParts: (query: string) => fetchJson<SparePart[]>(`/parts/search?query=${encodeURIComponent(query)}`),

  // Udhaar
  getUdhaarSummary: (techId: string, shopId: string) =>
    fetchJson<UdhaarSummary>(`/udhaar/summary?technicianId=${techId}&shopId=${shopId}`),
  getOutstandingForShop: async (shopId: string): Promise<{ accounts: UdhaarSummary[]; totalOutstanding: number; overdueCount: number }> => {
    const data = await fetchJson<OutstandingResponse>(`/udhaar/shop/${shopId}/outstanding`);
    return { accounts: data.accounts, totalOutstanding: data.totalOutstanding, overdueCount: data.overdueCount };
  },
  getOverdueForShop: (shopId: string) =>
    fetchJson<UdhaarSummary[]>(`/udhaar/shop/${shopId}/overdue`),
  recordPayment: (body: {
    technicianId: string;
    shopId: string;
    amount: number;
    paymentMode: string;
    referenceNumber?: string;
    notes?: string;
    recordedBy: string;
  }) => postJson<UdhaarSummary>('/udhaar/payment', body),

  // Invoices
  getInvoice: (orderId: string) => fetchJson<unknown>(`/invoices/order/${orderId}`),
  getWhatsAppInvoice: (orderId: string) => fetchJson<string>(`/invoices/order/${orderId}/whatsapp`),
};
