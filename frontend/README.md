# Charm Quiz frontend

React and Vite customer interface for the charm recommendation, catalog, and history quiz.

## Run locally

From the `frontend` directory:

```sh
npm install
npm run dev
```

Open the local URL printed by Vite. On PowerShell where `npm` script execution is blocked, use `npm.cmd` instead.

## Catalog data

The catalog uses 62 sample charms by default. Sample stock changes automatically every 8 seconds while the catalog is open.

Set `VITE_CATALOG_SOURCE=api` to read `GET /api/charms` and poll it every 8 seconds. `VITE_API_BASE_URL` defaults to `http://localhost:8080/api`. API rows should include `id`, `name`, `category`, `imageUrl` (or `image_url`), and `availabilityStatus` (or `availability_status`) with `IN_STOCK` or `OUT_OF_STOCK`. An optional `thumbnailUrl` is preferred for grid images. Unknown stock values display as "Check Availability".

The current backend filters active charms but does not yet expose a stock field or push events. Polling is the frontend update path until that contract is available.

## Checks

```sh
node --test src/services/*.test.js
npm run lint
npm run build
```
