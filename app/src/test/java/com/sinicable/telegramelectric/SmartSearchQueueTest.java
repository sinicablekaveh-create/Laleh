package com.sinicable.telegramelectric;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import org.junit.Test;

public class SmartSearchQueueTest {
    @Test
    public void searchCycleSerializesQueueOnlyAfterResultArrives() {
        Context context = mock(Context.class);
        SharedPreferences preferences = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        WordBank words = mock(WordBank.class);

        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        when(preferences.getString(anyString(), anyString())).thenReturn("");
        when(preferences.edit()).thenReturn(editor);
        when(editor.putString(anyString(), anyString())).thenReturn(editor);
        when(words.size()).thenReturn(2);
        when(words.allWords()).thenReturn(List.of("برق", "کابل"));

        SmartSearchQueue queue = new SmartSearchQueue(context, words);
        String query = queue.nextQuery();

        assertNotNull(query);
        verify(preferences, never()).edit();

        queue.recordResult(query, 1, 2);

        verify(preferences).edit();
        verify(editor).putString(anyString(), anyString());
    }
}
