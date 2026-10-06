# Group discovery precision

The audit began at `2d9c770` on `main`. The existing `TelegramClientManager`
already owned TDLib, request deadlines, chat recovery and staged discovery.
The old groupsearch helpers were not connected to that flow. They expanded
keywords and awarded substring scores; the channel check used reflection and
accepted supergroups when reflection failed.

## Updated flow

User query → GroupSuggestionEngine → SearchQueryBuilder → existing manager's
SearchPublicChats request → GroupFilter → GroupRankingEngine → ranked IDs and cards.

The discovery panel offers suggestions; selecting one fills the query. Pressing
search submits that query. It does not fan out all suggestions into concurrent
Telegram requests. Existing background word-search stages also pass through the
same group-focused public query builder. Their timing, pause/resume and flood-wait
handling remain in CentralCore. Known-chat search remains available.

Persian normalization handles Arabic kaf/yeh, diacritics, punctuation, repeated
spaces and half spaces. The keyword bank recognizes only the requested electrical,
industry, location and business phrases actually present in the query.

Ranking awards each signal at most once:

- 40: exact normalized title topic, ignoring the standalone group marker.
- 30: all query topic tokens found as whole tokens in title/username.
- 20: a requested bank location occurs in the result.
- 10: a requested electrical, industry or business category occurs in the result.

`گروه برق لاله زار` scores 100 for `برق لاله زار`. A query without a location
cannot earn location points. Results sort by descending score and then chat ID.
Private chats, secret chats and channels are excluded before ranking.

## Execution and storage

The existing runtime executor owns pending searches. Identical normalized public
queries share one operation and one deadline. At most eight distinct searches can
be pending. Successful results (including empty results) use a 64-entry LRU cache
with a two-minute monotonic TTL. Errors are never cached. Cache hits report zero
new groups. Closing or replacing the session invalidates cached and pending
responses. The existing TDLib client, native lifecycle and authentication remain.

The latest successful search is saved asynchronously as up to 200
`GroupSearchRecord` JSON objects in private preferences. Fields are query,
groupId, title, username, source, score and timestamp. The persisted query is the
actual normalized public-search query. IDs are JSON strings to preserve 64-bit
precision in Web consumers; timestamps are UTC epoch milliseconds. Source labels
are Android, Web and Background Indexer; this Android application writes Android.
`getGroupSearchRecords()` exposes the snapshot, and the record model supports
JSON import/export. No Web service or background-indexer transport is configured
in this repository, so cross-device synchronization is not implemented.

## Validation and boundaries

Unit/integration coverage includes suggestions, Persian normalization, filtering,
scoring, cache normalization/expiry/LRU/clear, duplicate coalescing, failure retries,
session invalidation, ranked TDLib results and record persistence/serialization.
The original staged-search, timeout, missing-chat recovery and authentication
regressions remain part of the full suite.

Build with JDK 17, Gradle 8.9 and Android SDK 35:

```
gradle --no-daemon testDebugUnitTest assembleDebug
```

The audit also found pre-existing compilation failures in ChatPhoneIndex and
ChatPhoneResultAdapter. A separate compatibility fix accepts the extractor's List
and binds every model source through the card's existing API and navigation helpers.

Public Telegram search only returns discoverable chats; filtering cannot expose
private groups. Live discovery quality, device rendering, and real-account TDLib
behavior require a signed-in Android device. No live account search was performed.
