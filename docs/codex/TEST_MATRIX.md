# Validation matrix

| Boundary | Deterministic coverage | External check still needed |
| --- | --- | --- |
| TDLib lifecycle/auth | Existing Android manager, phone, core, service/activity regression tests | Device login/2FA/reconnect/background |
| Public labels | Android + web reject unknown private fields, invalid IDs, control/bidi characters | Catalog provenance/operator approval |
| Search | Shared query fixtures; prefix revisions, filters, ranking, punctuation, explicit zero-result recovery | Live TDLib relevance and device usability |
| User choices | Local history opt-in/clear/reconstruction; bounded favorite IDs/metadata and theme/category validation | Full browser visual/keyboard audit; Android UI rotation/theme |
| Sync | Consent generations, dispatch revocation, old ack, duplicate/late response, timeout, close/cancel, persistent backoff | Authorized endpoint/auth, real cancellation, outage/retention |
| Web/API | v1 compatibility, v3/v6 additive route; headers, no-store, ETags, production HTTP checks | HTTPS hosting, mobile visual/accessibility review |
| Performance | Synthetic 10,000-public-row benchmark, numeric local aggregates, build byte report | Real device memory/thread/render profiles; production traffic |
| Release | Debug + release unit/lint/assembly; package/signature checks | Established release key, version increment, install/update/store review |

Commands and artifact paths are in RELEASE_RUNBOOK.md. Benchmarks are measurements, not unit-test timing assertions. No synthetic groups are published to the production catalog. CI uploads bounded reports, not Telegram data.
