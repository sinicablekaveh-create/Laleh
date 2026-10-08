# Laleh discovery web

Node 24+, npm and Next.js/React/TypeScript. This workspace has no Telegram
authentication/session access. The public discovery index starts empty; no live
backend or public group catalog is configured yet.

```sh
cd web
npm ci
npm test
npm run typecheck
npm run build
npm run measure:build
```

Use `npm run dev` for local development or `npm start` after a production build.
The server searches `data/public-groups.json` through the validated local index and
30-second bounded result cache. The committed catalog is empty. Operators may add
only explicitly approved public records conforming to `shared/discovery.schema.json`.
Malformed/private rows are rejected; never copy Telegram session/contact databases
into this file. There is no network synchronization service configured.

## Local public API contract

TASK-024 exposes read-only Next.js route handlers backed by the same approved local
catalog. These routes do not call TDLib, do not authenticate to Telegram, and do not
invent a remote service:

- `GET /api/search?q=&category=&location=&sort=&offset=&limit=`
- `GET /api/groups/{negative-chat-id}`
- `GET /api/groups/{negative-chat-id}/related?limit=`
- `GET /api/categories`
- `GET /api/health`
- `GET /api/status` (alias of health)

Every response has `apiVersion: 1` and metadata declaring
`source: "local-approved-catalog"` and `publicOnly: true`. Health/status reports
`backend: "disabled"` truthfully. Unknown groups return 404 and malformed identifiers
return 400. Search paging and related-result limits are bounded server-side.

TASK-025 adds conditional HTTP caching only where it is safe. Successful approved
public group/category metadata may use
`public, max-age=60, s-maxage=300, stale-while-revalidate=600` with SHA-256 ETags
and `304 Not Modified`. Search requests remain `no-store` because their query terms
are user-provided; health/status and error responses also remain `no-store`.
Result/category lists prefetch at most four internal detail routes, while the App
Router loading state provides an accessible no-JavaScript progress surface with
reduced-motion support.

CI restores `web/.next/cache` and `npm run measure:build` reports production
JavaScript chunk counts plus raw/gzip size. On TASK-025 implementation run
`37772949785`, 26 web tests, typecheck and production build passed; compile was
3.6s versus the prior TASK-024 4.9s baseline, and the build measured 27 JS chunks,
932301 raw bytes, 288559 gzip bytes, largest chunk 242379 bytes. Android regression
tests, lint and debug APK assembly also passed even though TASK-025 changes no
Android/TDLib source. APK artifact `telegram-electric-debug`: `11548880142`.
