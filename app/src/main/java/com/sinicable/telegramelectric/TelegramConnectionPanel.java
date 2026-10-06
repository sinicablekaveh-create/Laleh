package com.sinicable.telegramelectric;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/** UI component for choosing the Telegram application used for opening results. */
public final class TelegramConnectionPanel extends LinearLayout {

    public TelegramConnectionPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);

        TelegramConnectionSettings settings = new TelegramConnectionSettings(context);

        Button choose = new Button(context);
        choose.setText("انتخاب برنامه Telegram مقصد");
        choose.setOnClickListener(v -> {
            List<String> apps = TelegramAppConnector.getInstalledTelegramApps(context);

            if (apps.isEmpty()) {
                Toast.makeText(context, "Telegram نصب نشده است", Toast.LENGTH_SHORT).show();
                return;
            }

            String selected = apps.get(0);
            settings.setSelectedPackage(selected);
            Toast.makeText(context,
                    "Telegram انتخاب شد: " + selected,
                    Toast.LENGTH_LONG).show();
        });

        addView(choose, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
    }
}
