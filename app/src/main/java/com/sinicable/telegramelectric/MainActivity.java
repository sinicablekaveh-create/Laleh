package com.sinicable.telegramelectric;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
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

import java.io.OutputStream;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_FILE = 7001;

    private TelegramClientManager telegram;
    private WordBank wordBank;
    private CentralCorePanel corePanel;
    private AuthSessionStore authSessionStore;

    private TextView statusText;
    private TextView proxyStatusText;
    private TextView internetStatusText;
    private TextView telegramConnectionText;
    private TextView countText;
    private TextView wordsText;
    private EditText apiIdInput;
    private EditText apiHashInput;
    private EditText proxyInput;
    private EditText phoneInput;
    private EditText authInput;
    private EditText searchInput;
    private EditText addWordInput;
    private Button phoneButton;
    private Button authButton;
    private volatile boolean autoLearnEnabled = true;

    private ExportFileWriter.EntityType pendingExportEntity;
    private ExportFileWriter.Format pendingExportFormat;
    private int pendingExportStart;
    private int pendingExportEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wordBank = new WordBank(this);
        authSessionStore = new AuthSessionStore(this);
        telegram = new TelegramClientManager(this, new TelegramClientManager.Listener() {
            @Override
            public void onAuthStep(TelegramClientManager.AuthStep step, String message) {
                runOnUiThread(() -> updateAuthUi(step, message));
            }

            @Override
            public void onProxyStatus(String message) {
                runOnUiThread(() -> proxyStatusText.setText(message));
            }

            @Override
            public void onConnectionStatus(String message, boolean ready) {
                runOnUiThread(() -> telegramConnectionText.setText(message));
            }

            @Override
            public void onRecipientsChanged() {
                runOnUiThread(() -> {
                    if (corePanel != null) corePanel.refreshAll();
                });
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
        checkInternetConnection();

        if (authSessionStore.hasCredentials()) {
            apiIdInput.setText(String.valueOf(authSessionStore.getApiId()));
            statusText.setText("در حال بازیابی نشست ذخیره‌شده تلگرام...");
            telegram.start(authSessionStore.getApiId(), authSessionStore.getApiHash());
        } else {
            telegram.emitCurrentConnectionStatus();
        }
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

        space(root, 14);
        root.addView(text("بررسی اتصال", 20, true), matchWrap());

        internetStatusText = text("اینترنت: در حال بررسی...", 15, true);
        root.addView(internetStatusText, matchWrap());

        telegramConnectionText = text("تلگرام: هنوز شروع نشده است.", 15, true);
        root.addView(telegramConnectionText, matchWrap());

        Button connectionCheckButton = button("بررسی اینترنت و تلگرام");
        connectionCheckButton.setOnClickListener(v -> {
            checkInternetConnection();
            telegram.emitCurrentConnectionStatus();
        });
        root.addView(connectionCheckButton, matchWrap());

        space(root, 18);
        root.addView(text("اتصال تلگرام", 20, true), matchWrap());

        apiIdInput = input("API ID", InputType.TYPE_CLASS_NUMBER);
        apiIdInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(apiIdInput, matchWrap());

        apiHashInput = input("API Hash", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiHashInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(apiHashInput, matchWrap());

        Button connectButton = button("شروع اتصال و ذخیره ورود");
        connectButton.setOnClickListener(v -> {
            String idText = apiIdInput.getText().toString().trim();
            String hash = apiHashInput.getText().toString().trim();
            try {
                int apiId = Integer.parseInt(idText);
                if (hash.isEmpty()) {
                    statusText.setText("خطا: API Hash را وارد کنید.");
                    return;
                }
                authSessionStore.save(apiId, hash);
                telegram.start(apiId, hash);
            } catch (NumberFormatException e) {
                statusText.setText("خطا: API ID باید عدد باشد.");
            }
        });
        root.addView(connectButton, matchWrap());

        Button forgetLoginButton = button("حذف ورود خودکار ذخیره‌شده");
        forgetLoginButton.setOnClickListener(v -> {
            authSessionStore.clear();
            apiHashInput.setText("");
            Toast.makeText(
                    this,
                    "ورود خودکار حذف شد. نشست فعلی تلگرام تا وقتی خودت خارج نشوی باقی می‌ماند.",
                    Toast.LENGTH_LONG
            ).show();
        });
        root.addView(forgetLoginButton, matchWrap());

        space(root, 10);
        root.addView(text("پروکسی تلگرام (اختیاری)", 18, true), matchWrap());

        proxyInput = input(
                "لینک MTProto یا SOCKS5 را اینجا پیست کنید",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
        );
        proxyInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(proxyInput, matchWrap());

        LinearLayout proxyRow = new LinearLayout(this);
        proxyRow.setOrientation(LinearLayout.HORIZONTAL);
        proxyRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button pasteProxyButton = button("پیست و فعال‌سازی");
        pasteProxyButton.setOnClickListener(v -> pasteAndApplyProxy());
        proxyRow.addView(pasteProxyButton, weightedButton());

        Button applyProxyButton = button("فعال‌سازی");
        applyProxyButton.setOnClickListener(v -> applyProxyFromField());
        proxyRow.addView(applyProxyButton, weightedButton());

        root.addView(proxyRow, matchWrap());

        proxyStatusText = text(
                "پشتیبانی از لینک‌های tg://proxy ، t.me/proxy ، tg://socks و t.me/socks",
                12,
                false
        );
        proxyStatusText.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.addView(proxyStatusText, matchWrap());

        space(root, 10);
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
        corePanel = new CentralCorePanel(this, telegram, wordBank);
        corePanel.setExportRequestListener(
                (entityType, format, startNumber, endNumber) ->
                        startExport(entityType, format, startNumber, endNumber)
        );
        root.addView(corePanel, matchWrap());

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
                "برای ورود خودکار، API ID و API Hash فقط در فضای خصوصی همین برنامه روی گوشی ذخیره می‌شوند و داخل GitHub قرار نمی‌گیرند. " +
                        "شماره، کد ورود و رمز دومرحله‌ای توسط این بخش ذخیره نمی‌شوند؛ نشست تلگرام را TDLib نگه می‌دارد. " +
                        "بانک واژه و بسته ۱۰۰۰ عبارت فارسی برق ایران هم روی همین گوشی ذخیره می‌شوند.",
                12,
                false
        ), matchWrap());

        return scroll;
    }


    private void startExport(
            ExportFileWriter.EntityType entityType,
            ExportFileWriter.Format format,
            int startNumber,
            int endNumber
    ) {
        pendingExportEntity = entityType;
        pendingExportFormat = format;
        pendingExportStart = startNumber;
        pendingExportEnd = endNumber;

        String kind = entityType == ExportFileWriter.EntityType.CONTACTS
                ? "contacts"
                : "groups";
        String extension = format == ExportFileWriter.Format.XLSX ? "xlsx" : "txt";
        String mime = format == ExportFileWriter.Format.XLSX
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/plain";

        String fileName = "telegram-electric-" + kind + "-"
                + startNumber + "-" + endNumber + "." + extension;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, REQUEST_EXPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_EXPORT_FILE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null || pendingExportEntity == null || pendingExportFormat == null) {
            return;
        }

        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IllegalStateException("فایل خروجی باز نشد.");
            }

            ExportFileWriter.write(
                    output,
                    telegram,
                    pendingExportEntity,
                    pendingExportFormat,
                    pendingExportStart,
                    pendingExportEnd
            );

            Toast.makeText(
                    this,
                    "فایل با موفقیت ذخیره شد.",
                    Toast.LENGTH_LONG
            ).show();
        } catch (Throwable error) {
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = error.getClass().getSimpleName();
            }

            Toast.makeText(
                    this,
                    "ذخیره فایل ناموفق بود: " + message,
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            pendingExportEntity = null;
            pendingExportFormat = null;
            pendingExportStart = 0;
            pendingExportEnd = 0;
        }
    }

    private void checkInternetConnection() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (manager == null) {
            internetStatusText.setText("اینترنت: امکان بررسی وجود ندارد.");
            return;
        }

        Network network = manager.getActiveNetwork();
        if (network == null) {
            internetStatusText.setText("اینترنت: قطع ⛔");
            return;
        }

        NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null) {
            internetStatusText.setText("اینترنت: وضعیت نامشخص");
            return;
        }

        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        if (hasInternet && validated) {
            String type = connectionType(caps);
            internetStatusText.setText("اینترنت: متصل ✅" + (type.isEmpty() ? "" : " — " + type));
        } else if (hasInternet) {
            internetStatusText.setText("اینترنت: شبکه متصل است ولی دسترسی اینترنت تأیید نشده ⚠️");
        } else {
            internetStatusText.setText("اینترنت: قطع ⛔");
        }
    }

    private String connectionType(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi‑Fi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "دیتای موبایل";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
        return "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (internetStatusText != null) {
            checkInternetConnection();
        }
        if (telegram != null) {
            telegram.emitCurrentConnectionStatus();
        }
        if (corePanel != null) {
            corePanel.refreshAll();
        }
    }

    private void pasteAndApplyProxy() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "کلیپ‌بورد خالی است.", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(this, "کلیپ‌بورد خالی است.", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence value = clip.getItemAt(0).coerceToText(this);
        if (value == null || value.toString().trim().isEmpty()) {
            Toast.makeText(this, "متن قابل استفاده‌ای در کلیپ‌بورد نیست.", Toast.LENGTH_SHORT).show();
            return;
        }

        proxyInput.setText(value.toString().trim());
        proxyInput.setSelection(proxyInput.getText().length());
        applyProxyFromField();
    }

    private void applyProxyFromField() {
        String link = proxyInput.getText().toString().trim();
        if (link.isEmpty()) {
            Toast.makeText(this, "لینک پروکسی را پیست کنید.", Toast.LENGTH_SHORT).show();
            return;
        }
        telegram.setProxyFromLink(link);
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

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
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
        if (corePanel != null) corePanel.shutdown();
        telegram.close();
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
