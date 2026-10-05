package com.sinicable.telegramelectric;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public final class CentralCorePanel extends LinearLayout {
    public interface PhotoRequestListener {
        void onChoosePhoto();
    }

    public interface ExportRequestListener {
        void onExportRequested(
                ExportFileWriter.EntityType entityType,
                ExportFileWriter.Format format,
                int startNumber,
                int endNumber
        );
    }

    private final TelegramClientManager telegram;
    private CentralCore core;

    private final EditText messageInput;
    private final Spinner scheduleSpinner;
    private final LinearLayout targetContainer;
    private final TextView statusText;
    private final TextView summaryText;
    private final TextView groupsText;
    private final TextView contactsText;
    private TextView targetPageText;
    private final Button startButton;
    private final Button stopButton;
    private final TextView photoStatusText;
    private PhotoRequestListener photoRequestListener;
    private EditText exportStartInput;
    private EditText exportEndInput;
    private Spinner exportEntitySpinner;
    private ExportRequestListener exportRequestListener;
    private int targetPage = 0;
    private static final int TARGET_PAGE_SIZE = 40;

    private final String[] scheduleLabels = {
            "هر ۵ دقیقه ۱ پیام",
            "هر ۱۰ دقیقه ۱ پیام",
            "هر ۱۵ دقیقه ۱ پیام",
            "هر ۳۰ دقیقه ۱ پیام",
            "هر ساعت ۱ پیام",
            "هر ساعت ۳ پیام",
            "هر ساعت ۵ پیام",
            "هر روز ۱۰ پیام"
    };

    private final CentralCore.ScheduleMode[] scheduleModes = {
            CentralCore.ScheduleMode.EVERY_5_MINUTES,
            CentralCore.ScheduleMode.EVERY_10_MINUTES,
            CentralCore.ScheduleMode.EVERY_15_MINUTES,
            CentralCore.ScheduleMode.EVERY_30_MINUTES,
            CentralCore.ScheduleMode.EVERY_HOUR,
            CentralCore.ScheduleMode.THREE_PER_HOUR,
            CentralCore.ScheduleMode.FIVE_PER_HOUR,
            CentralCore.ScheduleMode.TEN_PER_DAY
    };

    public CentralCorePanel(Context context, TelegramClientManager telegram, WordBank wordBank) {
        this(context, telegram, wordBank, null);
    }

    public CentralCorePanel(
            Context context,
            TelegramClientManager telegram,
            WordBank wordBank,
            CentralCore sharedCore
    ) {
        super(context);
        this.telegram = telegram;

        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setPadding(dp(6), dp(8), dp(6), dp(8));

        addView(label("هسته مرکزی", 22, true), full());

        statusText = label("هسته مرکزی متوقف است.", 14, true);
        addView(statusText, full());

        summaryText = label("", 13, false);
        addView(summaryText, full());

        addSpace(10);
        addView(label("۱) ارسال زمان‌بندی‌شده پیام", 19, true), full());

        TextView safeNote = label(
                "ارسال زمان‌بندی‌شده فقط به گروه‌هایی انجام می‌شود که از نتیجه جستجو پیدا شده‌اند، خودت انتخابشان کرده‌ای و حساب اجازه ارسال پیام در آن‌ها دارد.",
                12,
                false
        );
        addView(safeNote, full());

        messageInput = new EditText(context);
        messageInput.setHint("متن پیام برای گروه‌های هدف");
        messageInput.setTextSize(16);
        messageInput.setMinLines(3);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        messageInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        addView(messageInput, full());

        addView(label("عکس پیام (اختیاری)", 14, true), full());

        photoStatusText = label("", 12, false);
        addView(photoStatusText, full());

        LinearLayout photoButtons = new LinearLayout(context);
        photoButtons.setOrientation(HORIZONTAL);
        photoButtons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button choosePhotoButton = new Button(context);
        choosePhotoButton.setText("انتخاب عکس");
        choosePhotoButton.setAllCaps(false);
        choosePhotoButton.setOnClickListener(v -> {
            if (photoRequestListener != null) {
                photoRequestListener.onChoosePhoto();
            } else {
                statusText.setText("انتخاب عکس هنوز آماده نیست.");
            }
        });
        photoButtons.addView(choosePhotoButton, weight());

        Button clearPhotoButton = new Button(context);
        clearPhotoButton.setText("حذف عکس");
        clearPhotoButton.setAllCaps(false);
        clearPhotoButton.setOnClickListener(v -> {
            core.clearPhoto(getContext());
            refreshPhotoStatus();
        });
        photoButtons.addView(clearPhotoButton, weight());

        addView(photoButtons, full());

        addView(label("زمان‌بندی ارسال", 14, true), full());

        scheduleSpinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                scheduleLabels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scheduleSpinner.setAdapter(adapter);
        addView(scheduleSpinner, full());

        addView(label("گروه‌های هدف", 14, true), full());

        targetContainer = new LinearLayout(context);
        targetContainer.setOrientation(VERTICAL);
        targetContainer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        targetContainer.setPadding(dp(6), dp(4), dp(6), dp(4));
        addView(targetContainer, full());

        LinearLayout targetPager = new LinearLayout(context);
        targetPager.setOrientation(HORIZONTAL);
        targetPager.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button targetNextButton = new Button(context);
        targetNextButton.setText("بعدی");
        targetNextButton.setAllCaps(false);
        targetNextButton.setOnClickListener(v -> {
            int size = telegram.getFoundGroups().size();
            int maxPage = Math.max(0, (size - 1) / TARGET_PAGE_SIZE);
            if (targetPage < maxPage) {
                targetPage++;
                refreshTargets();
            }
        });
        targetPager.addView(targetNextButton, weight());

        targetPageText = label("", 12, false);
        targetPageText.setGravity(Gravity.CENTER);
        targetPager.addView(targetPageText, weight());

        Button targetPreviousButton = new Button(context);
        targetPreviousButton.setText("قبلی");
        targetPreviousButton.setAllCaps(false);
        targetPreviousButton.setOnClickListener(v -> {
            if (targetPage > 0) {
                targetPage--;
                refreshTargets();
            }
        });
        targetPager.addView(targetPreviousButton, weight());

        addView(targetPager, full());

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(HORIZONTAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        startButton = new Button(context);
        startButton.setText("START");
        startButton.setAllCaps(false);
        controls.addView(startButton, weight());

        stopButton = new Button(context);
        stopButton.setText("STOP");
        stopButton.setAllCaps(false);
        controls.addView(stopButton, weight());

        addView(controls, full());

        addSpace(14);
        addView(label("۲) گروه‌های پیدا شده", 19, true), full());
        groupsText = label("", 14, false);
        groupsText.setTextIsSelectable(true);
        addView(groupsText, full());

        addSpace(14);
        addView(label("۳) مخاطبین موجود حساب", 19, true), full());
        TextView contactNote = label(
                "این بخش فقط مخاطبین قابل مشاهده حساب را نگه می‌دارد. مخاطبین مقصد ارسال زمان‌بندی‌شده نیستند.",
                12,
                false
        );
        addView(contactNote, full());

        contactsText = label("", 14, false);
        contactsText.setTextIsSelectable(true);
        addView(contactsText, full());

        addSpace(14);
        addView(label("۴) دریافت فایل", 19, true), full());
        addView(label(
                "نوع داده و بازه شماره‌ها را انتخاب کن؛ مثلا مخاطب یا گروه شماره ۵۰۰ تا ۱۰۰۰.",
                12,
                false
        ), full());

        exportEntitySpinner = new Spinner(context);
        ArrayAdapter<String> exportEntityAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                new String[]{"مخاطبین", "گروه‌ها"}
        );
        exportEntityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exportEntitySpinner.setAdapter(exportEntityAdapter);
        addView(exportEntitySpinner, full());

        LinearLayout exportRangeRow = new LinearLayout(context);
        exportRangeRow.setOrientation(HORIZONTAL);
        exportRangeRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        exportStartInput = new EditText(context);
        exportStartInput.setHint("از شماره");
        exportStartInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        exportStartInput.setSingleLine(true);
        exportRangeRow.addView(exportStartInput, weight());

        exportEndInput = new EditText(context);
        exportEndInput.setHint("تا شماره");
        exportEndInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        exportEndInput.setSingleLine(true);
        exportRangeRow.addView(exportEndInput, weight());

        addView(exportRangeRow, full());

        LinearLayout exportButtons = new LinearLayout(context);
        exportButtons.setOrientation(HORIZONTAL);
        exportButtons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Button txtButton = new Button(context);
        txtButton.setText("دریافت TXT");
        txtButton.setAllCaps(false);
        txtButton.setOnClickListener(v -> requestExport(ExportFileWriter.Format.TXT));
        exportButtons.addView(txtButton, weight());

        Button excelButton = new Button(context);
        excelButton.setText("دریافت Excel");
        excelButton.setAllCaps(false);
        excelButton.setOnClickListener(v -> requestExport(ExportFileWriter.Format.XLSX));
        exportButtons.addView(excelButton, weight());

        addView(exportButtons, full());

        CentralCore.Listener panelListener = new CentralCore.Listener() {
            @Override
            public void onStatus(String message) {
                post(() -> statusText.setText(message));
            }

            @Override
            public void onDataChanged() {
                post(CentralCorePanel.this::refreshSummary);
            }
        };

        if (sharedCore != null) {
            core = sharedCore;
            core.setListener(panelListener);
        } else {
            core = new CentralCore(
                    context,
                    telegram,
                    wordBank,
                    panelListener
            );
        }

        messageInput.setText(core.getMessage());
        refreshPhotoStatus();
        scheduleSpinner.setSelection(scheduleIndex(core.getMode()));

        scheduleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < scheduleModes.length) {
                    core.setMode(scheduleModes[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        startButton.setOnClickListener(v -> {
            core.setMessage(messageInput.getText().toString());
            core.start();
            refreshSummary();
        });

        stopButton.setOnClickListener(v -> {
            core.stop();
            refreshSummary();
        });

        refreshAll();
    }

    public void refreshAll() {
        refreshTargets();
        refreshGroups();
        refreshContacts();
        refreshPhotoStatus();
        refreshSummary();
    }

    public void shutdown() {
        core.shutdown();
    }

    public CentralCore getCore() {
        return core;
    }

    public void setExportRequestListener(ExportRequestListener listener) {
        this.exportRequestListener = listener;
    }

    public void setPhotoRequestListener(PhotoRequestListener listener) {
        this.photoRequestListener = listener;
    }

    public void setSelectedPhotoPath(String path) {
        core.setPhotoPath(path);
        refreshPhotoStatus();
    }

    private void refreshPhotoStatus() {
        if (photoStatusText == null) return;

        if (core.hasPhoto()) {
            photoStatusText.setText("عکس انتخاب شده ✅ — همراه متن به گروه هدف ارسال می‌شود.");
        } else {
            photoStatusText.setText("بدون عکس — فقط متن ارسال می‌شود.");
        }
    }

    private void requestExport(ExportFileWriter.Format format) {
        if (exportRequestListener == null) {
            statusText.setText("خروجی فایل هنوز آماده نیست.");
            return;
        }

        int start;
        int end;
        try {
            start = Integer.parseInt(exportStartInput.getText().toString().trim());
            end = Integer.parseInt(exportEndInput.getText().toString().trim());
        } catch (NumberFormatException error) {
            statusText.setText("برای بازه، عدد معتبر وارد کن؛ مثلا 500 تا 1000.");
            return;
        }

        if (start <= 0 || end <= 0 || end < start) {
            statusText.setText("بازه نامعتبر است. شماره شروع باید کوچکتر یا مساوی شماره پایان باشد.");
            return;
        }

        ExportFileWriter.EntityType entityType =
                exportEntitySpinner.getSelectedItemPosition() == 0
                        ? ExportFileWriter.EntityType.CONTACTS
                        : ExportFileWriter.EntityType.GROUPS;

        int count = ExportFileWriter.countInRange(telegram, entityType, start, end);
        if (count <= 0) {
            statusText.setText("در بازه " + start + " تا " + end + " رکوردی برای دریافت وجود ندارد.");
            return;
        }

        statusText.setText("آماده دریافت " + count + " رکورد...");
        exportRequestListener.onExportRequested(entityType, format, start, end);
    }

    private void refreshSummary() {
        summaryText.setText(
                "وضعیت: " + (core.isEnabled() ? "فعال ✅" : "متوقف")
                        + " | گروه هدف: " + core.selectedGroupCount()
                        + " | ارسال موفق: " + core.getSentCount()
                        + "\nگروه ثبت‌شده: " + telegram.getFoundGroups().size()
                        + " | مخاطب یکتا: " + telegram.getTelegramContacts().size()
                        + " | صف هوشمند: " + core.queueSize() + " واژه"
        );

        startButton.setEnabled(!core.isEnabled());
        stopButton.setEnabled(core.isEnabled());
    }

    private void refreshTargets() {
        targetContainer.removeAllViews();
        List<TelegramClientManager.GroupInfo> groups = telegram.getFoundGroups();

        if (groups.isEmpty()) {
            targetPage = 0;
            if (targetPageText != null) targetPageText.setText("۰ مورد");
            targetContainer.addView(
                    label("هنوز گروهی از نتیجه جستجوی عمومی ثبت نشده است.", 13, false),
                    full()
            );
            return;
        }

        int maxPage = Math.max(0, (groups.size() - 1) / TARGET_PAGE_SIZE);
        if (targetPage > maxPage) targetPage = maxPage;

        int from = targetPage * TARGET_PAGE_SIZE;
        int to = Math.min(groups.size(), from + TARGET_PAGE_SIZE);

        if (targetPageText != null) {
            targetPageText.setText(
                    (from + 1) + "–" + to + " از " + groups.size()
            );
        }

        for (int i = from; i < to; i++) {
            TelegramClientManager.GroupInfo group = groups.get(i);

            CheckBox check = new CheckBox(getContext());
            check.setText(
                    "#" + group.number + " — " + group.title + " — "
                            + (group.memberCount > 0 ? group.memberCount + " عضو" : "تعداد عضو نامشخص")
            );
            check.setChecked(core.isGroupSelected(group.id));
            check.setEnabled(group.canSend);
            check.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> core.setGroupSelected(group.id, isChecked)
            );
            targetContainer.addView(check, full());

            if (!group.canSend) {
                TextView note = label(
                        "وضعیت: " + group.status + " — حساب فعلاً اجازه ارسال پیام در این گروه را ندارد.",
                        11,
                        false
                );
                note.setPadding(dp(24), 0, dp(24), dp(3));
                targetContainer.addView(note, full());
            }
        }
    }

    private void refreshGroups() {
        List<TelegramClientManager.GroupInfo> groups = telegram.getFoundGroups();
        if (groups.isEmpty()) {
            groupsText.setText("هنوز گروهی ثبت نشده است.");
            return;
        }

        StringBuilder out = new StringBuilder();
        int limit = Math.min(groups.size(), 200);
        for (int i = 0; i < limit; i++) {
            TelegramClientManager.GroupInfo group = groups.get(i);
            out.append("شماره: ").append(group.number).append('\n');
            out.append("اسم: ").append(group.title).append('\n');
            out.append("لینک: ").append(group.link).append('\n');
            out.append("تعداد اعضا: ")
                    .append(group.memberCount > 0 ? group.memberCount : "نامشخص")
                    .append('\n');
            out.append("وضعیت: ").append(group.status).append('\n');
            out.append("────────────").append('\n');
        }
        groupsText.setText(out.toString());
    }

    private void refreshContacts() {
        List<TelegramClientManager.ContactInfo> contacts = telegram.getTelegramContacts();
        if (contacts.isEmpty()) {
            contactsText.setText("هنوز مخاطبی ثبت نشده است.");
            return;
        }

        StringBuilder out = new StringBuilder();
        int limit = Math.min(contacts.size(), 300);
        for (int i = 0; i < limit; i++) {
            TelegramClientManager.ContactInfo contact = contacts.get(i);
            out.append("شماره: ").append(contact.number).append('\n');
            out.append("اسم: ").append(contact.name).append('\n');
            out.append("شماره تلفن: ").append(contact.phone).append('\n');
            out.append("────────────").append('\n');
        }
        contactsText.setText(out.toString());
    }

    private int scheduleIndex(CentralCore.ScheduleMode mode) {
        for (int i = 0; i < scheduleModes.length; i++) {
            if (scheduleModes[i] == mode) return i;
        }
        return 0;
    }

    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LayoutParams full() {
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private LayoutParams weight() {
        LayoutParams params = new LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void addSpace(int heightDp) {
        View spacer = new View(getContext());
        addView(spacer, new LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
