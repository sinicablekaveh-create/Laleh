# Security and architecture review — 2026-10-09

Scope: local pending expansion tasks; independent read-only reviewer plus deterministic regression checks.

Verified and addressed:

- Disable/re-enable previously allowed an old upload acceptance to update cache or acknowledge newly queued same-revision work. Consent generations now cover queue selection, dispatch, and completion under the queue monitor.
- Public category/location labels accepted control/bidi overrides. Both platforms now reject them before normalization; schema documentation reflects the restriction.
- Cancellable transports and close support added. Late/duplicate completion remains one-shot. Returned cancellation handles are handled even if callbacks complete synchronously.
- Nonempty punctuation-only web queries no longer act as browse-all.
- Android discovery cards preserve ranked callback order rather than discovery-number order; private invite links are excluded from the public discovery panel.
- Web adds nosniff, no-referrer, frame denial, restricted feature permissions and CSP. Next.js requires inline bootstrap script support in this static CSP; nonce-based strict CSP is future deployment hardening, not a claimed property.

Preserved boundaries:

- Existing TelegramClientManager and one TDLib session; no new client/auth backend.
- Auth/session/contacts/private messages do not enter web public catalog, outbox, preferences exports or CI artifacts.
- Browser and Android discovery preferences are local. History is off by default and can be cleared; disabling discovery history does not remove existing core word-search attempt history.
- Remote sync remains disabled/unwired because endpoint/auth/catalog approval is absent. The legacy transport contract has no physical cancellation guarantee; production adapters must implement CancellableTransport.
- TDLib encryption-key migration and existing credential-store design remain separate security work, as required by repository memory. No encryption/secure-hardware claim is made.

Not verified: live Telegram, physical device, full browser visual/accessibility audit, HTTPS deployment, release signing/update, production request/memory profiles or remote cancellation. Do not use this local review as a production certification.

Local production Chromium interactions passed mobile RTL overflow checks, history consent/revocation, dark-theme selection, favorite removal, clear controls, storage-denied feedback and zero page errors. Synthetic favorites were injected only into the isolated test browser, never the committed catalog.
