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
Only explicitly approved public metadata may be connected in later sync/index tasks.
