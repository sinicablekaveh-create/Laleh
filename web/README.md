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
