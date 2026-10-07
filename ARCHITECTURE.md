# Laleh Architecture Overview

This document provides a high-level architecture diagram of the Laleh Android application, showing the key components and their interactions.

## System Architecture

```mermaid
graph TB
    subgraph "Telegram Integration Layer"
        TelegramClientManager["TelegramClientManager<br/>- Manages TDLib connection<br/>- Handles auth, send, search"]
        TelegramAPI["Telegram API<br/>via TDLib"]
    end

    subgraph "Core Business Logic"
        CentralCore["CentralCore<br/>- Schedules sends<br/>- Manages discovery cycles<br/>- Persists retry deadlines<br/>- Parses rate limits"]
        SmartSearchQueue["SmartSearchQueue<br/>- Prioritizes search queries<br/>- Records feedback<br/>- Lazy persistence"]
        WordBank["WordBank<br/>- Stores search terms<br/>- Tracks search feedback"]
    end

    subgraph "Data Persistence"
        SharedPrefs["SharedPreferences<br/>- User settings<br/>- Selected groups<br/>- Sent counts<br/>- Retry deadlines"]
    end

    subgraph "Export & Output"
        ExportFileWriter["ExportFileWriter<br/>- XLSX/CSV generation<br/>- XML sanitization<br/>- Control char replacement"]
        ExportedFiles["Exported Files<br/>XLSX/CSV"]
    end

    subgraph "UI Layer"
        MainActivity["MainActivity<br/>- User interface<br/>- Settings UI<br/>- Status updates"]
    end

    subgraph "Rate Limiting"
        RateLimitParser["Rate Limit Parser<br/>- FLOOD_WAIT<br/>- FLOOD_PREMIUM_WAIT<br/>- SLOWMODE_WAIT<br/>- RETRY AFTER"]
        RetryDeadlineManager["Retry Deadline Manager<br/>- Persists deadlines<br/>- Prevents premature retry<br/>- Survives restart"]
    end

    %% Connections
    MainActivity -->|User actions| CentralCore
    CentralCore -->|Send/Search requests| TelegramClientManager
    TelegramClientManager -->|TDLib protocol| TelegramAPI
    CentralCore -->|Read/Update| SharedPrefs
    CentralCore -->|Next query| SmartSearchQueue
    SmartSearchQueue -->|Record results| WordBank
    WordBank -->|Persist feedback| SharedPrefs
    
    TelegramClientManager -->|Rate limit responses| RateLimitParser
    RateLimitParser -->|Delay calculation| RetryDeadlineManager
    RetryDeadlineManager -->|Store deadline| SharedPrefs
    RetryDeadlineManager -->|Check on resume| CentralCore
    
    CentralCore -->|Export data| ExportFileWriter
    ExportFileWriter -->|Sanitize XML| ExportedFiles
    WordBank -->|Search data| ExportFileWriter

    style CentralCore fill:#ff9999
    style TelegramClientManager fill:#99ccff
    style SmartSearchQueue fill:#99ff99
    style RateLimitParser fill:#ffcc99
    style ExportFileWriter fill:#cc99ff
```

## Key Components

### 1. **CentralCore**
- Central orchestrator for send and discovery cycles
- Manages retry deadlines to respect Telegram rate limits
- Persists `retryNotBefore` timestamp across app restarts
- Handles multiple rate limit formats

### 2. **TelegramClientManager**
- Wraps TDLib (Telegram Database Library)
- Manages authentication and connection state
- Sends messages and performs searches
- Version string updated to match app version

### 3. **SmartSearchQueue**
- Intelligently prioritizes search queries
- Records search results and feedback
- **Optimization**: Only persists state after `recordResult()`, not on every `nextQuery()`
- Reduces redundant JSON writes during search cycles

### 4. **ExportFileWriter**
- Generates XLSX and CSV exports
- **New**: Replaces XML-forbidden control characters with U+FFFD
- Preserves valid Unicode (Persian text, emoji, supplementary chars)
- Ensures exported files remain valid and readable

### 5. **Rate Limit Parser**
- Recognizes multiple Telegram wait formats:
  - `FLOOD_WAIT_[seconds]`
  - `FLOOD_PREMIUM_WAIT_[seconds]`
  - `SLOWMODE_WAIT_[seconds]`
  - `RETRY AFTER [seconds]`
- Converts to milliseconds and enforces delays
- Prevents both premature retries and failed requests

### 6. **Retry Deadline Manager**
- Persists rate limit deadlines in SharedPreferences
- Survives app stop/restart cycles
- Prevents accidental bypass of Telegram's rate limits
- Uses safe arithmetic to avoid overflow

## Data Flow: Rate-Limited Send

```mermaid
sequenceDiagram
    participant User
    participant Core as CentralCore
    participant Telegram as TelegramClientManager
    participant Parser as Rate Limit Parser
    participant Prefs as SharedPrefs

    User->>Core: Start sending
    Core->>Telegram: Send message
    Telegram-->>Core: Error: FLOOD_WAIT_300
    Core->>Parser: Parse wait time
    Parser-->>Core: 300 seconds
    Core->>Prefs: Store deadline (now + 300s)
    Note over Core: Schedule retry after 300s
    Core-->>User: Status: "Rate limited, waiting..."
    Note over Core: Time passes...
    Core->>Prefs: Check deadline
    Prefs-->>Core: Deadline expired
    Core->>Telegram: Retry send
    Telegram-->>Core: Success
```

## Data Flow: App Restart During Rate Limit

```mermaid
sequenceDiagram
    participant User
    participant Core as CentralCore
    participant Prefs as SharedPrefs
    participant Telegram as TelegramClientManager

    User->>Core: Start sending
    Core->>Telegram: Send message
    Telegram-->>Core: Error: FLOOD_WAIT_300
    Core->>Prefs: Store deadline (now + 300s)
    Note over Core: Waiting...
    User->>Core: Stop app
    Note over Prefs: Deadline still stored
    User->>Core: Restart app
    Core->>Prefs: Load deadline
    Prefs-->>Core: Deadline retrieved
    Core->>Core: Calculate remaining delay
    Note over Core: Still need to wait ~295s
    Core-->>User: Status: "Rate limited, waiting..."
```

## Version Information

- **App Version**: 1.12.1 (version code: 15)
- **Build Configuration**: Gradle Kotlin DSL
- **CI/CD**: GitHub Actions (APK builds on PR + push)

## Testing Coverage

- `CentralCoreTest`: Rate limit parsing and deadline persistence
- `ExportFileWriterTest`: XML sanitization with control characters
- `SmartSearchQueueTest`: Single serialization per search cycle

## Build & CI

- **Build System**: Gradle with Kotlin DSL
- **CI Workflow**: `.github/workflows/build-apk.yml`
  - Triggers: `pull_request` (main), `push` (main + feature branches)
  - Concurrency controls prevent duplicate runs
  - Path filters optimize workflow execution
