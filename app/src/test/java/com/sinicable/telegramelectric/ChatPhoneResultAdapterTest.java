package com.sinicable.telegramelectric;

import android.content.Context;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ChatPhoneResultAdapterTest {
    private Context context;
    private ChatPhoneResultAdapter adapter;

    @Before
    public void setUp() throws Exception {
        context = mock(Context.class);
        // Bypass the Android BaseAdapter constructor, while exercising real adapter methods.
        adapter = mock(ChatPhoneResultAdapter.class, CALLS_REAL_METHODS);
        setField("context", context);
        setField("items", new ArrayList<ChatPhoneSourceLocator>());
        doNothing().when(adapter).notifyDataSetChanged();
    }

    @Test
    public void repeatedSourceObjectsRemainSeparateRowsWithinAndAcrossModels() {
        ChatPhoneSourceLocator item = source("+12025550100", 11L, "09:00");

        adapter.update(Arrays.asList(model(item, item), model(item)));

        assertEquals(3, adapter.getCount());
        for (int position = 0; position < 3; position++) {
            assertSame(item, adapter.getItem(position));
        }
        verify(adapter).notifyDataSetChanged();
    }

    @Test
    public void boundActionsKeepOriginalSourceAfterRowsAreReplacedAndCleared() {
        ChatPhoneSourceLocator original = source("+12025550100", 11L, "09:00");
        adapter.update(Collections.singletonList(model(original)));
        ChatPhoneResultCardView card = mock(ChatPhoneResultCardView.class);
        adapter.getView(0, card, null);
        ArgumentCaptor<ChatPhoneResultCardView.ActionListener> listener =
                ArgumentCaptor.forClass(ChatPhoneResultCardView.ActionListener.class);
        verify(card).bind(eq(original.getPhone()), eq(original.getChatTitle()),
                eq(original.getMessageTime()), listener.capture());

        try (MockedStatic<TelegramMessageSourceOpener> messages = mockStatic(TelegramMessageSourceOpener.class);
             MockedStatic<TelegramResultOpener> phones = mockStatic(TelegramResultOpener.class)) {
            adapter.update(Collections.singletonList(model(source("+12025550101", 22L, "10:00"))));
            listener.getValue().onOpenMessage();
            adapter.update(null);
            listener.getValue().onOpenTelegram();

            messages.verify(() -> TelegramMessageSourceOpener.openMessage(
                    context, original.getChatId(), original.getMessageId()));
            phones.verify(() -> TelegramResultOpener.openPhone(context, original.getPhone()));
            messages.verifyNoMoreInteractions();
            phones.verifyNoMoreInteractions();
        }
    }

    @Test
    public void removedRowCannotRebindARecycledCard() {
        adapter.update(Collections.singletonList(model(source("+12025550100", 11L, "09:00"))));
        adapter.update(null);
        ChatPhoneResultCardView card = mock(ChatPhoneResultCardView.class);

        assertThrows(IndexOutOfBoundsException.class, () -> adapter.getView(0, card, null));

        verifyNoInteractions(card);
    }

    @Test
    public void updateFlattensModelsInOrderAndSkipsNullAndEmptyModels() {
        ChatPhoneSourceLocator first = source("+12025550100", 11L, "09:00");
        ChatPhoneSourceLocator second = source("+12025550101", 22L, "10:00");
        ChatPhoneSourceLocator third = source("+12025550102", 33L, "11:00");

        adapter.update(Arrays.asList(null, model(first, second), model(), null, model(third)));

        assertEquals(3, adapter.getCount());
        assertSame(first, adapter.getItem(0));
        assertSame(second, adapter.getItem(1));
        assertSame(third, adapter.getItem(2));
        verify(adapter).notifyDataSetChanged();
    }

    @Test
    public void samePhoneFromDifferentMessagesRetainsBothSources() {
        ChatPhoneSourceLocator first = source("+12025550100", 11L, "09:00");
        ChatPhoneSourceLocator second = source("+12025550100", 22L, "10:00");

        adapter.update(Arrays.asList(model(first), model(second)));

        assertEquals(2, adapter.getCount());
        assertSame(first, adapter.getItem(0));
        assertSame(second, adapter.getItem(1));
    }

    @Test
    public void updateReplacesOldRowsAndNotifiesOncePerUpdate() {
        ChatPhoneSourceLocator replacement = source("+12025550102", 33L, "11:00");
        adapter.update(Collections.singletonList(model(
                source("+12025550100", 11L, "09:00"),
                source("+12025550101", 22L, "10:00"))));

        adapter.update(Collections.singletonList(model(replacement)));

        assertEquals(1, adapter.getCount());
        assertSame(replacement, adapter.getItem(0));
        verify(adapter, times(2)).notifyDataSetChanged();
    }

    @Test
    public void nullEmptyAndNullOnlyUpdatesClearPreviousRows() {
        ChatPhoneResultViewModel result = model(source("+12025550100", 11L, "09:00"));
        adapter.update(Collections.singletonList(result));
        adapter.update(null);
        assertEquals(0, adapter.getCount());

        adapter.update(Collections.singletonList(result));
        adapter.update(Collections.emptyList());
        assertEquals(0, adapter.getCount());

        adapter.update(Collections.singletonList(result));
        adapter.update(Arrays.asList(null, model(), null));
        assertEquals(0, adapter.getCount());
        verify(adapter, times(6)).notifyDataSetChanged();
    }

    @Test
    public void sourceModelChangesDoNotChangeRowsUntilNextUpdate() {
        ChatPhoneSourceLocator first = source("+12025550100", 11L, "09:00");
        ChatPhoneSourceLocator second = source("+12025550101", 22L, "10:00");
        ChatPhoneResultViewModel result = model(first);
        adapter.update(Collections.singletonList(result));

        result.clear();
        result.addResult(second);

        assertEquals(1, adapter.getCount());
        assertSame(first, adapter.getItem(0));
        adapter.update(Collections.singletonList(result));
        assertSame(second, adapter.getItem(0));
    }

    @Test
    public void recycledCardBindsSelectedRowAndActionsUseItsSource() {
        ChatPhoneSourceLocator first = source("+12025550100", 11L, "09:00");
        ChatPhoneSourceLocator second = source("+12025550101", 22L, "10:00");
        adapter.update(Collections.singletonList(model(first, second)));
        ChatPhoneResultCardView card = mock(ChatPhoneResultCardView.class);

        assertSame(card, adapter.getView(0, card, null));
        assertSame(card, adapter.getView(1, card, null));

        ArgumentCaptor<ChatPhoneResultCardView.ActionListener> listener =
                ArgumentCaptor.forClass(ChatPhoneResultCardView.ActionListener.class);
        verify(card).bind(eq(second.getPhone()), eq(second.getChatTitle()),
                eq("10:00"), listener.capture());

        try (MockedStatic<TelegramMessageSourceOpener> messages = mockStatic(TelegramMessageSourceOpener.class);
             MockedStatic<TelegramResultOpener> phones = mockStatic(TelegramResultOpener.class)) {
            listener.getValue().onOpenMessage();
            messages.verify(() -> TelegramMessageSourceOpener.openMessage(
                    context, second.getChatId(), second.getMessageId()));
            phones.verifyNoInteractions();

            listener.getValue().onOpenTelegram();
            phones.verify(() -> TelegramResultOpener.openPhone(context, second.getPhone()));
            messages.verifyNoMoreInteractions();
            phones.verifyNoMoreInteractions();
        }
    }

    @Test
    public void missingOrIncompatibleRecycledViewCreatesAndBindsCard() {
        ChatPhoneSourceLocator item = source("+12025550100", 11L, null);
        adapter.update(Collections.singletonList(model(item)));

        try (MockedConstruction<ChatPhoneResultCardView> cards = mockConstruction(
                ChatPhoneResultCardView.class,
                (card, construction) -> assertEquals(Collections.singletonList(context), construction.arguments()))) {
            View first = adapter.getView(0, null, null);
            View second = adapter.getView(0, mock(View.class), null);

            assertEquals(2, cards.constructed().size());
            assertSame(cards.constructed().get(0), first);
            assertSame(cards.constructed().get(1), second);
            for (ChatPhoneResultCardView card : cards.constructed()) {
                verify(card).bind(eq(item.getPhone()), eq(item.getChatTitle()), eq(""),
                        any(ChatPhoneResultCardView.ActionListener.class));
            }
        }
    }

    private ChatPhoneSourceLocator source(String phone, long messageId, String time) {
        return new ChatPhoneSourceLocator(phone, -1_000_000_000_000L - messageId,
                messageId, "Source " + messageId, time);
    }

    private ChatPhoneResultViewModel model(ChatPhoneSourceLocator... sources) {
        ChatPhoneResultViewModel model = new ChatPhoneResultViewModel();
        for (ChatPhoneSourceLocator source : sources) {
            model.addResult(source);
        }
        return model;
    }

    private void setField(String name, Object value) throws Exception {
        Field field = ChatPhoneResultAdapter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(adapter, value);
    }
}
