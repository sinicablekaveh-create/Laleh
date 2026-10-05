package com.sinicable.telegramelectric;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class MainActivity extends Activity {
    private TelegramClientManager telegram;
    private WordBank wordBank;

    private TextView statusText;
    private TextView countText;
    private TextView wordsText;
    private EditText apiHashInput;
    private EditText phoneInput;
    private EditText authInput;
    private EditText searchInput;
    private EditText addWordInput;
    private Button phoneButton;
    private Button authButton;
    private volatile boolean autoLearnEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wordBank = new WordBank(this);
        telegram = new TelegramClientManager(this, new TelegramClientManager.Listener() {
            @Override
            public void onAuthStep(TelegramClientManager.AuthStep step, String message) {
                runOnUiThread(() -> updateAuthUi(step, message));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    statusText.setText("خطا: " + message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onMessageText(String text) {
                if (!autoLearnEnabled) return;
                int added = wordBank.learnFromMessage(text);
                if (added > 0) {
                    runOnUiThread(() -> {
                        refreshWords(searchInput.getText().toString());
                        countText.setText("بانک واژه: " + wordBank.size() + " واژه — " + added + " واژه جدید یاد گرفته شد");
                    });
                }
            }
        });

        setContentView(buildUi());
        refreshWords("");
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView title = text("Telegram Electric", 26, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text("ورود تلگرام + بانک آفلاین واژه‌های برق", 15, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());

        space(root, 14);
        statusText = text("آماده برای اتصال", 15, true);
        statusText.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(statusText, matchWrap());

        space(root, 18);
        root.addView(text("اتصال تلگرام", 20, true), matchWrap());

        EditText apiIdInput = input("API ID", InputType.TYPE_CLASS_NUMBER);
        apiIdInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(apiIdInput, matchWrap());

        apiHashInput = input("API Hash", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiHashInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(apiHashInput, matchWrap());

        Button connectButton = button("شروع اتصال");
        connectButton.setOnClickListener(v -> {
            String idText = apiIdInput.getText().toString().trim();
            String hash = apiHashInput.getText().toString().trim();
            try {
                telegram.start(Integer.parseInt(idText), hash);
            } catch (NumberFormatException e) {
                statusText.setText("خطا: API ID باید عدد باشد.");
            }
        });
        root.addView(connectButton, matchWrap());

        phoneInput = input("شماره با کد کشور، مثل +994...", InputType.TYPE_CLASS_PHONE);
        phoneInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(phoneInput, matchWrap());

        phoneButton = button("ارسال شماره");
        phoneButton.setEnabled(false);
        phoneButton.setOnClickListener(v -> telegram.submitPhone(phoneInput.getText().toString()));
        root.addView(phoneButton, matchWrap());

        authInput = input("کد ورود / رمز دومرحله‌ای", InputType.TYPE_CLASS_TEXT);
        authInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(authInput, matchWrap());

        authButton = button("ارسال");
        authButton.setEnabled(false);
        authButton.setOnClickListener(v -> telegram.submitAuthValue(authInput.getText().toString()));
        root.addView(authButton, matchWrap());

        space(root, 22);
        root.addView(text("بانک واژه برق", 20, true), matchWrap());

        CheckBox autoLearnCheck = new CheckBox(this);
        autoLearnCheck.setText("یادگیری خودکار از پیام‌های مرتبط تلگرام");
        autoLearnCheck.setChecked(true);
        autoLearnCheck.setOnCheckedChangeListener((buttonView, isChecked) -> autoLearnEnabled = isChecked);
        root.addView(autoLearnCheck, matchWrap());

        countText = text("", 14, true);
        root.addView(countText, matchWrap());

        searchInput = input("جستجوی واژه", InputType.TYPE_CLASS_TEXT);
        root.addView(searchInput, matchWrap());
        searchInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                refreshWords(s.toString());
            }
        });

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        addWordInput = input("افزودن واژه", InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        addRow.addView(addWordInput, inputParams);

        Button addButton = button("افزودن");
        addButton.setOnClickListener(v -> {
            if (wordBank.add(addWordInput.getText().toString())) {
                addWordInput.setText("");
                refreshWords(searchInput.getText().toString());
            } else {
                Toast.makeText(this, "این واژه قبلاً وجود دارد یا معتبر نیست.", Toast.LENGTH_SHORT).show();
            }
        });
        addRow.addView(addButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(addRow, matchWrap());

        wordsText = text("", 15, false);
        wordsText.setTextIsSelectable(true);
        wordsText.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(wordsText, matchWrap());

        space(root, 20);
        root.addView(text(
                "API Hash و شماره تلفن در GitHub قرار نمی‌گیرند. بانک واژه روی همین گوشی ذخیره می‌شود. " +
                        "برای اتصال تلگرام اینترنت لازم است؛ بانک واژه بدون اینترنت هم کار می‌کند.",
                12,
                false
        ), matchWrap());

        return scroll;
    }

    private void updateAuthUi(TelegramClientManager.AuthStep step, String message) {
        statusText.setText(message);
        phoneButton.setEnabled(step == TelegramClientManager.AuthStep.PHONE);

        boolean needsAuth = step == TelegramClientManager.AuthStep.CODE
                || step == TelegramClientManager.AuthStep.PASSWORD
                || step == TelegramClientManager.AuthStep.EMAIL_ADDRESS
                || step == TelegramClientManager.AuthStep.EMAIL_CODE;
        authButton.setEnabled(needsAuth);

        if (step == TelegramClientManager.AuthStep.CODE) {
            authInput.setHint("کد ورود تلگرام");
            authInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if (step == TelegramClientManager.AuthStep.PASSWORD) {
            authInput.setHint("رمز دومرحله‌ای");
            authInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (step == TelegramClientManager.AuthStep.EMAIL_ADDRESS) {
            authInput.setHint("آدرس ایمیل");
            authInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        } else if (step == TelegramClientManager.AuthStep.EMAIL_CODE) {
            authInput.setHint("کد ایمیل");
            authInput.setInputType(InputType.TYPE_CLASS_TEXT);
        }

        if (step == TelegramClientManager.AuthStep.READY) {
            apiHashInput.setText("");
            authInput.setText("");
        }
    }

    private void refreshWords(String query) {
        List<String> list = wordBank.search(query);
        countText.setText("بانک واژه: " + wordBank.size() + " واژه — نمایش " + list.size());

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(list.size(), 300);
        for (int i = 0; i < limit; i++) {
            builder.append("• ").append(list.get(i)).append('\n');
        }
        if (list.size() > limit) {
            builder.append("… و ").append(list.size() - limit).append(" واژه دیگر");
        }
        wordsText.setText(builder.toString());
    }

    private EditText input(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(16);
        field.setInputType(inputType);
        field.setSingleLine(true);
        field.setPadding(dp(10), dp(8), dp(10), dp(8));
        return field;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        return button;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private void space(LinearLayout root, int heightDp) {
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        telegram.close();
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
