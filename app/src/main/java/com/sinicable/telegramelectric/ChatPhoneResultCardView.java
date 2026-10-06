package com.sinicable.telegramelectric;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * UI card for phone numbers discovered inside Telegram chats.
 * Displays phone, chat source and message time.
 */
public class ChatPhoneResultCardView extends LinearLayout {

    public interface ActionListener {
        void onOpenMessage();
        void onOpenTelegram();
    }

    private final TextView phoneView;
    private final TextView chatView;
    private final TextView timeView;

    public ChatPhoneResultCardView(Context context) {
        super(context);
        setOrientation(VERTICAL);

        phoneView = new TextView(context);
        chatView = new TextView(context);
        timeView = new TextView(context);

        Button messageButton = new Button(context);
        messageButton.setText("مشاهده پیام");

        Button telegramButton = new Button(context);
        telegramButton.setText("باز کردن در Telegram");

        addView(phoneView);
        addView(chatView);
        addView(timeView);
        addView(messageButton);
        addView(telegramButton);
    }

    public void bind(String phone, String chatName, String messageTime, ActionListener listener) {
        phoneView.setText(phone);
        chatView.setText(chatName);
        timeView.setText(messageTime);

        getChildAt(3).setOnClickListener(v -> {
            if (listener != null) listener.onOpenMessage();
        });

        getChildAt(4).setOnClickListener(v -> {
            if (listener != null) listener.onOpenTelegram();
        });
    }
}
