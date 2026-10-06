package com.sinicable.telegramelectric;

import android.content.Context;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PhoneSearchCompatibilityTest {
    @Test public void adapterFlattensAllSourcesAndBindsTheSelectedCard() throws Exception {
        ChatPhoneResultAdapter adapter = mock(ChatPhoneResultAdapter.class, CALLS_REAL_METHODS);
        Field items = ChatPhoneResultAdapter.class.getDeclaredField("items");
        items.setAccessible(true);
        items.set(adapter, new ArrayList<ChatPhoneSourceLocator>());
        doNothing().when(adapter).notifyDataSetChanged();
        ChatPhoneResultViewModel model = new ChatPhoneResultViewModel();
        model.addResult(new ChatPhoneSourceLocator("09123456789", -1, 10, "اول"));
        model.addResult(new ChatPhoneSourceLocator("09223456789", -2, 20, "دوم"));
        adapter.update(List.of(model));
        assertEquals(2, adapter.getCount());
        ChatPhoneResultCardView card = mock(ChatPhoneResultCardView.class);
        assertSame(card, adapter.getView(1, card, null));
        verify(card).bind(eq("09223456789"), eq("دوم"), eq(""), any(ChatPhoneResultCardView.ActionListener.class));
        adapter.update(null);
        assertEquals(0, adapter.getCount());
    }
}
