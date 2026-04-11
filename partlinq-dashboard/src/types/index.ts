// PartLinQ Domain Types — aligned with Spring Boot backend DTOs

export interface Technician {
  id: string;
  fullName: string;
  phone: string;
  specializations: string[];
  trustScore: number;
  creditLimit: number;
  currentBalance: number;
  totalTransactions: number;
  avgPaymentDays: number;
  isActive: boolean;
  registeredAt: string;
  lastActiveAt: string;
  profileImageUrl: string;
}

export interface PartsShop {
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

export interface SparePart {
  id: string;
  partNumber: string;
  name: string;
  description?: string;
  category: string;
  applianceType: string;
  brand: string;
  modelCompatibility?: string;
  mrp: number;
  isOem: boolean;
  createdAt: string;
}

export interface OrderItem {
  id: string;
  sparePartId: string;
  partName: string;
  partNumber: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  id: string;
  orderNumber: string;
  technicianId: string;
  technicianName: string;
  shopId: string;
  shopName: string;
  status: OrderStatus;
  totalAmount: number;
  creditUsed: boolean;
  paymentDueDate?: string;
  paidAt?: string;
  createdAt: string;
  updatedAt?: string;
  notes?: string;
  items: OrderItem[];
}

export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'READY' | 'PICKED_UP' | 'COMPLETED' | 'CANCELLED';

export interface UdhaarSummary {
  technicianId: string;
  technicianName: string;
  technicianPhone: string;
  shopId: string;
  shopName: string;
  totalCredit: number;
  totalPaid: number;
  currentBalance: number;
  lastCreditDate?: string;
  lastPaymentDate?: string;
  daysSinceLastPayment: number;
  totalUnpaidOrders: number;
  riskTag: RiskTag;
  recentEntries: LedgerEntry[];
}

export type RiskTag = 'CLEAR' | 'NORMAL' | 'AT_RISK' | 'OVERDUE' | 'NEW_CREDIT';

export interface LedgerEntry {
  id: string;
  entryType: 'CREDIT' | 'PAYMENT' | 'ADJUSTMENT';
  amount: number;
  balanceAfter: number;
  paymentMode?: string;
  referenceNumber?: string;
  notes?: string;
  recordedBy: string;
  createdAt: string;
  orderId?: string;
  orderNumber?: string;
}

export interface TickerEvent {
  id: string;
  type: 'order' | 'payment' | 'credit' | 'alert' | 'trust';
  technicianName: string;
  amount?: number;
  status?: string;
  message: string;
  timestamp: string;
}

export interface DashboardStats {
  todaySales: number;
  totalOutstanding: number;
  pendingOrders: number;
  overduePayments: number;
  activeTechnicians: number;
  lowStockItems: number;
}
