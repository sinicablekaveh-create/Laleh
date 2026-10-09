import { spawn } from "node:child_process";
import { createServer } from "node:net";
import assert from "node:assert/strict";
const listener = createServer();
await new Promise(resolve => listener.listen(0, "127.0.0.1", resolve));
const port = listener.address().port;
await new Promise(resolve => listener.close(resolve));
const server = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "-H", "127.0.0.1", "-p", String(port)],
  { stdio: ["ignore", "ignore", "pipe"], env: { ...process.env, NEXT_TELEMETRY_DISABLED: "1" } });
let exited = false;
server.on("exit", () => { exited = true; });
const origin = `http://127.0.0.1:${port}`;
try {
  let ready = false;
  for (let attempt = 0; attempt < 100 && !exited; attempt++) {
    try { ready = (await fetch(`${origin}/api/health`)).ok; } catch { }
    if (ready) break;
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  assert.ok(ready, "production server failed to start");
  const home = await fetch(`${origin}/?q=${encodeURIComponent("برق صنعتی تهران")}`);
  assert.equal(home.status, 200);
  assert.equal(home.headers.get("x-content-type-options"), "nosniff");
  assert.equal(home.headers.get("x-frame-options"), "DENY");
  assert.equal(home.headers.get("referrer-policy"), "no-referrer");
  assert.ok(home.headers.get("content-security-policy").includes("frame-ancestors 'none'"));
  const html = await home.text();
  assert.match(html, /dir="rtl"/); assert.match(html, /lang="fa"/);
  const search = await fetch(`${origin}/api/search?q=synthetic`);
  assert.equal(search.headers.get("cache-control"), "no-store");
  const result = await search.json();
  assert.equal(result.apiVersion, 1); assert.equal(result.meta.publicOnly, true);
  const discovery = await (await fetch(`${origin}/api/discovery?q=synthetic`)).json();
  assert.equal(discovery.data.engineVersion, 3); assert.equal(discovery.data.searchVersion, 6);
  const health = await (await fetch(`${origin}/api/health`)).json();
  assert.equal(health.data.backend, "disabled");
  assert.equal((await fetch(`${origin}/api/groups/invalid`)).status, 400);
  assert.equal((await fetch(`${origin}/api/groups/-9223372036854775808`)).status, 404);
  const categories = await fetch(`${origin}/api/categories`);
  const etag = categories.headers.get("etag"); assert.ok(etag);
  assert.equal((await fetch(`${origin}/api/categories`, { headers: { "If-None-Match": etag } })).status, 304);
  assert.equal((await fetch(`${origin}/saved`)).status, 200);
  console.log("Production HTTP smoke passed: RTL, headers, API compatibility, discovery extension, errors, ETag, saved page.");
} finally {
  server.kill("SIGTERM");
  if (!exited) await new Promise(resolve => { server.once("exit", resolve); setTimeout(() => { server.kill("SIGKILL"); resolve(); }, 3000).unref(); });
}
