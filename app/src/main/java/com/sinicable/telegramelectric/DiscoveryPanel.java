package com.sinicable.telegramelectric;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Discovery UI shares the application's existing Telegram client and device-local choices. */
// Programmatic-only view: inflation cannot supply the existing shared Telegram client.
@android.annotation.SuppressLint("ViewConstructor")
public final class DiscoveryPanel extends LinearLayout {
    private final TelegramClientManager telegram;
    private final DiscoveryPreferences preferences;
    private final EditText query, category;
    private final TextView status;
    private final LinearLayout results, recent;
    private final CheckBox favoritesOnly;
    private final Button search;
    private int generation, page;
    private boolean detached;
    private List<Long> resultIds;

    public DiscoveryPanel(Context context, TelegramClientManager telegram) {
        super(context);
        this.telegram = telegram; preferences = new DiscoveryPreferences(context);
        setOrientation(VERTICAL); setLayoutDirection(LAYOUT_DIRECTION_RTL);
        addText("کشف گروه‌های عمومی و علاقه‌مندی‌ها");
        addText("تنظیمات و تاریخچه فقط روی همین گوشی می‌مانند؛ همگام‌سازی شبکه‌ای فعال نیست.");
        query = field("موضوع یا شهر؛ مثل برق صنعتی تهران"); addView(query);
        category = field("فیلتر موضوع عنوان گروه"); category.setText(preferences.category()); addView(category);
        search = button("جست‌وجوی گروه عمومی", () -> runSearch()); addView(search);
        status = new TextView(context); status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE); addView(status);
        favoritesOnly = new CheckBox(context); favoritesOnly.setText("فقط گروه‌های علاقه‌مندی");
        favoritesOnly.setOnCheckedChangeListener((view, checked) -> { page = 0; render(); }); addView(favoritesOnly);
        CheckBox history = new CheckBox(context); history.setText("ذخیرهٔ تاریخچهٔ جست‌وجو روی گوشی");
        history.setChecked(preferences.remembersHistory());
        history.setOnCheckedChangeListener((view, checked) -> { preferences.setRememberHistory(checked); renderHistory(); }); addView(history);
        addView(button("نمایش همهٔ گروه‌های عمومی کشف‌شده", () -> { resultIds = null; page = 0; render(); }));
        addView(button("ذخیرهٔ فیلتر موضوع", () -> { preferences.setCategory(category.getText().toString()); render(); }));
        addView(button("ظاهر روشن (پس از باز کردن دوباره برنامه)", () -> preferences.setTheme("light")));
        addView(button("ظاهر تیره (پس از باز کردن دوباره برنامه)", () -> preferences.setTheme("dark")));
        addView(button("ظاهر مطابق دستگاه (پس از باز کردن دوباره برنامه)", () -> preferences.setTheme("system")));
        recent = new LinearLayout(context); recent.setOrientation(VERTICAL); addView(recent);
        results = new LinearLayout(context); results.setOrientation(VERTICAL); addView(results);
        addView(button("صفحهٔ قبل", () -> { page = Math.max(0, page - 1); render(); }));
        addView(button("صفحهٔ بعد", () -> { page++; render(); }));
        addView(button("پاک کردن تنظیمات، تاریخچه و علاقه‌مندی‌های کشف", () -> {
            preferences.clear(); history.setChecked(false); category.setText(""); renderHistory(); render();
        }));
        renderHistory(); render();
    }
    private void runSearch() {
        if (detached) return;
        String raw = SearchQuery.parse(query.getText().toString()).normalized;
        preferences.remember(raw); renderHistory();
        final int request = ++generation;
        search.setEnabled(false); status.setText("در حال جست‌وجو...");
        telegram.discoverPublicGroupsForReview(raw, (success, count, ids, message) -> post(() -> {
            if (detached || request != generation) return;
            search.setEnabled(true); status.setText(message);
            if (success) { resultIds = new ArrayList<>(ids); page = 0; render(); }
        }));
    }
    private void renderHistory() {
        recent.removeAllViews();
        for (String value : preferences.history()) recent.addView(button(value, () -> query.setText(value)));
    }
    private void render() {
        results.removeAllViews();
        Set<Long> favorites = preferences.favorites();
        String filter = SearchQuery.parse(category.getText().toString()).normalized;
        List<TelegramClientManager.GroupInfo> groups = DiscoveryResults.select(
                telegram.getFoundGroups(), resultIds, favorites, favoritesOnly.isChecked(), filter);
        int pages = Math.max(1, (groups.size() + 19) / 20); page = Math.min(page, pages - 1);
        TextView total = new TextView(getContext()); total.setText(getContext().getString(R.string.discovery_result_page, groups.size(), page + 1, pages)); results.addView(total);
        if (groups.isEmpty()) { TextView empty = new TextView(getContext()); empty.setText("گروهی برای نمایش نیست؛ عبارت یا فیلتر دیگری را امتحان کن."); results.addView(empty); }
        for (int i = page * 20; i < Math.min(groups.size(), page * 20 + 20); i++) {
            TelegramClientManager.GroupInfo group = groups.get(i);
            TextView title = new TextView(getContext()); title.setText(group.title); results.addView(title);
            results.addView(button(favorites.contains(group.id) ? "حذف از علاقه‌مندی‌ها" : "ذخیره در علاقه‌مندی‌ها", () -> { preferences.toggleFavorite(group.id); render(); }));
            results.addView(button("مشاهده در تلگرام", () -> {
                try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(group.link))); }
                catch (android.content.ActivityNotFoundException error) { status.setText("برنامه‌ای برای باز کردن لینک موجود نیست."); }
            }));
        }
    }
    public void detach() { detached = true; generation++; }
    @Override protected void onDetachedFromWindow() { detach(); super.onDetachedFromWindow(); }
    private EditText field(String hint) {
        EditText value = new EditText(getContext()); value.setHint(hint); value.setContentDescription(hint);
        value.setSingleLine(true); value.setFilters(new InputFilter[] {new InputFilter.LengthFilter(96)}); return value;
    }
    private Button button(String label, Runnable action) {
        Button value = new Button(getContext()); value.setText(label); value.setAllCaps(false);
        value.setOnClickListener(view -> action.run()); return value;
    }
    private void addText(String value) { TextView view = new TextView(getContext()); view.setText(value); addView(view); }
}
