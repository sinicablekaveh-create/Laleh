# Laleh+T security boundaries

- Do not commit credentials, API hashes, phone numbers, authentication codes, passwords, encryption keys, session files, APKs, or private message data.
- Never copy an APK into another APK, import a Telegram application's private database, or duplicate a Telegram session.
- Keep `TelegramClientManager` as the sole TDLib client/session owner until a reviewed migration replaces it with an equivalent single owner.
- Store Laleh offline data only in the app's private storage and preserve existing compatibility.
- Proxy support remains user-configured and continues through the existing Telegram integration; proxy URLs containing credentials must never be logged or committed.
- CI uploads only the debug build output and sanitized test reports.
