package com.sinicable.telegramelectric;

import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class WordBankUiTest {
    private MainActivity activity;
    private CentralCore core;
    private TelegramClientManager telegram;
    private EditText stages;
    private EditText minimum;
    private TextView feedback;

    @Before
    public void setUp() throws Exception {
        activity = mock(MainActivity.class, CALLS_REAL_METHODS);
        core = mock(CentralCore.class);
        telegram = mock(TelegramClientManager.class);
        CentralCorePanel panel = mock(CentralCorePanel.class);
        when(panel.getCore()).thenReturn(core);
        setField("corePanel", panel);
        setField("telegram", telegram);
        stages = mock(EditText.class);
        minimum = mock(EditText.class);
        feedback = mock(TextView.class);
        setField("searchStagesInput", stages);
        setField("searchMinimumInput", minimum);
        setField("wordFeedbackText", feedback);
        doReturn(false).when(activity).isFinishing();
        doReturn(false).when(activity).isDestroyed();
    }

    @Test
    public void searchConfigurationAcceptsPersianAndArabicDigits() throws Exception {
        input(stages, "۵");
        input(minimum, "٣");

        assertEquals(Boolean.TRUE, invoke("saveWordSearchConfig"));
        verify(core).setSearchConfig(5, 3);
    }

    @Test
    public void tooShortMinimumIsVisibleAndDoesNotChangeSearchConfiguration() throws Exception {
        input(stages, "5");
        input(minimum, "1");

        assertEquals(Boolean.FALSE, invoke("saveWordSearchConfig"));
        verify(core, never()).setSearchConfig(anyInt(), anyInt());
        verify(feedback).setText(contains("۲ تا ۹۶"));
    }

    @Test
    public void persistedHistoryShowsNewestFirstWithResultsAndErrors() throws Exception {
        TextView history = mock(TextView.class);
        installHistoryViews(history);
        when(core.getSearchHistoryCount()).thenReturn(41L);
        when(core.getWordSearchStatus()).thenReturn("جستجو در حال اجراست.");
        when(core.getSearchHistory(0, 20)).thenReturn(List.of(
                new WordSearchResult("جدیدترین واژه", "جدیدترین جستجو", 2, 2000L,
                        false, 0, 0, "Telegram 429: FLOOD_WAIT_30", List.of()),
                new WordSearchResult("واژه قدیمی", "جستجوی قدیمی", 1, 1000L,
                        true, 1, 1, "", List.of(42L))
        ));
        when(telegram.getFoundGroups()).thenReturn(List.of(
                new TelegramClientManager.GroupInfo(42L, "گروه نتیجه", "https://t.me/result", 20,
                        "عمومی", false)
        ));

        invoke("refreshWordSearch");
        ArgumentCaptor<CharSequence> rendered = ArgumentCaptor.forClass(CharSequence.class);
        verify(history).setText(rendered.capture());
        String text = rendered.getValue().toString();
        assertTrue(text.indexOf("جدیدترین واژه") < text.indexOf("واژه قدیمی"));
        assertTrue(text.contains("Telegram 429: FLOOD_WAIT_30"));
        assertTrue(text.contains("گروه نتیجه"));
        assertTrue(text.contains("https://t.me/result"));
        verify(core).getSearchHistory(0, 20);
        verify(core, never()).getSearchHistory();
    }

    @Test
    public void olderHistoryPageUsesOffsetAndRemainsAccessible() throws Exception {
        TextView history = mock(TextView.class);
        installHistoryViews(history);
        setField("searchHistoryPage", 1);
        when(core.getSearchHistoryCount()).thenReturn(41L);
        when(core.getSearchHistory(20, 20)).thenReturn(List.of());

        invoke("refreshWordSearch");
        verify(core).getSearchHistory(20, 20);
        verify(core, never()).getSearchHistory();
    }

    private void installHistoryViews(TextView history) throws Exception {
        setField("wordSearchStatusText", mock(TextView.class));
        setField("wordSearchHistoryText", history);
        setField("wordSearchStartButton", mock(Button.class));
        setField("wordSearchStopButton", mock(Button.class));
        setField("previousHistoryPageButton", mock(Button.class));
        setField("nextHistoryPageButton", mock(Button.class));
        setField("searchHistoryPageText", mock(TextView.class));
    }

    private void input(EditText view, String value) {
        Editable editable = mock(Editable.class);
        when(editable.toString()).thenReturn(value);
        when(view.getText()).thenReturn(editable);
    }

    private Object invoke(String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(activity);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }
}
