package com.sinicable.telegramelectric;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public final class MainActivity extends Activity {
    private static final int REQUEST_EXPORT_FILE = 7001;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 7002;
    private static final int REQUEST_PICK_MESSAGE_PHOTO = 7003;

    private TelegramClientManager telegram;
    private WordBank wordBank;
    private CentralCorePanel corePanel;
    private CentralCore sharedCore;
    private AuthSessionStore authSessionStore;
    private BackgroundModeStore backgroundModeStore;

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
    private Button connectButton;
    private Button phoneButton;
    private Button authButton;
    private CheckBox backgroundRunCheck;
    private volatile boolean autoLearnEnabled = true;
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private long latestPhotoRequest;

    private ExportFileWriter.EntityType pendingExportEntity;
    private ExportFileWriter.Format pendingExportFormat;
    private int pendingExportStart;
    private int pendingExportEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authSessionStore = new AuthSessionStore(this);
        backgroundModeStore = new BackgroundModeStore(this);

        if (savedInstanceState != null) {
            String entity = savedInstanceState.getString("pending_export_entity");
            String format = savedInstanceState.getString("pending_export_format");
            if (entity != null && format != null) {
                try {
                    pendingExportEntity = ExportFileWriter.EntityType.valueOf(entity);
                    pendingExportFormat = ExportFileWriter.Format.valueOf(format);
                    pendingExportStart = savedInstanceState.getInt("pending_export_start");
                    pendingExportEnd = savedInstanceState.getInt("pending_export_end");
                } catch (IllegalArgumentException ignored) {
                    pendingExportEntity = null;
                    pendingExportFormat = null;
                }
            }
        }

        TelegramClientManager.Listener uiListener = createTelegramUiListener();
        BackgroundRuntime.Snapshot runtime = BackgroundRuntime.get();

        if (runtime != null) {
            wordBank = runtime.wordBank;
            telegram = runtime.telegram;
            sharedCore = runtime.core;
            telegram.setListener(uiListener);
        } else {
            wordBank = new WordBank(this);
            telegram = new TelegramClientManager(this, uiListener);
        }

        setContentView(buildUi());
        refreshWords("");
        checkInternetConnection();

        if (BackgroundRuntime.get() == null) {
            if (authSessionStore.hasCredentials()) {
                apiIdInput.setText(String.valueOf(authSessionStore.getApiId()));
                statusText.setText("در حال بازیابی نشست ذخیره‌شده تلگرام...");
                telegram.start(authSessionStore.getApiId(), authSessionStore.getApiHash());
            } else {
                telegram.emitCurrentConnectionStatus();
            }
        } else {
            if (authSessionStore.hasCredentials()) {
                apiIdInput.setText(String.valueOf(authSessionStore.getApiId()));
            }
            telegram.emitCurrentConnectionStatus();
        }

        if (backgroundModeStore.isEnabled()) {
            BackgroundRuntime.attach(telegram, wordBank, corePanel.getCore());
            BackgroundCoreService.start(this);
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

        ImageView appLogo = new ImageView(this);
        appLogo.setImageResource(R.drawable.app_logo);
        appLogo.setAdjustViewBounds(true);
        appLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(230)
        );
        logoParams.setMargins(0, 0, 0, dp(8));
        root.addView(appLogo, logoParams);

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

        connectButton = button("شروع اتصال و ذخیره ورود");
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
                connectButton.setEnabled(false);
                statusText.setText("در حال راه‌اندازی تلگرام...");
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
        corePanel = new CentralCorePanel(this, telegram, wordBank, sharedCore);
        corePanel.setExportRequestListener(
                (entityType, format, startNumber, endNumber) ->
                        startExport(entityType, format, startNumber, endNumber)
        );
        corePanel.setPhotoRequestListener(this::chooseMessagePhoto);
        root.addView(corePanel, matchWrap());

        backgroundRunCheck = new CheckBox(this);
        backgroundRunCheck.setText("اجرا در پس‌زمینه");
        backgroundRunCheck.setChecked(backgroundModeStore.isEnabled());
        backgroundRunCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backgroundModeStore.setEnabled(isChecked);

            if (isChecked) {
                ensureNotificationPermission();
                BackgroundRuntime.attach(telegram, wordBank, corePanel.getCore());
                BackgroundCoreService.start(this);
                Toast.makeText(
                        this,
                        "اجرای پس‌زمینه فعال شد. اعلان دائمی برنامه نمایش داده می‌شود.",
                        Toast.LENGTH_LONG
                ).show();
            } else {
                BackgroundCoreService.stop(this);
                BackgroundRuntime.clear();
                Toast.makeText(
                        this,
                        "اجرای پس‌زمینه غیرفعال شد.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        root.addView(backgroundRunCheck, matchWrap());

        root.addView(text(
                "وقتی این گزینه روشن باشد، هسته مرکزی با Foreground Service و اعلان دائمی در پس‌زمینه فعال می‌ماند. بعضی گوشی‌ها ممکن است برای برنامه محدودیت باتری جداگانه اعمال کنند.",
                12,
                false
        ), matchWrap());

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


    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION
            );
        }
    }

    private TelegramClientManager.Listener createTelegramUiListener() {
        return new TelegramClientManager.Listener() {
            @Override
            public void onAuthStep(TelegramClientManager.AuthStep step, String message) {
                runOnUiThread(() -> updateAuthUi(step, message));
            }

            @Override
            public void onProxyStatus(String message) {
                runOnUiThread(() -> {
                    if (proxyStatusText != null) proxyStatusText.setText(message);
                });
            }

            @Override
            public void onConnectionStatus(String message, boolean ready) {
                runOnUiThread(() -> {
                    if (telegramConnectionText != null) telegramConnectionText.setText(message);
                });
            }

            @Override
            public void onTargetGroupChanged(long chatId) {
                runOnUiThread(() -> {
                    if (corePanel != null) corePanel.onTargetGroupChanged(chatId);
                });
            }

            @Override
            public void onFoundGroupsChanged() {
                runOnUiThread(() -> {
                    if (corePanel != null) corePanel.onFoundGroupsChanged();
                });
            }

            @Override
            public void onObservedUsersChanged() {
                runOnUiThread(() -> {
                    if (corePanel != null) corePanel.onObservedUsersChanged();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (statusText != null) statusText.setText("خطا: " + message);
                    if (connectButton != null) connectButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onMessageText(String text) {
                if (!autoLearnEnabled) return;
                int added = wordBank.learnFromMessage(text);
                if (added > 0) {
                    runOnUiThread(() -> {
                        if (searchInput != null) {
                            refreshWords(searchInput.getText().toString());
                        }
                        if (countText != null) {
                            countText.setText(
                                    "بانک واژه: " + wordBank.size()
                                            + " واژه — " + added + " واژه جدید یاد گرفته شد"
                            );
                        }
                    });
                }
            }
        };
    }

    private void detachUiListenersForBackground() {
        telegram.setListener(new TelegramClientManager.Listener() {
            @Override
            public void onAuthStep(TelegramClientManager.AuthStep step, String message) {
            }

            @Override
            public void onProxyStatus(String message) {
            }

            @Override
            public void onConnectionStatus(String message, boolean ready) {
            }

            @Override
            public void onTargetGroupChanged(long chatId) {
            }

            @Override
            public void onFoundGroupsChanged() {
            }

            @Override
            public void onObservedUsersChanged() {
            }

            @Override
            public void onError(String message) {
            }

            @Override
            public void onMessageText(String text) {
                wordBank.learnFromMessage(text);
            }
        });

        corePanel.getCore().setListener(new CentralCore.Listener() {
            @Override
            public void onStatus(String message) {
            }

            @Override
            public void onDataChanged() {
            }
        });
    }

    private void chooseMessagePhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_MESSAGE_PHOTO);
    }

    private void handleSelectedMessagePhoto(Uri uri) {
        if (uri == null || corePanel == null) return;
        final Context appContext = getApplicationContext();
        final CentralCore core = corePanel.getCore();
        final long request = ++latestPhotoRequest;
        final long photoRevision = core.getPhotoRevision();
        Toast.makeText(this, "در حال ذخیره عکس...", Toast.LENGTH_SHORT).show();
        fileExecutor.execute(() -> {
            String stagedPath = null;
            boolean selected = false;
            try {
                stagedPath = PhotoMessageStore.copyIntoApp(appContext, uri);
                final String savedPath = stagedPath;
                FutureTask<String> selection = new FutureTask<>(() -> {
                    if (isFinishing() || isDestroyed()
                            || request != latestPhotoRequest
                            || core.getPhotoRevision() != photoRevision) return null;
                    String previousPath = core.getPhotoPath();
                    core.setPhotoPath(savedPath);
                    return previousPath;
                });
                runOnUiThread(selection);
                String previousPath = selection.get();
                if (previousPath == null) return;
                selected = true;
                if (!previousPath.isEmpty() && !previousPath.equals(savedPath)) {
                    new File(previousPath).delete();
                }
                showFileResult("عکس پیام ذخیره شد و همراه متن ارسال می‌شود.");
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                showFileResult("ذخیره عکس ناموفق بود: " + fileErrorMessage(error));
            } finally {
                if (!selected && stagedPath != null && !stagedPath.equals(core.getPhotoPath())) {
                    new File(stagedPath).delete();
                }
            }
        });
    }

    private void showFileResult(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private static String fileErrorMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
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

        if (requestCode == REQUEST_PICK_MESSAGE_PHOTO) {
            if (resultCode == RESULT_OK && data != null) {
                handleSelectedMessagePhoto(data.getData());
            }
            return;
        }

        if (requestCode != REQUEST_EXPORT_FILE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null || pendingExportEntity == null || pendingExportFormat == null) {
            return;
        }

        final ExportFileWriter.EntityType entity = pendingExportEntity;
        final ExportFileWriter.Format format = pendingExportFormat;
        final int start = pendingExportStart;
        final int end = pendingExportEnd;
        final TelegramClientManager source = telegram;
        final Context appContext = getApplicationContext();
        pendingExportEntity = null;
        pendingExportFormat = null;
        pendingExportStart = 0;
        pendingExportEnd = 0;
        Toast.makeText(this, "در حال ذخیره فایل...", Toast.LENGTH_SHORT).show();
        fileExecutor.execute(() -> {
            try (OutputStream output = appContext.getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("فایل خروجی باز نشد.");
                ExportFileWriter.write(output, source, entity, format, start, end);
            } catch (Exception error) {
                showFileResult("ذخیره فایل ناموفق بود: " + fileErrorMessage(error));
                return;
            }
            showFileResult("فایل با موفقیت ذخیره شد.");
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (pendingExportEntity != null && pendingExportFormat != null) {
            state.putString("pending_export_entity", pendingExportEntity.name());
            state.putString("pending_export_format", pendingExportFormat.name());
            state.putInt("pending_export_start", pendingExportStart);
            state.putInt("pending_export_end", pendingExportEnd);
        }
        super.onSaveInstanceState(state);
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
        if (connectButton != null) {
            connectButton.setEnabled(step != TelegramClientManager.AuthStep.WAIT_PARAMETERS);
        }
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
        fileExecutor.shutdown();
        if (backgroundModeStore != null
                && backgroundModeStore.isEnabled()
                && corePanel != null
                && telegram != null
                && wordBank != null) {
            detachUiListenersForBackground();
            BackgroundRuntime.attach(telegram, wordBank, corePanel.getCore());
        } else {
            if (corePanel != null) corePanel.shutdown();
            if (telegram != null) telegram.close();
            BackgroundRuntime.clear();
        }
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
