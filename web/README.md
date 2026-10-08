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
