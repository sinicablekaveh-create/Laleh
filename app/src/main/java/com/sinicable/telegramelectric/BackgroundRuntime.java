package com.sinicable.telegramelectric;

final class BackgroundRuntime {
    static final class Snapshot {
        final TelegramClientManager telegram;
        final WordBank wordBank;
        final CentralCore core;

        Snapshot(
                TelegramClientManager telegram,
                WordBank wordBank,
                CentralCore core
        ) {
            this.telegram = telegram;
            this.wordBank = wordBank;
            this.core = core;
        }
    }

    private static Snapshot current;

    static synchronized void attach(
            TelegramClientManager telegram,
            WordBank wordBank,
            CentralCore core
    ) {
        if (telegram == null || wordBank == null || core == null) return;
        current = new Snapshot(telegram, wordBank, core);
    }

    static synchronized Snapshot get() {
        return current;
    }

    static synchronized void clear() {
        current = null;
    }

    private BackgroundRuntime() {
    }
}
