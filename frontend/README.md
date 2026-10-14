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

## History quiz data

The quiz uses a 10-question preview by default. Set `VITE_QUIZ_SOURCE=api` to use the backend at `VITE_API_BASE_URL`. The frontend expects `POST /quiz/start` to return a session ID and 10 questions with four options each, and `POST /quiz/answer` to accept `{ sessionId, questionId, optionId }` and return answer correctness plus the correct option ID. Question responses must not contain answer correctness before submission.

The final answer response may include a `result` object with score, counts, optional details, and `canRetry`. When it does not, the frontend requests `GET /quiz/result?sessionId=...`. If that request fails, the Result screen shows totals from the 10 graded answers and offers to reload the backend result. The current backend does not yet expose these quiz endpoints.

## Manager sign-in

Set `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY` in `frontend/.env.local` to enable `/manager/login`. Supabase Auth manages session persistence; the frontend does not store passwords or use a backend secret key. Manager routes require a signed-in Supabase user. Server-side authorization and Supabase Row Level Security must enforce manager permissions for manager data.

The `/manager` charm dashboard currently uses preview inventory with in-memory add/edit changes and local image previews. Changes disappear on refresh. Manager list, image upload, and save APIs are intentionally not called until their backend contract exists.

The protected `/manager/rules` screen uses preview recommendation rules and the same in-memory charm list. It supports add/edit, search/filter, priority ordering, and enable/disable. Operator is free text until the backend publishes its supported operator contract; the displayed sample operator is illustrative, not an API guarantee. No recommendation matching runs in the frontend, and rule changes are not sent to an API yet.

The protected `/manager/quizzes` screen lists preview quiz questions and imports JSON to an in-memory preview library. The temporary preview format is `{ "title": "Vietnam History", "questions": [{ "prompt": "...", "options": ["A", "B", "C", "D"], "correctIndex": 0 }] }`. The import checker reports JSON syntax and display-shape errors by question/field. It is not authoritative backend validation; no quiz admin endpoints, import schema, or status actions have been published yet. Save preview does not persist or update the customer quiz.

## Checks

```sh
node --test src/services/*.test.js
npm run lint
npm run build
```
