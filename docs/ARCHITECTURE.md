# Laleh+T architecture

Laleh+T is an additive Android workspace that keeps the existing Laleh application functional while establishing migration boundaries for Telegram and Laleh features.

## Modules

- `app`: current Android UI and production integration. `TelegramClientManager` remains the only TDLib client and authentication/session lifecycle owner.
- `telegram-core`: Telegram-facing contracts and the TDLib dependency boundary. It must not instantiate a second client, import another app's database, or create a second session.
- `laleh-core`: offline Laleh feature contracts. Existing local word-bank, discovery cache, and preferences remain private application data.

## Data and lifecycle rules

- Authentication, messaging, proxy configuration, and TDLib updates continue through the existing `TelegramClientManager`.
- No APK, session directory, credentials, authentication code, private message, or Telegram database is copied or shared.
- Laleh offline data remains local and backward-compatible. Any storage migration requires a separately reviewed migration plan.
- `telegram-core` is a boundary module, not a new Telegram runtime. Dependency injection or adapters added later must receive the existing owner.
