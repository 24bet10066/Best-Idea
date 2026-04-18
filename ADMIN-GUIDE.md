# PartLinQ — Admin & Operator Guide

For the person deploying, running, and maintaining PartLinQ. Technical but plain.

## Stack

- **Backend** — Java 21 + Spring Boot 3.5.13, Virtual Threads, JPA, PostgreSQL (prod) / H2 (dev)
- **Frontend** — React 18 + Vite + TypeScript + Tailwind + shadcn/ui
- **Deploy** — Render (backend), Vercel or Render static (frontend)
- **Cache** — Caffeine (JVM-local)
- **Scheduler** — Spring `@Scheduled` cron jobs

---

## Architecture

```
[Dashboard (React)] ──HTTPS──> [Spring Boot API] ──JDBC──> [Postgres]
                                     │
                                     ├─ @Scheduled daily 10 AM IST → reminders
                                     ├─ @Scheduled Mon 8 AM IST → weekly summary
                                     └─ NotificationService → WhatsApp/SMS gateway
```

- **[FACT]** All controllers namespaced at `/api/v1/*`
- **[FACT]** Context path `/api` set via `server.servlet.context-path`
- **[FACT]** Virtual Threads enabled — one thread per request, cheap under load

---

## Environment variables (prod)

| Var | Required | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | Set to `prod` |
| `PORT` | injected by Render | HTTP port |
| `DATABASE_URL` | yes | Postgres connection string (`postgresql://...` or `jdbc:postgresql://...`) |
| `DATABASE_USER` | yes | DB username |
| `DATABASE_PASSWORD` | yes | DB password |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated list incl. Vercel/dashboard origin |
| `DDL_AUTO` | first deploy only | Set to `update` on first deploy. **Remove after schema stabilizes.** Default is `validate`. |

**[FACT]** `DataSourceConfig.java` parses both `postgresql://user:pass@host/db` and `jdbc:postgresql://host/db` formats.

**[FACT]** `DataSeeder.java` is `@Profile("dev")` — never runs in prod. Prod starts empty.

---

## First-time prod deploy

1. Provision Postgres (Render Managed Postgres is fine for start).
2. Deploy backend to Render. Set all env vars above. **Set `DDL_AUTO=update` on first deploy only.**
3. Hit `<backend>/api/actuator/health`. Should return `{"status":"UP"}`.
4. Hit `<backend>/api/swagger-ui.html`. API docs.
5. Deploy frontend. Set `VITE_API_URL=<backend>/api/v1`.
6. Dashboard loads → shows onboarding screen (no shops yet).
7. `curl` POST your first shop + technician. Dashboard flips to live.
8. **Remove `DDL_AUTO`** env var. Schema is now validated on boot — any entity drift fails fast.

---

## Schema changes after stable

Do **not** let Hibernate auto-alter tables in prod. Ever. Use Flyway or Liquibase:

1. Add Flyway dependency in `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-core</artifactId>
   </dependency>
   ```
2. Create `src/main/resources/db/migration/V1__baseline.sql` by dumping current schema.
3. Subsequent changes go as `V2__add_reminders_table.sql`, etc.
4. Keep `DDL_AUTO=validate`. Flyway handles migrations. Hibernate only validates.

---

## Monitoring

- **Health** — `GET /api/actuator/health` (show-details: always in dev, restricted in prod by default)
- **Metrics** — `GET /api/actuator/metrics`
- **Info** — `GET /api/actuator/info`

**[ASSUMPTION]** Add Sentry/Honeycomb/NewRelic for real error tracking — currently only console logging.

**[VALIDATE]** Before production load, run `k6` or `JMeter` at expected peak (estimate: **500 req/min** for 50 active shops).

---

## The scheduler — what runs when

| Cron | UTC | IST | Purpose |
|---|---|---|---|
| `0 30 4 * * *` | 04:30 daily | 10:00 AM | Payment reminder check |
| `0 30 2 * * MON` | 02:30 Mon | 08:00 AM Mon | Weekly udhaar summary |

Logic (see `PaymentReminderScheduler.java`):

- Iterates all shops
- For each shop, pulls `udhaarService.getOverdueForShop(shopId)`
- Any technician with **balance > ₹0** AND **daysSinceLastPayment > 15** → queue `NotificationService.queuePaymentReminder`
- Monday run sends shop owner a summary of all outstanding

**[INFERENCE]** `MIN_DAYS_BETWEEN_REMINDERS = 7` — hard-coded constant. Tune in code if needed. Changing requires redeploy.

**[VALIDATE]** Wire `NotificationService` to a real WhatsApp Business API / MSG91 / Twilio adapter before going live. Currently the notification queue is a stub in dev.

---

## Backup and disaster recovery

**[ASSUMPTION]** Render Managed Postgres does daily snapshots. Verify your plan includes them.

- **Daily** — automated snapshot (Render, AWS RDS, or equivalent)
- **Weekly** — `pg_dump` offsite copy to S3 or Google Cloud Storage
- **Monthly** — restore drill on a staging instance. Confirm it actually boots.

**[VALIDATE]** Untested backups are not backups.

---

## Security

- **[FACT]** Spring Security is on the classpath. Check `SecurityConfig.java` — current config may be permissive for early dev.
- **[VALIDATE]** Before public launch: enable JWT or session auth on all `/api/v1/*` endpoints. Leave `/api/actuator/health` open for Render's health probes only.
- **[VALIDATE]** Shop owner auth: each shop should only see its own technicians' udhaar. Add a `@PreAuthorize` layer that checks `shopId` against the authenticated user's shop claim.
- **[FACT]** H2 console is disabled in prod (`spring.h2.console.enabled: false`).
- **[FACT]** `show-sql` is `false` in prod — no DB queries in logs.
- **[ASSUMPTION]** Rate limiting is not yet implemented. Add Bucket4j or Resilience4j at the gateway before public traffic.

---

## Performance knobs

- `spring.threads.virtual.enabled: true` — Java 21 Virtual Threads. Cheap concurrency, no pool tuning needed.
- `server.tomcat.threads.max: 200` — Tomcat keeps 200 classic threads as ceiling; VTs run on top.
- `spring.cache.caffeine.spec: maximumSize=1000,expireAfterWrite=10m` — in-JVM cache. Per-node only. **If you horizontally scale, move to Redis.**
- Connection pool (HikariCP default: 10 connections) — tune in `DataSourceConfig` when concurrent request rate exceeds ~20 qps.

---

## Logging

Prod profile:

```
root: WARN
com.partlinq: INFO
org.hibernate.SQL: WARN
```

- **[INFERENCE]** INFO level for business code is the right balance. Every scheduler run, every payment, every order transition → INFO. Errors escalate naturally.
- **[VALIDATE]** Pipe logs to a central store (Better Stack, Logtail, Datadog). `stdout` on Render is ephemeral — gone on restart.

---

## Common admin tasks

### Add a new shop (no UI yet)

```bash
curl -X POST <backend>/api/v1/shops -H 'Content-Type: application/json' -d '{...}'
```

### Force trust recomputation

```bash
curl -X POST <backend>/api/v1/trust/recalculate
```

Runs the PageRank-style algorithm across all technicians. Takes seconds for <1,000 techs.

### Inspect a technician's full history

```bash
curl '<backend>/api/v1/udhaar/history?technicianId=<id>&shopId=<id>'
```

Returns full ledger (debits, credits, adjustments) sorted by date.

### Manual adjustment (write-off, refund, correction)

```bash
curl -X POST <backend>/api/v1/udhaar/adjustment \
  -H 'Content-Type: application/json' \
  -d '{
    "technicianId": "<id>",
    "shopId": "<id>",
    "amount": -500,
    "reason": "Overcharge correction",
    "adjustmentType": "CREDIT_ADJUSTMENT",
    "recordedBy": "admin@example.com"
  }'
```

**[VALIDATE]** Audit every adjustment. They're the most-likely fraud vector in a credit system.

---

## Known limitations (be honest)

- **No tenant isolation enforced.** Any authenticated user can read any shop's data if auth layer is lax. Close this before public launch.
- **No WhatsApp/SMS adapter.** `NotificationService.queuePaymentReminder` stores intent; delivery requires your integration.
- **No dashboard registration forms.** First-run is via `curl` / Swagger. UI forms are roadmap.
- **Caffeine cache is per-node.** Multi-instance deploy will show stale data until cache expiry.
- **Trust algorithm is heuristic.** Document it, explain it to users. Don't pretend it's ML.
- **Reminder dedup is in-memory, not persisted.** Restart loses the "last reminded" state. Acceptable for MVP; wire to DB for long-term.

**[INFERENCE]** These are acceptable for a **1–20 shop pilot**. Beyond that, fix them in order: auth, notifications, forms.

---

## Quick health check (run weekly)

```bash
# 1. Backend up
curl -sf <backend>/api/actuator/health | jq .status

# 2. Scheduler ran today
curl -s <backend>/api/actuator/metrics/scheduled.tasks.execution | jq

# 3. No runaway queries
curl -s <backend>/api/actuator/metrics/hikaricp.connections.active | jq

# 4. Frontend sees backend (check browser console)
# Open dashboard → should show LIVE badge, not DEMO

# 5. Scheduler effectiveness
# On Tuesday, verify Monday's summary notifications show in your WhatsApp logs
```

---

## Cost expectations (rough)

**[ASSUMPTION]** Based on Render's 2026 pricing, small workload (10 shops, 150 technicians, 500 orders/mo):

- **Backend** — Render Standard: **₹1,500/month**
- **Postgres** — Render Starter Postgres: **₹600/month**
- **Frontend** — Vercel Hobby: **₹0** (under 100GB bandwidth)
- **WhatsApp Business API** — Meta-approved BSP (Gupshup, AiSensy): **₹0.80–1.20 per reminder** × ~30 reminders/shop/month × 10 shops = **₹300–400/month**
- **Total** — **~₹2,500/month** at pilot scale

Crosses **₹10,000/month** only past ~100 shops or heavy message volume.

---

## Who to call when

- **500 error on API** — check Render logs, check DB connectivity
- **Dashboard shows DEMO persistently** — CORS issue or `VITE_API_URL` wrong
- **Scheduler not firing** — check `@EnableScheduling` on `PartLinQApplication.java`, verify server clock is NTP-synced
- **Udhaar balance wrong** — pull ledger history, look for missing payment or duplicate credit entry
- **Trust score frozen** — trigger `POST /trust/recalculate`; if no change, check `TrustService` logs

Keep this file next to the deploy runbook. Update the **Known limitations** section as you fix them.
