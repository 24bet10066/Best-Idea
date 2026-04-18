# PartLinQ — User Guide

For parts shop owners and technicians. Read-once, use-daily.

## What PartLinQ does

Tracks **udhaar** (credit) between parts shops and appliance-repair technicians. Replaces notebook-and-phone-call with a real system.

Five workflows:

1. **Orders** — technician books parts from a shop
2. **Udhaar** — track who owes what, who paid what
3. **Payments** — record cash/UPI/bank payments against balances
4. **Trust** — auto-computed technician reliability score
5. **Reminders** — automated daily/weekly nudges over WhatsApp/SMS

No hardcoded data. No demo fake. Everything you see is data you entered.

---

## Before you start

- **[FACT]** Backend URL: `https://<your-render-app>.onrender.com/api`
- **[FACT]** Dashboard URL: set by your deploy (`VITE_API_URL` env var points to backend)
- **[FACT]** API docs: `<backend>/swagger-ui.html`
- **[ASSUMPTION]** You have admin access to POST to `/api/v1/shops` for first-time registration

First-time setup is POST-based for now. Dashboard forms are on the roadmap. Use Swagger UI or `curl`.

---

## Workflow 1 — Register a shop

A shop must exist before anything else. One shop per parts business.

```bash
curl -X POST <backend>/api/v1/shops \
  -H 'Content-Type: application/json' \
  -d '{
    "shopName": "Raj Spare Parts",
    "ownerName": "Raj Kumar",
    "phone": "9000000001",
    "email": "raj@example.com",
    "address": "23, Hazratganj Market",
    "city": "Lucknow",
    "pincode": "226001",
    "gstNumber": "09AABCR1234A1Z5",
    "upiId": "rajspare@upi"
  }'
```

Validation rules:

- `phone` must be exactly **10 digits**
- `pincode` must be exactly **6 digits**
- `email` must be valid
- `gstNumber` is optional

Response returns the shop `id` (UUID). Save it. You'll need it.

---

## Workflow 2 — Register a technician

Technician profiles are shared across shops — register once, any shop can extend credit.

```bash
curl -X POST <backend>/api/v1/technicians \
  -H 'Content-Type: application/json' \
  -d '{
    "fullName": "Rajesh Sharma",
    "phone": "9000000002",
    "email": "rajesh@example.com",
    "city": "Lucknow",
    "pincode": "226001",
    "specializations": ["AC", "REFRIGERATOR"],
    "creditLimit": 25000
  }'
```

Validation rules:

- `fullName` 2–255 chars
- `phone` exactly 10 digits
- `creditLimit` minimum **₹1,000**
- `specializations` at least one of: `AC`, `REFRIGERATOR`, `WASHING_MACHINE`, `MICROWAVE`, `MIXER_GRINDER`, `GEYSER`

Credit limit is the **starting cap**. Trust score raises it over time automatically.

---

## Workflow 3 — Add parts to catalog

```bash
curl -X POST <backend>/api/v1/parts \
  -H 'Content-Type: application/json' \
  -d '{
    "partNumber": "CMP-VOL-15T",
    "name": "Compressor 1.5T Voltas",
    "description": "Rotary compressor, R32 refrigerant",
    "category": "COMPRESSOR",
    "applianceType": "AC",
    "brand": "Voltas",
    "modelCompatibility": "1.5T split models 2019+",
    "mrp": 4200.00,
    "isOem": true
  }'
```

Then stock it into a shop's inventory:

```bash
curl -X POST <backend>/api/v1/shops/<shopId>/inventory \
  -H 'Content-Type: application/json' \
  -d '{
    "sparePartId": "<partId>",
    "quantity": 10,
    "sellingPrice": 4000.00,
    "reorderLevel": 2
  }'
```

---

## Workflow 4 — Place an order

Technician visits shop, books parts. Shop creates the order.

```bash
curl -X POST <backend>/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "technicianId": "<techId>",
    "shopId": "<shopId>",
    "items": [
      { "sparePartId": "<partId>", "quantity": 1, "unitPrice": 4000 }
    ],
    "useCredit": true,
    "notes": "Customer: Sharma ji, Indira Nagar"
  }'
```

If `useCredit: true`, the order goes into **udhaar** — technician owes the shop.

Order status flows through: `PLACED → CONFIRMED → READY → PICKED_UP → COMPLETED`

State transitions are explicit API calls (dashboard buttons handle these):

- `PATCH /api/v1/orders/{id}/confirm`
- `PATCH /api/v1/orders/{id}/ready`
- `PATCH /api/v1/orders/{id}/pickup`
- `PATCH /api/v1/orders/{id}/complete`
- `PATCH /api/v1/orders/{id}/cancel`

---

## Workflow 5 — Record a payment

Technician pays. Cash, UPI, bank transfer, or cheque. Enter it in the dashboard's **Udhaar** tab or via API:

```bash
curl -X POST <backend>/api/v1/udhaar/payment \
  -H 'Content-Type: application/json' \
  -d '{
    "technicianId": "<techId>",
    "shopId": "<shopId>",
    "amount": 5000.00,
    "paymentMode": "UPI",
    "referenceNumber": "UPI-2025-XYZ",
    "recordedBy": "Raj Kumar"
  }'
```

What happens:

- Outstanding balance drops by the payment amount
- **Trust score moves up** based on payment ratio:
  - Paid ≥ 80% of outstanding → **+3 points**
  - Paid ≥ 50% → **+2 points**
  - Any positive payment → **+1 point**
- Ledger entry stored with timestamp and reference

---

## Workflow 6 — Read udhaar status

**Dashboard → Udhaar tab.** Shows every technician with an outstanding balance for your shop.

Each row shows:

- **Name and phone**
- **Risk tag** (colour-coded):
  - **CLEAR** — no balance
  - **NORMAL** — balance, paid recently
  - **NEW_CREDIT** — balance, very recent first purchase
  - **AT_RISK** — balance, **15+ days** since last payment
  - **OVERDUE** — balance, **30+ days** since last payment
- **Total credit taken**, **total paid**, **outstanding**
- **Days since last payment**
- **Unpaid orders count**

API form:

```bash
curl '<backend>/api/v1/udhaar/shop/<shopId>/outstanding'
curl '<backend>/api/v1/udhaar/shop/<shopId>/overdue'
```

---

## Workflow 7 — Automatic reminders

The scheduler runs **without any admin action**.

- **Daily 10:00 AM IST** — checks all technicians with balance > ₹0 who haven't paid in **15+ days**. Queues a polite WhatsApp/SMS reminder. Same person won't get spammed — **minimum 7 days** between reminders.
- **Every Monday 8:00 AM IST** — sends shop owners a weekly roll-up: total outstanding, who's overdue, who's at risk.

**[INFERENCE]** This is the highest-leverage feature. Most parts-shop owners lose money because they forget to follow up. The scheduler never forgets.

**[VALIDATE]** Confirm your WhatsApp/SMS provider integration is wired in `NotificationService` before relying on delivery.

---

## Workflow 8 — Trust score

Auto-computed. No manual rating. Each technician has a **0–100 score**.

Inputs:

- Payment ratio (paid ÷ credit taken) — weighted highest
- Payment speed (avg days to pay) — lower is better
- Volume (total transactions) — more data = more confidence
- Endorsements from other shops — small bonus
- Customer feedback — small bonus/penalty

Dashboard visual: coloured bar.

- **≥ 80** emerald — raise credit limit, offer priority
- **60–79** green — standard trust
- **40–59** amber — caution, watch patterns
- **< 40** red — consider cash-only

API:

```bash
curl '<backend>/api/v1/trust/<technicianId>/score'
curl '<backend>/api/v1/trust/leaderboard'
```

---

## How to read the dashboard

Top bar shows **LIVE** (green dot) or **DEMO** (grey dot). If you see DEMO, the API is unreachable. Fix the backend before trusting any number.

**Three pulse metrics you check every morning:**

- **Sales Today** — total order value created today
- **Outstanding** — total udhaar across all technicians
- **Overdue** — count of technicians past 30-day mark

**One rule:** if Overdue > 0, open the Udhaar tab. Call them. Don't wait for the scheduler.

---

## What to do when something breaks

- **Dashboard shows DEMO and won't reconnect.** Check that backend is running (`<backend>/actuator/health` should return `UP`).
- **Validation error (HTTP 400).** Response body lists exactly which fields are wrong. Fix the payload and retry.
- **Order stuck at PLACED.** Only the shop that received the order can confirm it. Open the Orders tab, tap Confirm.
- **Payment doesn't reduce balance.** Check the `technicianId` and `shopId` in the payment match the order. Wrong pair = orphaned entry.
- **Trust score didn't move after payment.** Scores recompute on the next scheduler run or when you call `POST /api/v1/trust/recalculate`.

---

## Money notation

All amounts in Indian Rupees. Short form on dashboard:

- **₹5,000** shown as `₹5K`
- **₹47,250** shown as `₹47K`
- **₹1,82,400** shown as `₹1.8L` (1 Lakh = 1,00,000)
- **₹1,25,00,000** shown as `₹1.25Cr` (1 Crore = 1,00,00,000)

Dialogs and reports use full form: `₹1,82,400`.

---

## What this system will **not** do

Straight talk:

- **Will not** take payments on your behalf. It records them. Money still moves via UPI/cash.
- **Will not** call customers for you. It queues reminders. You connect your WhatsApp Business or SMS gateway.
- **Will not** stop a technician from defaulting. Trust score + overdue alerts help you **predict** risk. Collection is your job.
- **Will not** replace GST invoicing. It generates order-level invoices; run them through your accountant.

Trust is built on what this system does well, not on what it pretends to do.
