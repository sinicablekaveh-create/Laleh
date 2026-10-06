package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** In-memory preferences used to verify reconstruction, rather than mock away persistence. */
final class TestPreferences {
    final Context context = mock(Context.class);
    private final Map<String, SharedPreferences> preferences = new HashMap<>();
    private final Map<String, Map<String, Object>> stores = new HashMap<>();

    TestPreferences() {
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt()))
                .thenAnswer(call -> prefs(call.getArgument(0)));
    }

    Map<String, Object> values(String name) { return stores.computeIfAbsent(name, ignored -> new HashMap<>()); }

    private SharedPreferences prefs(String name) {
        return preferences.computeIfAbsent(name, ignored -> {
            Map<String, Object> values = values(name);
            SharedPreferences prefs = mock(SharedPreferences.class);
            SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class, RETURNS_SELF);
            when(prefs.getString(anyString(), nullable(String.class)))
                    .thenAnswer(call -> values.getOrDefault(call.getArgument(0), call.getArgument(1)));
            when(prefs.getInt(anyString(), anyInt()))
                    .thenAnswer(call -> values.getOrDefault(call.getArgument(0), call.getArgument(1)));
            when(prefs.getLong(anyString(), anyLong()))
                    .thenAnswer(call -> values.getOrDefault(call.getArgument(0), call.getArgument(1)));
            when(prefs.getBoolean(anyString(), anyBoolean()))
                    .thenAnswer(call -> values.getOrDefault(call.getArgument(0), call.getArgument(1)));
            when(prefs.getStringSet(anyString(), nullable(Set.class))).thenAnswer(call -> {
                Set<String> value = (Set<String>) values.getOrDefault(call.getArgument(0), call.getArgument(1));
                return value == null ? null : new HashSet<>(value);
            });
            when(prefs.getAll()).thenAnswer(call -> new HashMap<>(values));
            when(prefs.contains(anyString())).thenAnswer(call -> values.containsKey(call.getArgument(0)));
            when(prefs.edit()).thenReturn(editor);
            when(editor.putString(anyString(), nullable(String.class))).thenAnswer(call -> {
                values.put(call.getArgument(0), call.getArgument(1)); return editor;
            });
            when(editor.putStringSet(anyString(), nullable(Set.class))).thenAnswer(call -> {
                Set<String> value = call.getArgument(1);
                values.put(call.getArgument(0), value == null ? null : new HashSet<>(value)); return editor;
            });
            when(editor.putInt(anyString(), anyInt())).thenAnswer(call -> {
                values.put(call.getArgument(0), call.getArgument(1)); return editor;
            });
            when(editor.putLong(anyString(), anyLong())).thenAnswer(call -> {
                values.put(call.getArgument(0), call.getArgument(1)); return editor;
            });
            when(editor.putBoolean(anyString(), anyBoolean())).thenAnswer(call -> {
                values.put(call.getArgument(0), call.getArgument(1)); return editor;
            });
            when(editor.remove(anyString())).thenAnswer(call -> {
                values.remove(call.getArgument(0)); return editor;
            });
            when(editor.commit()).thenReturn(true);
            return prefs;
        });
    }
}
