# GitHub audit — 2026-10-08 (Asia/Tehran)

Native Git fetch, GitHub Issue #33, PR metadata and local merge-tree were read before implementation. No branches were merged or reset.

- Default: `codex/laleh-intelligent-search-v2` at `219e371ae704080cd1b670be0823e012f6b60ff3`.
- `main`: `d17ffa2a1f5bee79bc7f1c19a14f54f147ea50fd`.
- TASK-022 exists on `codex/task-001-audit` at `8cfbbe5bb2bc9461304b7c10fce3343466c26ac1`, including its actual local web workspace. TASK-023 therefore branches from that head; this does not integrate that branch into main/default.

## Open PR dependencies and observed conflicts

| PR | Actual base | Head SHA | Local conflict paths |
| --- | --- | --- | --- |
| #12 | `main` | `219e371ae704080cd1b670be0823e012f6b60ff3` | `.github/workflows/build-apk.yml` |
| #20 | `sinicablekaveh-create-patch-1` | `a64f57db44164594a48f54d16ab3c32e066fead5` | `README.md`; `app/src/main/java/com/sinicable/telegramelectric/CentralCore.java`; `app/src/main/java/com/sinicable/telegramelectric/CentralCorePanel.java`; `app/src/main/java/com/sinicable/telegramelectric/TelegramClientManager.java` |
| #22 | `fix/baseline-compile` | `566fce21e0c51983886fb4555357eaa2dcbe28b5` | `app/src/main/java/com/sinicable/telegramelectric/TelegramClientManager.java`; `app/src/test/java/com/sinicable/telegramelectric/TelegramClientManagerTest.java` |
| #28 | `codex/laleh-intelligent-search-v2` | `a0a9db73c89930853220135cced1134559c3ba57` | `.github/workflows/build-apk.yml` |
| #30 | `codex/record-ci-pr-validation` | `d62065d086682d78e383939969543d092f7fde8a` | `app/src/main/java/com/sinicable/telegramelectric/ChatPhoneIndex.java`; `app/src/main/java/com/sinicable/telegramelectric/ChatPhoneResultAdapter.java`; `app/src/main/java/com/sinicable/telegramelectric/ChatPhoneSourceLocator.java`; `app/src/main/java/com/sinicable/telegramelectric/TelegramAppConnector.java`; `app/src/main/java/com/sinicable/telegramelectric/TelegramChatIdConverter.java`; `app/src/main/java/com/sinicable/telegramelectric/TelegramMessageSourceOpener.java`; `app/src/main/java/com/sinicable/telegramelectric/TelegramPhoneTarget.java` |
| #31 | `codex/record-ci-pr-validation` | `868bc77a2a39532eb15543f1acc580270b9f2897` | None in local merge simulation |
| #32 | `codex/record-ci-pr-validation` | `7e47a9fc59498ecfc7f7297f59167640dfdb8c0d` | None in local merge simulation |

GitHub API confirms these PRs remain open; #32 is draft. #31 and #32 were reported mergeable; #28 returned unknown, while the local merge simulation identifies a workflow conflict. These are observations, not permission to merge.

#30 contains Javadoc based on older source; it conflicts with seven phone/deep-link classes already changed on its base. #31 adds test coverage on the same base and touches the connector test also extended by #32. Preserve these stacked changes and compare actual patches; do not cherry-pick their shared ancestry a second time. #12 head is the current default itself, and is already contained on this task base. No TASK-023 implementation existed in the fetched histories.

The four review findings on #28 are addressed on #32 (URI encoding, checkout credentials, lint documentation/gates). Its existing run [37756375827](https://github.com/sinicablekaveh-create/Laleh/actions/runs/37756375827) was independently queried and successful for SHA `7e47a9fc59498ecfc7f7297f59167640dfdb8c0d`; this evidence belongs to #32, not this task.

## Remote branch inventory

Counts are commits unique to main / unique to each branch, not a feature-completion score.

| Branch | Head SHA | Counts |
| --- | --- | --- |
| `origin/ci-baseline-ef701a9` | `ef701a9b584e31012962d88b43b7de2f0ef341d1` | 8 / 0 |
| `origin/ci/pr-validation` | `0123c402e420f66ea9e67fff00f5ea5d386c3d01` | 6 / 2 |
| `origin/coderabbit/document-pull-request-functions/90b165ee` | `d62065d086682d78e383939969543d092f7fde8a` | 5 / 2 |
| `origin/coderabbit/pull-request-test-coverage/a80d0cef` | `868bc77a2a39532eb15543f1acc580270b9f2897` | 5 / 4 |
| `origin/codespace-refactored-lamp-xrpgrq55ggrxf9xrg` | `d8a51e612d096c20f835d42319679fd48d582496` | 48 / 3 |
| `origin/codespace-verbose-acorn-qv45vg775jp6c957q` | `c455276c364e0f5e6353e15efa6aec0f0b3735b5` | 28 / 0 |
| `origin/codex/core-worker-lifecycle` | `a64f57db44164594a48f54d16ab3c32e066fead5` | 49 / 2 |
| `origin/codex/group-search-precision` | `0bad922983ffcf7a704f484474b7652a1f2ca9bf` | 20 / 8 |
| `origin/codex/integrate-github-plugin` | `1df302e52f69d716fb12eed2632b03fc0e934d6d` | 49 / 1 |
| `origin/codex/integrate-github-plugin-5sjavp` | `9b1ab63929d7f24e4984e991cc1c131096b1a7e0` | 49 / 1 |
| `origin/codex/integrate-github-plugin-jdhda3` | `17aae824322b334dc54eedf2769742a26ab39f07` | 49 / 2 |
| `origin/codex/laleh-execution-chain` | `2b36ebabd1552421c9669257509bd3e426843207` | 16 / 0 |
| `origin/codex/laleh-intelligent-search-v2` | `219e371ae704080cd1b670be0823e012f6b60ff3` | 20 / 17 |
| `origin/codex/member-targets-1.12` | `0f3a4fdda8f60dc1fa0d19e9f7fef119ea603090` | 49 / 0 |
| `origin/codex/record-ci-pr-validation` | `a0a9db73c89930853220135cced1134559c3ba57` | 5 / 3 |
| `origin/codex/stability-1.11` | `566fce21e0c51983886fb4555357eaa2dcbe28b5` | 55 / 3 |
| `origin/codex/task-001-audit` | `8cfbbe5bb2bc9461304b7c10fce3343466c26ac1` | 3 / 45 |
| `origin/feature/telegram-group-search-engine` | `d694ae03b038f325eb32b62ffb3019ccc3ebec83` | 20 / 3 |
| `origin/fix/baseline-compile` | `1dd8506e98ebf449b54a4ce3e6654182d79f6409` | 16 / 0 |
| `origin/fix/pr28-uri-checkout-lint-gate-20261008` | `7e47a9fc59498ecfc7f7297f59167640dfdb8c0d` | 5 / 5 |
| `origin/main` | `d17ffa2a1f5bee79bc7f1c19a14f54f147ea50fd` | 0 / 0 |
| `origin/sinicablekaveh-create-patch-1` | `0883d6976cc3c5e6c2dec4f2e6b16e2980e5100b` | 11 / 0 |
| `origin/work` | `59bdfccb1b7354a1c815da0474f524d894349dad` | 27 / 0 |

## Remaining integration decisions

Resolve #20/#22 auth/lifecycle/permission conflicts in separate scoped work with regression coverage; do not mass-merge them. Resolve CI divergence for #12/#28 without dropping gates. This task leaves Android source and local storage untouched. Catalog remains empty and remote synchronization remains disabled pending an approved public source/endpoint.
