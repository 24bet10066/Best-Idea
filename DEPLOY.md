# PartLinQ — Production Deployment Guide

## Step 1: Push Code to GitHub

The repo is already committed locally. Open your terminal, go to this folder, and run:

```bash
git push -u origin main
```

If it asks for credentials:
- Username: `24bet10066`
- Password: use a **GitHub Personal Access Token** (Settings → Developer Settings → Personal Access Tokens → Tokens (classic) → Generate new token → select `repo` scope)

Alternatively, if you have SSH configured:
```bash
git remote set-url origin git@github.com:24bet10066/Best-Idea.git
git push -u origin main
```

---

## Step 2: Create PostgreSQL Database on Render

1. Go to [render.com/dashboard](https://dashboard.render.com) → **New +** → **PostgreSQL**
2. Settings:
   - **Name**: `partlinq-db`
   - **Database**: `partlinq`
   - **User**: `partlinq`
   - **Region**: Oregon (US West) — same as your other services
   - **Plan**: Free
3. Click **Create Database**
4. After creation, go to the database page and **copy the Internal Database URL** — it looks like:
   ```
   postgresql://partlinq:RANDOMPASSWORD@dpg-XXXXXX-a.oregon-postgres.internal/partlinq
   ```
   ⚠️ Use **Internal URL** (not External) so Render backend connects for free over private network.

---

## Step 3: Deploy Spring Boot Backend on Render

1. Go to **New +** → **Web Service**
2. Connect your GitHub repo: `24bet10066/Best-Idea`
3. Settings:

   | Field | Value |
   |-------|-------|
   | **Name** | `partlinq-api` |
   | **Language** | **Java** (NOT Elixir — change it!) |
   | **Branch** | `main` |
   | **Root Directory** | *(leave empty — backend is at repo root)* |
   | **Build Command** | `./mvnw clean package -DskipTests` |
   | **Start Command** | `java -jar target/partlinq-core-1.0.0.jar` |
   | **Plan** | Free (0.1 CPU / 512MB RAM) |

4. Under **Advanced** → **Environment Variables**, add these:

   | Key | Value |
   |-----|-------|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DATABASE_URL` | `jdbc:postgresql://dpg-XXXXXX-a.oregon-postgres.internal:5432/partlinq` |
   | `DATABASE_USER` | `partlinq` |
   | `DATABASE_PASSWORD` | *(password from Render DB page)* |
   | `CORS_ALLOWED_ORIGINS` | *(fill in after Step 4 — Vercel URL)* |

   ⚠️ **DATABASE_URL format**: Render gives `postgresql://user:pass@host/db` but Spring Boot needs `jdbc:postgresql://host:5432/db`.
   Transform it:
   - Render gives: `postgresql://partlinq:ABC@dpg-XYZ.oregon-postgres.internal/partlinq`
   - You need: `jdbc:postgresql://dpg-XYZ.oregon-postgres.internal:5432/partlinq`

5. Click **Create Web Service**
6. Wait for build to complete (~4-6 minutes). Your backend URL will be:
   ```
   https://partlinq-api.onrender.com
   ```
   Test it: `https://partlinq-api.onrender.com/api/v1/shops` — should return `[]` or shop data.

---

## Step 4: Deploy React Frontend on Vercel

1. Go back to [vercel.com/new](https://vercel.com/new) and import `24bet10066/Best-Idea`
2. Settings:

   | Field | Value |
   |-------|-------|
   | **Framework Preset** | **Vite** |
   | **Root Directory** | `partlinq-dashboard` |
   | **Build Command** | `npm run build` |
   | **Output Directory** | `dist` |
   | **Install Command** | `npm install` |

3. Under **Environment Variables**, add:

   | Key | Value |
   |-----|-------|
   | `VITE_API_URL` | `https://partlinq-api.onrender.com/api/v1` |

4. Click **Deploy**
5. Your frontend URL will be something like:
   ```
   https://best-idea-raj.vercel.app
   ```

---

## Step 5: Update CORS in Render (Critical!)

Now that you have the Vercel URL, go back to Render → `partlinq-api` → Environment:

Update `CORS_ALLOWED_ORIGINS` to:
```
https://best-idea-raj.vercel.app,https://best-idea-git-main-raj.vercel.app
```

Then click **Save** — Render will auto-redeploy.

---

## Step 6: Test End-to-End

1. Open your Vercel URL in browser
2. Header should show **🟢 LIVE** badge (not DEMO)
3. Navigate to Orders — you should see real data
4. Check browser console for any CORS errors (should be zero)

### Quick health check:
```bash
# Backend health
curl https://partlinq-api.onrender.com/api/health

# Shops endpoint
curl https://partlinq-api.onrender.com/api/v1/shops

# Frontend loads
open https://best-idea-raj.vercel.app
```

---

## Notes

- **Render Free Tier**: The backend "spins down" after 15 minutes of inactivity. First request after sleep takes ~30 seconds. Upgrade to Starter ($7/mo) to keep it always-on.
- **Database**: `ddl-auto: update` creates tables automatically on first deploy. Once stable, change to `validate` for safety.
- **Logs**: Render dashboard → `partlinq-api` → Logs — watch for startup errors.
- **Future**: When you get a custom domain, add it to `CORS_ALLOWED_ORIGINS`.
