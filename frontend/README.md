# Charm Quiz frontend

React and Vite customer interface for the charm recommendation, catalog, and history quiz.

## Run locally

From the `frontend` directory:

```sh
npm install
npm run dev
```

Open the local URL printed by Vite. On PowerShell where `npm` script execution is blocked, use `npm.cmd` instead.

Create a local environment file from `.env.example` and configure:

```properties
VITE_API_BASE_URL=http://localhost:8080/api
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=<public-publishable-key>
```

The publishable key is intentionally public and relies on Supabase RLS. Never add a service-role key, `SUPABASE_SECRET_KEY`, Gemini key, or any backend credential to a `VITE_*` variable.

## Backend integration

- The catalog loads from `GET /api/charms`.
- Recommendation profiles are sent to `POST /api/recommendations`.
- Catalog stock updates subscribe directly to Supabase Realtime `UPDATE` events on `public.charm`.
- Realtime events are invalidation signals only. The frontend always refetches `GET /api/charms` for display data.

No catalog or recommendation mock data is used at runtime.

## Checks

```sh
npm test
npm run lint
npm run build
```
