package com.hooshikadev.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 210;
    private static final int NOTIFICATION_PERMISSION = 211;
    private static final String RUBIKA_PACKAGE = "ir.resaneh1.iptv";

    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private AppRepository repository;
    private SendQueue queue;
    private TextView summary;
    private TextView contactsText;
    private TextView groupsText;
    private TextView queueText;
    private EditText searchInput;
    private EditText groupTitleInput;
    private EditText groupUrlInput;
    private EditText messageInput;
    private EditText intervalInput;
    private CheckBox consentCheck;
    private List<String> plannedQueries = new ArrayList<>();
    private int queryIndex;
    private boolean awaitingSendConfirmation;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new AppRepository(this);
        queue = new SendQueue(this);
        setContentView(buildUi());
        refresh();
        requestNotificationsIfNeeded();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshQueue();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(22), dp(16), dp(32));
        root.setBackgroundColor(Color.rgb(245, 243, 255));
        scroll.addView(root);

        TextView title = text("hooshikadev", 30, true, Color.rgb(76, 29, 149));
        title.setGravity(Gravity.CENTER);
        root.addView(title, match());
        TextView subtitle = text("هستهٔ هوشمند جذب مخاطب و گروه‌های عمومی روبیکا", 15, false, Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, match());

        summary = text("", 14, true, Color.WHITE);
        summary.setPadding(dp(14), dp(14), dp(14), dp(14));
        summary.setBackground(rounded(Color.rgb(76, 29, 149), 16));
        LinearLayout.LayoutParams summaryParams = match();
        summaryParams.setMargins(0, dp(16), 0, dp(12));
        root.addView(summary, summaryParams);

        addContactSection(root);
        addGroupSection(root);
        addAiSection(root);
        addSendSection(root);
        return scroll;
    }

    private void addContactSection(LinearLayout root) {
        LinearLayout card = card("۱. جستجوگر و بانک مخاطبان");
        card.addView(text("فایل CSV یا XLSX را انتخاب کنید. ستون‌های name/نام، phone/شماره و consent/رضایت خودکار شناسایی می‌شوند.", 13, false, Color.DKGRAY), match());
        Button importButton = button("انتخاب فایل اکسل یا CSV", Color.rgb(109, 40, 217));
        importButton.setOnClickListener(v -> chooseFile());
        card.addView(importButton, matchWithTop(10));
        contactsText = text("", 13, false, Color.rgb(31, 41, 55));
        card.addView(contactsText, matchWithTop(10));
        root.addView(card, cardParams());
    }

    private void addGroupSection(LinearLayout root) {
        LinearLayout card = card("۲. جستجوگر چندموتورهٔ گروه‌های عمومی");
        searchInput = input("موضوع دلخواه؛ مثال: برق صنعتی تهران", false);
        card.addView(searchInput, match());
        Button searchButton = button("ساخت و اجرای جستجوی بعدی", Color.rgb(8, 145, 178));
        searchButton.setOnClickListener(v -> openNextSearch());
        card.addView(searchButton, matchWithTop(8));
        card.addView(text("نتیجهٔ عمومی مناسب را بررسی کنید، سپس لینک rubika.ir آن را در پایین ذخیره کنید.", 12, false, Color.GRAY), matchWithTop(8));
        groupTitleInput = input("عنوان گروه یا کانال", false);
        groupUrlInput = input("https://rubika.ir/username", false);
        groupUrlInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(groupTitleInput, matchWithTop(8));
        card.addView(groupUrlInput, matchWithTop(6));
        Button saveGroup = button("ذخیره و امتیازدهی هوشمند", Color.rgb(8, 145, 178));
        saveGroup.setOnClickListener(v -> saveGroup());
        card.addView(saveGroup, matchWithTop(8));
        groupsText = text("", 13, false, Color.rgb(31, 41, 55));
        card.addView(groupsText, matchWithTop(10));
        root.addView(card, cardParams());
    }

    private void addAiSection(LinearLayout root) {
        LinearLayout card = card("۳. هوش هماهنگ‌ساز آفلاین + جستجوی آنلاین");
        card.addView(text("امتیازدهی روی گوشی و بدون کلید پولی انجام می‌شود. بخش آنلاین فقط عبارت‌های هدفمند را در موتور جستجوی نصب‌شده باز می‌کند و اطلاعات خصوصی را جمع‌آوری نمی‌کند.", 13, false, Color.DKGRAY), match());
        Button sync = button("همگام‌سازی چهار هسته", Color.rgb(5, 150, 105));
        sync.setOnClickListener(v -> {
            plannedQueries = SearchPlanner.queries(searchInput.getText().toString());
            queryIndex = 0;
            refresh();
            Toast.makeText(this, "بانک مخاطبان، گروه‌ها، AI محلی و صف ارسال همگام شدند.", Toast.LENGTH_LONG).show();
        });
        card.addView(sync, matchWithTop(10));
        root.addView(card, cardParams());
    }

    private void addSendSection(LinearLayout root) {
        LinearLayout card = card("۴. مسیر ارسال کنترل‌شده در روبیکا");
        messageInput = input("متن پیام", true);
        card.addView(messageInput, match());
        intervalInput = input("فاصلهٔ هر یادآوری به دقیقه (حداقل ۱)", false);
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalInput.setText("5");
        card.addView(intervalInput, matchWithTop(8));
        consentCheck = new CheckBox(this);
        consentCheck.setText("تأیید می‌کنم این مخاطبان رضایت دریافت پیام دارند.");
        consentCheck.setTextSize(13);
        card.addView(consentCheck, matchWithTop(8));
        Button start = button("ساخت صف از فایل کاربر", Color.rgb(217, 70, 70));
        start.setOnClickListener(v -> startQueue());
        card.addView(start, matchWithTop(8));
        Button prepare = button("باز کردن پیام فعلی در روبیکا", Color.rgb(109, 40, 217));
        prepare.setOnClickListener(v -> openCurrentInRubika());
        card.addView(prepare, matchWithTop(8));
        Button confirm = button("پیام ارسال شد؛ رفتن به نفر بعد", Color.rgb(5, 150, 105));
        confirm.setOnClickListener(v -> confirmSent());
        card.addView(confirm, matchWithTop(8));
        Button stop = button("توقف صف", Color.rgb(75, 85, 99));
        stop.setOnClickListener(v -> {
            queue.stop();
            ReminderReceiver.cancel(this);
            awaitingSendConfirmation = false;
            refreshQueue();
        });
        card.addView(stop, matchWithTop(8));
        queueText = text("", 13, true, Color.rgb(31, 41, 55));
        card.addView(queueText, matchWithTop(10));
        root.addView(card, cardParams());
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        });
        startActivityForResult(intent, PICK_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = displayName(uri);
        contactsText.setText(String.format(Locale.getDefault(), "در حال خواندن و تحلیل %s …", name));
        files.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("فایل باز نشد.");
                List<ContactRecord> contacts = SpreadsheetImporter.parse(input, name);
                if (contacts.size() > 5000) contacts = new ArrayList<>(contacts.subList(0, 5000));
                repository.saveContacts(contacts);
                int count = contacts.size();
                runOnUiThread(() -> {
                    refresh();
                    Toast.makeText(this, count + " مخاطب معتبر ذخیره شد.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> contactsText.setText(String.format(Locale.getDefault(), "خطا در فایل: %s", safeMessage(error))));
            }
        });
    }

    private void openNextSearch() {
        String seed = searchInput.getText().toString().trim();
        if (plannedQueries.isEmpty() || queryIndex >= plannedQueries.size()) {
            plannedQueries = SearchPlanner.queries(seed);
            queryIndex = 0;
        }
        String query = plannedQueries.get(queryIndex++);
        Intent search = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query);
        if (search.resolveActivity(getPackageManager()) == null) {
            search = new Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query)));
        }
        try { startActivity(search); }
        catch (Exception error) { Toast.makeText(this, "مرورگر یا موتور جستجو پیدا نشد.", Toast.LENGTH_LONG).show(); }
    }

    private void saveGroup() {
        String title = groupTitleInput.getText().toString().trim();
        String url = groupUrlInput.getText().toString().trim();
        Uri parsed = Uri.parse(url);
        String host = parsed.getHost();
        if (host == null || !(host.equals("rubika.ir") || host.endsWith(".rubika.ir")) || parsed.getPathSegments().isEmpty()) {
            groupUrlInput.setError("یک لینک عمومی معتبر rubika.ir وارد کنید.");
            return;
        }
        repository.addGroup(url, title.isEmpty() ? parsed.getLastPathSegment() : title);
        groupTitleInput.setText("");
        groupUrlInput.setText("");
        refresh();
    }

    private void startQueue() {
        List<ContactRecord> contacts = repository.contacts();
        String message = messageInput.getText().toString().trim();
        if (contacts.isEmpty()) { toast("اول فایل مخاطبان را وارد کنید."); return; }
        if (message.isEmpty()) { messageInput.setError("متن پیام لازم است."); return; }
        if (!consentCheck.isChecked()) { toast("تأیید رضایت مخاطبان لازم است."); return; }
        int interval;
        try { interval = Math.max(1, Integer.parseInt(intervalInput.getText().toString().trim())); }
        catch (Exception ignored) { interval = 5; intervalInput.setText("5"); }
        queue.start(contacts, message, interval);
        awaitingSendConfirmation = false;
        SendQueue.QueueItem item = queue.current();
        if (item != null) ReminderReceiver.schedule(this, item.nextAt);
        refresh();
    }

    private void openCurrentInRubika() {
        SendQueue.QueueItem item = queue.current();
        if (item == null) { toast("صف فعالی وجود ندارد."); return; }
        if (System.currentTimeMillis() < item.nextAt) { toast("هنوز زمان مخاطب بعدی نرسیده است."); return; }
        String body = (item.name.isEmpty() ? "" : item.name + "\n") + item.message + "\n\nشماره ثبت‌شده: " + item.phone;
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body);
        if (getPackageManager().getLaunchIntentForPackage(RUBIKA_PACKAGE) != null) share.setPackage(RUBIKA_PACKAGE);
        awaitingSendConfirmation = true;
        try { startActivity(Intent.createChooser(share, "ارسال با روبیکا")); }
        catch (Exception error) {
            awaitingSendConfirmation = false;
            toast("روبیکا یا برنامهٔ اشتراک‌گذاری پیدا نشد.");
        }
    }

    private void confirmSent() {
        if (!awaitingSendConfirmation) {
            new AlertDialog.Builder(this).setMessage("ابتدا پیام فعلی را در روبیکا باز و ارسال کنید.")
                    .setPositiveButton("باشه", null).show();
            return;
        }
        awaitingSendConfirmation = false;
        if (queue.advance()) {
            SendQueue.QueueItem next = queue.current();
            if (next != null) ReminderReceiver.schedule(this, next.nextAt);
            toast("ثبت شد؛ یادآوری بعدی زمان‌بندی شد.");
        } else {
            ReminderReceiver.cancel(this);
            toast("صف کامل شد.");
        }
        refresh();
    }

    private void refresh() {
        List<ContactRecord> contacts = repository.contacts();
        List<AppRepository.GroupRecord> groups = repository.groups();
        int consentCount = 0;
        for (ContactRecord contact : contacts) if (contact.consent) consentCount++;
        summary.setText(String.format(Locale.US, "مخاطب: %d  •  رضایت ثبت‌شده: %d  •  گروه عمومی: %d  •  صف: %s",
                contacts.size(), consentCount, groups.size(), queue.isActive() ? "فعال" : "متوقف"));

        StringBuilder contactLines = new StringBuilder("تعداد ذخیره‌شده: ").append(contacts.size());
        for (int i = 0; i < Math.min(8, contacts.size()); i++) {
            ContactRecord item = contacts.get(i);
            contactLines.append("\n• ").append(item.name.isEmpty() ? "بدون نام" : item.name)
                    .append(" — ").append(item.phone).append(" — امتیاز ").append(item.score)
                    .append(item.consent ? " — رضایت ✓" : "");
        }
        contactsText.setText(contactLines);

        StringBuilder groupLines = new StringBuilder("گروه‌های بررسی و ذخیره‌شده: ").append(groups.size());
        for (int i = 0; i < Math.min(8, groups.size()); i++) {
            AppRepository.GroupRecord item = groups.get(i);
            groupLines.append("\n• ").append(item.title).append(" — امتیاز ").append(item.score)
                    .append("\n  ").append(item.url);
        }
        groupsText.setText(groupLines);
        refreshQueue();
    }

    private void refreshQueue() {
        if (queueText == null) return;
        SendQueue.QueueItem item = queue.current();
        if (item == null) {
            queueText.setText("صف ارسال متوقف است.");
            return;
        }
        String when = item.nextAt <= System.currentTimeMillis() ? "آمادهٔ ارسال" : DateFormat.getDateTimeInstance().format(new Date(item.nextAt));
        String recipient = item.name.isEmpty() ? item.phone : item.name + " — " + item.phone;
        queueText.setText(String.format(Locale.getDefault(),
                "ردیف %d از %d\nمخاطب: %s\nزمان: %s\nفاصله: %d دقیقه",
                item.index + 1, item.total, recipient, when, queue.intervalMinutes()));
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        return "contacts.csv";
    }

    private void requestNotificationsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        }
    }

    private LinearLayout card(String title) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, 18));
        card.addView(text(title, 19, true, Color.rgb(76, 29, 149)), match());
        return card;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.2f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, boolean multiline) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(14);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setBackground(rounded(Color.rgb(243, 244, 246), 12));
        field.setSingleLine(!multiline);
        if (multiline) {
            field.setMinLines(3);
            field.setGravity(Gravity.TOP | Gravity.END);
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return field;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(color, 12));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWithTop(int top) {
        LinearLayout.LayoutParams params = match();
        params.setMargins(0, dp(top), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = match();
        params.setMargins(0, dp(10), 0, dp(4));
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
