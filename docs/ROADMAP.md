# Laleh+T roadmap

## Phase 1.1 — Repository bootstrap

- [x] Establish the Laleh+T Gradle workspace and module boundaries.
- [x] Add reproducible Gradle Wrapper and Android CI validation.
- [x] Document build, architecture, and security constraints.

## Next phases

1. Introduce adapters from the existing app integration to `telegram-core` without changing the single-session lifecycle.
2. Move Laleh offline feature contracts and tests into `laleh-core` with backward-compatible storage access.
3. Add device-based validation of authentication, messaging, proxy support, and offline feature continuity using a test account.
4. Plan release signing and distribution without committing secrets.
