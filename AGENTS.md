# Laleh agent context

- Before changing the repository, read `README.md`, `CHATGPT_MEMORY.md`, and the relevant rules under `docs/codex/`.
- Treat `CHATGPT_MEMORY.md` as durable project context. Verify time-sensitive facts such as open pull requests, workflow results, versions, and test status before relying on them.
- After meaningful work, append a dated, evidence-based activity entry to `CHATGPT_MEMORY.md`. Record only tests and builds that actually ran.
- Preserve the architecture and privacy invariants listed in `CHATGPT_MEMORY.md`. Never record credentials, Telegram session data, private messages, phone numbers, authentication codes, encryption keys, or secret environment values.
- Preserve existing user changes. Do not create a Git worktree unless the user explicitly requests one.
