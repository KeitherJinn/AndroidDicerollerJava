package com.example.dicerollerjava;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Native Java presentation. State/IO and rendering are deliberately separate. */
public final class MainActivity extends ComponentActivity {
    private DiceRollerViewModel model;
    private ThemePreferences preferences;
    private ThemePalette palette;
    private Ui ui;
    private ResultCards resultCards;
    private Bundle presentation;
    private LinearLayout root, content, form, advanced, latest, history, historyHeading, footer, warnings;
    private ScrollView mainScroll, formScroll;
    private EditText notation, times, name;
    private TextView notationHint, timesHint, modeHint, activeSettings, activeNote, status, currentMode;
    private Button rollButton, cancelButton, advancedButton;
    private final List<View> formControls = new ArrayList<>();
    private final Map<Integer, Button> quickButtons = new LinkedHashMap<>();
    private final Map<RollMode, RadioButton> modes = new LinkedHashMap<>();
    private final Map<String, View> anchors = new LinkedHashMap<>();
    private List<RollBatch> renderedHistory = null;
    private boolean renderedBusy, binding, expanded, wide, paletteOpen;
    private String pendingAnchor;
    private int pendingOffset, lastRevision;
    private Runnable unsubscribe;
    private final Handler messages = new Handler(Looper.getMainLooper());
    private final Runnable hideStatus = () -> { if (status != null) status.setVisibility(View.GONE); };
    private AlertDialog activeDialog;
    private OneShotPreDrawListener mainRestore, formRestore;
    private String queuedAnchor;
    private int queuedOffset, queuedScrollY, mainRestoreVersion, formRestoreVersion;
    private static final String RAW_SCROLL_ANCHOR = "scroll_position";
    private static final String TOP_ANCHOR = "scroll_top";

    public DiceRollerViewModel getModel() { return model; }

    @Override protected void onCreate(Bundle saved) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(saved);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        preferences = new ThemePreferences(this); palette = preferences.readPalette();
        presentation = saved == null ? new Bundle() : saved.getBundle("presentation");
        if (presentation == null) presentation = new Bundle();
        expanded = presentation.getBoolean("advanced");
        paletteOpen = presentation.getBoolean("paletteOpen");
        pendingAnchor = presentation.getString("anchor"); pendingOffset = presentation.getInt("offset");
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type, CreationExtras extras) {
                return type.cast(new DiceRollerViewModel(SavedStateHandleSupport.createSavedStateHandle(extras), new HistoryStore(getFilesDir())));
            }
        }).get(DiceRollerViewModel.class);
        lastRevision = saved == null ? model.getState().resultRevision : presentation.getInt("revision", model.getState().resultRevision);
        buildUi();
        unsubscribe = model.observe(this::render);
        if (paletteOpen) mainScroll.post(this::showPalette);
        else if (presentation.getBoolean("confirmClear")) mainScroll.post(this::confirmClear);
        else if (presentation.getBoolean("confirmReset")) mainScroll.post(this::confirmReset);
    }

    private void buildUi() {
        cancelScrollRestorations();
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ui = new Ui(this, palette.resolve(this, dark));
        wide = getResources().getConfiguration().screenWidthDp >= 720 && getResources().getConfiguration().screenHeightDp >= 220;
        formControls.clear(); quickButtons.clear(); modes.clear(); anchors.clear(); renderedHistory = null;
        root = ui.column(); root.setBackgroundColor(ui.colors.background); root.setFocusableInTouchMode(true);
        setContentView(root);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(!dark);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(!dark);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, keyboard.bottom));
            return windowInsets;
        });
        LinearLayout body = new LinearLayout(this); body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        mainScroll = scroll("main_list"); content = ui.column(); content.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(16));
        mainScroll.addView(content); form = createForm(); latest = ui.column(); history = ui.column(); historyHeading = ui.column();
        resultCards = createResultCards();
        if (wide) {
            formScroll = scroll("form_list"); LinearLayout panel = ui.column(); panel.setPadding(ui.dp(16), ui.dp(12), ui.dp(8), ui.dp(16));
            panel.addView(header()); panel.addView(form); formScroll.addView(panel);
            body.addView(formScroll, new LinearLayout.LayoutParams(0, -1, 0.44f));
            body.addView(mainScroll, new LinearLayout.LayoutParams(0, -1, 0.56f));
            content.addView(latest);
        } else {
            formScroll = mainScroll;
            body.addView(mainScroll, new LinearLayout.LayoutParams(-1, -1));
            View title = header(); content.addView(title); anchors.put("title", title);
            content.addView(latest); content.addView(form); anchors.put("form", form);
        }
        anchors.put("latest", latest); anchors.put("history_heading", historyHeading);
        content.addView(historyHeading); content.addView(history);
        footer = ui.column(); footer.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8)); footer.setBackgroundColor(ui.colors.surfaceContainer);
        status = ui.text("", 14, ui.colors.onSurface); status.setTag("status"); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); status.setVisibility(View.GONE); footer.addView(status);
        warnings = ui.card(ui.colors.errorContainer);
        ScrollView warningScroll = new ScrollView(this) {
            @Override protected void onMeasure(int width, int height) {
                int max = Math.max(ui.dp(80), Math.min(ui.dp(230), getResources().getDisplayMetrics().heightPixels / 3));
                super.onMeasure(width, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        warningScroll.setTag("persistence_panel"); warningScroll.addView(warnings); footer.addView(warningScroll);
        currentMode = ui.text("", 12, ui.colors.onSurfaceVariant); currentMode.setTag("current_mode");
        footer.addView(currentMode);
        rollButton = ui.button("", "roll_button", this::submit, true); footer.addView(rollButton, new LinearLayout.LayoutParams(-1, -2));
        cancelButton = ui.button(getString(R.string.cancel), "cancel_roll", model::cancelRoll, false); footer.addView(cancelButton, new LinearLayout.LayoutParams(-1, -2));
        root.addView(footer);
        ViewCompat.requestApplyInsets(root);
        render(model.getState());
        if (wide) restoreFormScroll(presentation.getInt("formScrollY", 0));
        // EditText must not become the initial focus merely because it is the first focusable child.
        // Users can explicitly focus any input; launching or recoloring the screen keeps the keyboard closed.
        root.requestFocus();
    }

    private ScrollView scroll(String tag) { ScrollView view = new ScrollView(this); view.setTag(tag); view.setFillViewport(true); view.setClipToPadding(false); return view; }
    private View header() {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 0, 0, ui.dp(12));
        TextView title = ui.text(getString(R.string.app_name), 25, ui.colors.onBackground); title.setTypeface(null, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.button(getString(R.string.palette_action), "palette_button", () -> { hideKeyboard(); showPalette(); }, false));
        return row;
    }
    private LinearLayout createForm() {
        LinearLayout panel = ui.column(); panel.setPadding(0, ui.dp(12), 0, ui.dp(12));
        panel.addView(ui.text(getString(R.string.quick_dice), 16, ui.colors.onSurface));
        FlowLayout quick = ui.flow();
        for (int sides : new int[]{4, 6, 8, 10, 12, 20, 100}) {
            Button button = ui.button("d" + sides, "quick_d" + sides, () -> model.setQuickDie(sides), false);
            quickButtons.put(sides, button); formControls.add(button); quick.addView(button);
        }
        panel.addView(quick); panel.addView(ui.text(getString(R.string.quick_dice_hint), 12, ui.colors.onSurfaceVariant)); ui.addGap(panel, 8);
        notation = input(panel, R.string.dice_notation, "notation", R.id.notation_input, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        notation.setImeOptions(EditorInfo.IME_ACTION_NEXT); notationHint = ui.text("", 12, ui.colors.onSurfaceVariant); panel.addView(notationHint);
        advancedButton = ui.button("", "advanced_toggle", () -> { expanded = !expanded; showAdvanced(); }, false); panel.addView(advancedButton);
        activeSettings = ui.text("", 14, ui.colors.onSurface); panel.addView(activeSettings);
        activeNote = ui.text("", 12, ui.colors.onSurfaceVariant); panel.addView(activeNote);
        advanced = ui.column(); panel.addView(advanced);
        times = input(advanced, R.string.repeat_count, "times", R.id.times_input, InputType.TYPE_CLASS_NUMBER);
        times.setImeOptions(EditorInfo.IME_ACTION_DONE); timesHint = ui.text("", 12, ui.colors.onSurfaceVariant); advanced.addView(timesHint);
        advanced.addView(ui.text(getString(R.string.roll_mode), 16, ui.colors.onSurface));
        FlowLayout modesRow = ui.flow();
        for (RollMode mode : RollMode.values()) {
            RadioButton radio = new RadioButton(this); radio.setText(modeText(mode)); radio.setTextColor(ui.colors.onSurface);
            radio.setTextSize(14); radio.setMinHeight(ui.dp(48)); radio.setPadding(0, ui.dp(4), ui.dp(8), ui.dp(4));
            radio.setButtonTintList(ColorStateList.valueOf(ui.colors.primary)); radio.setTag("mode_" + mode.name());
            radio.setOnClickListener(v -> model.setMode(mode)); modes.put(mode, radio); modesRow.addView(radio); formControls.add(radio);
        }
        advanced.addView(modesRow); modeHint = ui.text("", 12, ui.colors.onSurfaceVariant); advanced.addView(modeHint);
        name = input(advanced, R.string.note_optional, "name", R.id.name_input, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        watch(notation, model::setNotation); watch(times, model::setTimes); watch(name, model::setName);
        notation.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_NEXT) return false;
            expanded = true; showAdvanced(); times.requestFocus(); times.post(() -> times.requestRectangleOnScreen(new android.graphics.Rect(0, 0, times.getWidth(), times.getHeight()), false)); return true;
        });
        times.setOnEditorActionListener((v, action, event) -> { if (action != EditorInfo.IME_ACTION_DONE) return false; submit(); return true; });
        name.setOnEditorActionListener((v, action, event) -> { if (action != EditorInfo.IME_ACTION_DONE) return false; submit(); return true; });
        showAdvanced(); return panel;
    }
    private EditText input(LinearLayout parent, int label, String tag, int id, int type) {
        TextView caption = ui.text(getString(label), 14, ui.colors.onSurface); caption.setLabelFor(id); parent.addView(caption);
        EditText field = new EditText(this); field.setId(id); field.setTag(tag); field.setTextSize(16); field.setTextColor(ui.colors.onSurface);
        field.setHintTextColor(ui.colors.onSurfaceVariant); field.setSingleLine(true); field.setInputType(type); field.setSaveEnabled(false);
        field.setBackground(ui.background(ui.colors.surface, true)); field.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        field.setMinHeight(ui.dp(56)); parent.addView(field, new LinearLayout.LayoutParams(-1, -2)); formControls.add(field); return field;
    }
    private void watch(EditText field, Consumer<String> action) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!binding) action.accept(s.toString()); }
            @Override public void afterTextChanged(Editable text) { }
        });
    }
    private void showAdvanced() {
        presentation.putBoolean("advanced", expanded);
        advanced.setVisibility(expanded ? View.VISIBLE : View.GONE);
        advancedButton.setText(expanded ? R.string.collapse_settings : R.string.more_settings);
    }
    private void setText(EditText view, String value) {
        if (!view.getText().toString().equals(value)) { view.setText(value); view.setSelection(value.length()); }
    }

    private void render(RollerUiState state) {
        if (notation == null) return;
        binding = true;
        setText(notation, state.notation); setText(times, state.timesText); setText(name, state.name);
        notationHint.setText(state.notationIssue == null ? getString(R.string.notation_hint, "d20", new DiceExpression(2, 6, 3).notation, new DiceExpression(4, 8, -2).notation) : issueText(state.notationIssue));
        timesHint.setText(state.timesIssue == null ? getString(R.string.repeat_hint, RollLimits.MAX_REPEATS, RollLimits.MAX_TOTAL_DICE) : issueText(state.timesIssue));
        notationHint.setTextColor(state.notationIssue == null ? ui.colors.onSurfaceVariant : ui.colors.error);
        timesHint.setTextColor(state.timesIssue == null ? ui.colors.onSurfaceVariant : ui.colors.error);
        notation.setError(state.notationIssue == null ? null : issueText(state.notationIssue));
        times.setError(state.timesIssue == null ? null : issueText(state.timesIssue));
        activeSettings.setText(getString(R.string.active_settings, modeText(state.mode), state.timesText.isEmpty() ? getString(R.string.parameter_empty) : state.timesText));
        activeSettings.setTag("active_settings");
        activeNote.setText(state.name.isEmpty() ? "" : getString(R.string.active_note, state.name)); activeNote.setVisibility(state.name.isEmpty() ? View.GONE : View.VISIBLE);
        modeHint.setText(state.mode == RollMode.NORMAL ? R.string.mode_normal_hint : state.mode == RollMode.ADVANTAGE ? R.string.mode_advantage_hint : R.string.mode_disadvantage_hint);
        for (Map.Entry<RollMode, RadioButton> entry : modes.entrySet()) entry.getValue().setChecked(entry.getKey() == state.mode);
        for (Map.Entry<Integer, Button> entry : quickButtons.entrySet()) {
            String value = state.notation.trim().toLowerCase(java.util.Locale.ROOT);
            boolean selected = value.equals("d" + entry.getKey()) || value.equals("1d" + entry.getKey());
            entry.getValue().setSelected(selected); entry.getValue().setBackground(ui.background(selected ? ui.colors.primaryContainer : ui.colors.surfaceContainer, false));
            entry.getValue().setTextColor(selected ? ui.colors.onPrimaryContainer : ui.colors.primary);
        }
        for (View view : formControls) view.setEnabled(!state.busy);
        binding = false;
        renderWarnings(state);
        currentMode.setText(modeText(state.mode));
        cancelButton.setVisibility(state.isRolling ? View.VISIBLE : View.GONE);
        rollButton.setEnabled(!state.busy && !state.blockedForRecovery);
        rollButton.setAlpha(rollButton.isEnabled() ? 1f : 0.65f);
        if (state.isLoading) rollButton.setText(R.string.loading);
        else if (state.isSaving) rollButton.setText(R.string.saving);
        else if (state.isRolling) rollButton.setText(getString(R.string.rolling_progress, state.progress, Math.max(1, state.requested)));
        else rollButton.setText(getString(R.string.roll_parameters, state.notation.isEmpty() ? getString(R.string.parameter_empty) : state.notation, state.timesText.isEmpty() ? getString(R.string.parameter_empty) : state.timesText));
        if (renderedHistory == null || !renderedHistory.equals(state.history) || renderedBusy != state.busy) renderResults();
        if (!state.isLoading && pendingAnchor != null) { String key = pendingAnchor; int offset = pendingOffset; pendingAnchor = null; restoreAnchor(key, offset); }
        // A new process restores presentation state but starts a fresh ViewModel revision.
        // Reset the baseline without disturbing the restored browsing position.
        if (state.resultRevision < lastRevision) lastRevision = state.resultRevision;
        if (state.resultRevision > lastRevision) { lastRevision = state.resultRevision; restoreAnchor(TOP_ANCHOR, 0); }
        String message = statusText(state);
        if (message != null) { showStatus(message); model.consumeStatus(); }
    }

    private void renderResults() {
        if (latest == null) return;
        String anchor = captureAnchor(); int offset = presentation.getInt("offset");
        RollerUiState state = model.getState(); renderedHistory = state.history; renderedBusy = state.busy;
        if (!state.isLoading) PresentationState.prune(presentation, state.history);
        historyHeading.removeAllViews();
        anchors.entrySet().removeIf(entry -> entry.getKey().startsWith("history_"));
        anchors.putAll(resultCards.render(latest, history, state.history, !state.busy));
        historyHeading.addView(ui.text(getResources().getQuantityString(R.plurals.history_batches_title, state.history.size(), state.history.size()), 20, ui.colors.onSurface));
        Button clear = ui.button(getString(R.string.clear_history), "clear_history", this::confirmClear, false);
        clear.setEnabled(!state.busy && !state.blockedForRecovery && !state.history.isEmpty()); historyHeading.addView(clear);
        historyHeading.addView(ui.text(getString(R.string.history_batch_retention, RollLimits.MAX_BATCHES, RollLimits.MAX_HISTORY_RESULTS, RollLimits.MAX_HISTORY_DICE), 12, ui.colors.onSurfaceVariant));
        if (state.hasUnsavedChanges) historyHeading.addView(ui.text(getString(R.string.history_not_saved), 12, ui.colors.error));
        if (anchor != null) restoreAnchor(anchor, offset);
    }
    private ResultCards createResultCards() {
        return new ResultCards(ui, presentation, new ResultCards.Actions() {
            @Override public void changed() { renderResults(); }
            @Override public void loadBatch(RollBatch batch) { model.loadBatch(batch); loaded(); }
            @Override public void loadResult(RollResult result) { model.loadResult(result); loaded(); }
            @Override public void copyBatch(RollBatch batch) { copy(formatter().formatBatch(batch)); }
            @Override public void copyResult(RollResult result) { copy(formatter().formatResult(result)); }
        });
    }
    private void loaded() {
        expanded = true; showAdvanced(); hideKeyboard();
        formScroll.post(() -> { if (wide) formScroll.smoothScrollTo(0, 0); else formScroll.smoothScrollTo(0, form.getTop()); });
        showStatus(getString(R.string.settings_loaded));
    }
    private void renderWarnings(RollerUiState s) {
        warnings.removeAllViews(); boolean visible = s.blockedForRecovery || s.hasUnsavedChanges || s.clearFailed || s.recoveryFailed;
        ((View) warnings.getParent()).setVisibility(visible ? View.VISIBLE : View.GONE);
        if (s.blockedForRecovery) {
            String value = s.loadIssue == HistoryLoadIssue.RECOVERED_RECORDS ? getResources().getQuantityString(R.plurals.history_recovered_records, s.skippedRecords, s.skippedRecords)
                    : getString(s.loadIssue == HistoryLoadIssue.CORRUPT_FILE ? R.string.history_corrupt_file : s.loadIssue == HistoryLoadIssue.UNSUPPORTED_VERSION ? R.string.history_unsupported_version : R.string.history_read_error);
            warning(value, "load_warning"); FlowLayout actions = ui.flow();
            warningAction(actions, R.string.retry_load, "retry_load", model::retryLoad, !s.busy);
            if (s.loadIssue == HistoryLoadIssue.RECOVERED_RECORDS && !s.history.isEmpty()) warningAction(actions, R.string.recover_valid_history, "recover_history", model::recoverHistory, !s.busy);
            warningAction(actions, R.string.reset_history, "reset_history", this::confirmReset, !s.busy); warnings.addView(actions);
        }
        if (s.recoveryFailed) warning(getString(R.string.history_recovery_failed), "recovery_warning");
        if (s.hasUnsavedChanges) {
            warning(getString(R.string.history_save_failed), "unsaved_warning"); FlowLayout actions = ui.flow();
            warningAction(actions, R.string.retry_save, "retry_save", model::retrySave, !s.busy && !s.blockedForRecovery); warnings.addView(actions);
        }
        if (s.clearFailed) {
            warning(getString(R.string.history_clear_failed), "clear_warning"); FlowLayout actions = ui.flow();
            warningAction(actions, R.string.retry_clear, "retry_clear", this::confirmClear, !s.busy && !s.blockedForRecovery); warnings.addView(actions);
        }
    }
    private void warning(String value, String tag) { TextView text = ui.text(value, 13, ui.colors.onErrorContainer); text.setTag(tag); text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); warnings.addView(text); }
    private void warningAction(FlowLayout parent, int label, String tag, Runnable action, boolean enabled) { Button b = ui.button(getString(label), tag, action, false); b.setTextColor(ui.colors.onErrorContainer); b.setEnabled(enabled); parent.addView(b); }

    private void submit() {
        if (model.roll()) hideKeyboard();
        else {
            RollerUiState state = model.getState(); if (state.busy || state.blockedForRecovery) return;
            EditText field = state.notationIssue != null ? notation : times;
            if (field == times) { expanded = true; showAdvanced(); }
            field.requestFocus(); field.post(() -> field.requestRectangleOnScreen(new android.graphics.Rect(0, 0, field.getWidth(), field.getHeight()), false));
        }
    }
    private void hideKeyboard() { View focus = getCurrentFocus(); if (focus != null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(), 0); root.requestFocus(); }
    private void showStatus(String message) {
        messages.removeCallbacks(hideStatus); status.setText(message); status.setVisibility(View.VISIBLE);
        AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
        int timeout = accessibility == null ? 5000
                : accessibility.getRecommendedTimeoutMillis(5000, AccessibilityManager.FLAG_CONTENT_TEXT);
        messages.postDelayed(hideStatus, timeout);
    }
    private void confirmClear() {
        RollerUiState s = model.getState(); if (s.busy || s.blockedForRecovery || s.history.isEmpty()) return;
        presentation.putBoolean("confirmClear", true);
        int count = 0; for (RollBatch batch : s.history) count += batch.count;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.clear_title).setMessage(getString(R.string.clear_batches_message, s.history.size(), count))
                .setNegativeButton(R.string.cancel, (d, which) -> { }).setPositiveButton(R.string.clear_history, (d, which) -> model.clearHistory()).create();
        dialog.setOnDismissListener(d -> { presentation.putBoolean("confirmClear", false); activeDialog = null; });
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTag("confirm_clear"); activeDialog = dialog;
    }
    private void confirmReset() {
        if (model.getState().busy || !model.getState().blockedForRecovery) return;
        presentation.putBoolean("confirmReset", true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.reset_history_title).setMessage(R.string.reset_history_message)
                .setNegativeButton(R.string.cancel, (d, which) -> { }).setPositiveButton(R.string.reset_history, (d, which) -> model.resetHistory()).create();
        dialog.setOnDismissListener(d -> { presentation.putBoolean("confirmReset", false); activeDialog = null; });
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTag("confirm_reset"); activeDialog = dialog;
    }
    private void showPalette() {
        if (isFinishing()) return;
        paletteOpen = true; presentation.putBoolean("paletteOpen", true);
        LinearLayout choices = ui.card(ui.colors.surface); choices.setTag("palette_dialog");
        choices.addView(ui.text(getString(R.string.palette_hint), 14, ui.colors.onSurfaceVariant));
        for (ThemePalette item : ThemePalette.values()) {
            RadioButton row = new RadioButton(this); row.setTag("palette_option_" + item.name()); row.setText(paletteName(item)); row.setTextSize(16);
            row.setMinHeight(ui.dp(56)); row.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)); row.setChecked(item == palette);
            row.setTextColor(ui.colors.onSurface); row.setButtonTintList(ColorStateList.valueOf(ui.colors.primary)); choices.addView(row, new LinearLayout.LayoutParams(-1, -2));
            row.setOnClickListener(v -> {
                String anchor = captureAnchor(); int offset = presentation.getInt("offset");
                preferences.setPalette(item); palette = item;
                AlertDialog previous = activeDialog; if (previous != null) { previous.setOnDismissListener(null); previous.dismiss(); }
                buildUi(); restoreAnchor(anchor, offset); showPalette();
            });
        }
        ScrollView list = new ScrollView(this); list.setTag("palette_options"); list.addView(choices);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.palette_title).setView(list).setPositiveButton(R.string.palette_done, (d, which) -> { }).create();
        dialog.setOnDismissListener(d -> { paletteOpen = false; presentation.putBoolean("paletteOpen", false); activeDialog = null; });
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTag("palette_done"); activeDialog = dialog;
        int max = Math.max(ui.dp(96), getResources().getDisplayMetrics().heightPixels - ui.dp(190));
        list.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, Math.min(ui.dp(380), max)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ui.colors.primary);
    }
    private int paletteName(ThemePalette item) {
        switch (item) { case VIOLET: return R.string.palette_violet; case OCEAN: return R.string.palette_ocean; case FOREST: return R.string.palette_forest; case AMBER: return R.string.palette_amber; default: return R.string.palette_system; }
    }
    private String modeText(RollMode mode) { return getString(mode == RollMode.NORMAL ? R.string.mode_normal : mode == RollMode.ADVANTAGE ? R.string.mode_advantage : R.string.mode_disadvantage); }
    private String issueText(InputIssue issue) {
        switch (issue) {
            case NOTATION_FORMAT: return getString(R.string.error_notation, "d20", new DiceExpression(2, 6, 3).notation, new DiceExpression(4, 8, -2).notation);
            case NOTATION_LENGTH: return getString(R.string.error_notation_length, RollLimits.MAX_NOTATION_LENGTH);
            case DICE_LIMIT: return getString(R.string.error_dice_limit, RollLimits.MAX_DICE);
            case SIDES_LIMIT: return getString(R.string.error_sides_limit, RollLimits.MAX_SIDES);
            case MODIFIER_LIMIT: return getString(R.string.error_modifier_limit);
            case REPEAT_LIMIT: return getString(R.string.error_repeat_limit, RollLimits.MAX_REPEATS);
            default: return getString(R.string.error_work_limit, RollLimits.MAX_TOTAL_DICE);
        }
    }
    private String statusText(RollerUiState s) {
        if (s.status == null) return null;
        switch (s.status) {
            case COMPLETED: return getResources().getQuantityString(R.plurals.rolls_completed, s.requested, s.requested);
            case CANCELLED: return getString(R.string.roll_cancelled);
            case CLEARED: return getString(R.string.history_cleared);
            case SAVED: return getString(R.string.history_saved);
            case RECOVERED: return getString(R.string.history_recovered);
            case RESET: return getString(R.string.history_reset);
            case ROLL_FAILED: return getString(R.string.roll_failed);
            default: return null;
        }
    }
    private ResultTextFormatter formatter() {
        return new ResultTextFormatter(label -> {
            switch (label) {
                case BATCH: return getString(R.string.export_batch); case EXPRESSION: return getString(R.string.dice_notation);
                case MODE: return getString(R.string.roll_mode); case NOTE: return getString(R.string.export_note);
                case TIME: return getString(R.string.export_time); case COUNT: return getString(R.string.batch_count);
                case TOTAL: return getString(R.string.batch_total); case AVERAGE: return getString(R.string.batch_average);
                case MINIMUM: return getString(R.string.batch_minimum); case MAXIMUM: return getString(R.string.batch_maximum);
                case ROLL: return getString(R.string.export_roll); case FIRST_GROUP: return getString(R.string.first_roll);
                case SECOND_GROUP: return getString(R.string.second_roll); case CHOSEN_TOTAL: return getString(R.string.export_chosen_total);
                case MODIFIER: return getString(R.string.export_modifier); case RESULT: return getString(R.string.export_result);
                case NORMAL: return getString(R.string.mode_normal); case HIGHER: return getString(R.string.mode_advantage);
                default: return getString(R.string.mode_disadvantage);
            }
        }, getResources().getConfiguration().getLocales().get(0));
    }
    private void copy(String text) { getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(getString(R.string.copy_label), text)); showStatus(getString(R.string.result_copied)); }

    private String captureAnchor() {
        captureFormScroll();
        if (mainScroll == null) return null;
        // Consecutive state changes can rebuild cards before the next layout. Keep the
        // intended position until it has actually been restored instead of sampling new zero-sized Views.
        if (queuedAnchor != null) {
            presentation.putString("anchor", queuedAnchor); presentation.putInt("offset", queuedOffset);
            presentation.putInt("scrollY", queuedScrollY); return queuedAnchor;
        }
        if (!mainScroll.isLaidOut()) return null;
        int[] origin = new int[2]; mainScroll.getLocationInWindow(origin);
        String match = null, nearest = null;
        int bestTop = Integer.MIN_VALUE, offset = 0, nearestOffset = 0, nearestDistance = Integer.MAX_VALUE;
        for (Map.Entry<String, View> entry : anchors.entrySet()) {
            View view = entry.getValue(); if (view.getHeight() == 0 || !view.isShown()) continue;
            int[] point = new int[2]; view.getLocationInWindow(point);
            int top = point[1] - origin[1];
            if (top <= 0 && top + view.getHeight() > 0 && top > bestTop) { match = entry.getKey(); offset = -top; bestTop = top; }
            if (top + view.getHeight() > 0 && top < mainScroll.getHeight() && Math.abs(top) < nearestDistance) {
                nearest = entry.getKey(); nearestOffset = -top; nearestDistance = Math.abs(top);
            }
        }
        // A gap or section heading may occupy the viewport's first pixels. A negative
        // offset preserves the visible space above the nearest card rather than jumping to the latest result.
        if (match == null && nearest != null) { match = nearest; offset = nearestOffset; }
        if (match == null) { match = RAW_SCROLL_ANCHOR; offset = 0; }
        presentation.putString("anchor", match); presentation.putInt("offset", offset);
        presentation.putInt("scrollY", mainScroll.getScrollY()); return match;
    }
    private void restoreAnchor(String key, int offset) {
        if (key == null) return;
        if (mainRestore != null) mainRestore.removeListener();
        int version = ++mainRestoreVersion;
        ScrollView target = mainScroll;
        int fallback = TOP_ANCHOR.equals(key) ? 0 : presentation.getInt("scrollY", target.getScrollY());
        queuedAnchor = key; queuedOffset = offset; queuedScrollY = fallback;
        mainRestore = OneShotPreDrawListener.add(target, () -> {
            if (version != mainRestoreVersion) return;
            mainRestore = null; queuedAnchor = null;
            if (target != mainScroll || isDestroyed()) return;
            if (TOP_ANCHOR.equals(key)) { target.scrollTo(0, 0); return; }
            // When the portrait form moves into its own landscape pane, preserve its
            // local position and start the separate results pane at the latest batch.
            if (wide && ("form".equals(key) || "title".equals(key))) {
                formRestoreVersion++;
                if (formRestore != null) { formRestore.removeListener(); formRestore = null; }
                formScroll.scrollTo(0, Math.max(0, ("form".equals(key) ? form.getTop() : 0) + offset));
                target.scrollTo(0, 0); return;
            }
            View anchor = anchors.get(key);
            if (anchor == null) { target.scrollTo(0, fallback); return; }
            int[] point = new int[2], origin = new int[2]; anchor.getLocationInWindow(point); target.getLocationInWindow(origin);
            target.scrollTo(0, Math.max(0, target.getScrollY() + point[1] - origin[1] + offset));
        });
        target.invalidate();
    }
    private void captureFormScroll() {
        if (wide && formScroll != null && formScroll != mainScroll && formScroll.isLaidOut() && formRestore == null) {
            presentation.putInt("formScrollY", formScroll.getScrollY());
        }
    }
    private void restoreFormScroll(int position) {
        if (!wide || formScroll == null || formScroll == mainScroll) return;
        if (formRestore != null) formRestore.removeListener();
        int version = ++formRestoreVersion;
        ScrollView target = formScroll;
        formRestore = OneShotPreDrawListener.add(target, () -> {
            if (version != formRestoreVersion) return;
            formRestore = null;
            if (target == formScroll && !isDestroyed()) target.scrollTo(0, position);
        });
        target.invalidate();
    }
    private void cancelScrollRestorations() {
        mainRestoreVersion++; formRestoreVersion++;
        if (mainRestore != null) { mainRestore.removeListener(); mainRestore = null; }
        if (formRestore != null) { formRestore.removeListener(); formRestore = null; }
        queuedAnchor = null;
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        RollerUiState state = model.getState();
        if (!state.isLoading) PresentationState.prune(presentation, state.history);
        captureAnchor(); presentation.putBoolean("advanced", expanded); presentation.putBoolean("paletteOpen", paletteOpen); presentation.putInt("revision", lastRevision);
        saved.putBundle("presentation", new Bundle(presentation)); super.onSaveInstanceState(saved);
    }
    @Override protected void onDestroy() {
        cancelScrollRestorations();
        if (unsubscribe != null) unsubscribe.run(); messages.removeCallbacksAndMessages(null);
        if (activeDialog != null) { activeDialog.setOnDismissListener(null); activeDialog.dismiss(); }
        super.onDestroy();
    }
}
