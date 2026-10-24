package com.example.dicerollerjava;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Native result cards retain expansion and pagination in the activity's presentation state. */
public final class ResultCards {
    private static final int RESULTS_PER_PAGE = 10;
    private static final int VALUES_PER_PAGE = RollLimits.DISPLAYED_VALUES;
    public interface Actions {
        void changed();
        void loadBatch(RollBatch batch);
        void loadResult(RollResult result);
        void copyBatch(RollBatch batch);
        void copyResult(RollResult result);
    }
    private final Ui ui;
    private final Bundle presentation;
    private final Actions actions;
    private final Locale locale;
    private final NumberFormat integer, decimal;
    private final Map<String, Card> retained = new LinkedHashMap<>();
    private final Set<String> invalidated = new HashSet<>();
    private View ready, noEarlier;

    private static final class Card {
        final RollBatch batch;
        final boolean latest;
        final List<Button> loadButtons = new ArrayList<>();
        View view;
        Card(RollBatch batch, boolean latest) { this.batch = batch; this.latest = latest; }
    }

    public ResultCards(Ui ui, Bundle presentation, Actions actions) {
        this.ui = ui; this.presentation = presentation; this.actions = actions;
        locale = ui.context.getResources().getConfiguration().getLocales().get(0);
        integer = NumberFormat.getIntegerInstance(locale);
        decimal = NumberFormat.getNumberInstance(locale);
        decimal.setMaximumFractionDigits(2);
    }

    public View build(RollBatch batch, boolean latest, boolean enabled) {
        Card card = retained.get(batch.id);
        boolean dirty = invalidated.remove(batch.id);
        if (card == null || card.latest != latest || !card.batch.equals(batch) || dirty) {
            card = new Card(batch, latest);
            card.view = buildCard(card, enabled);
            retained.put(batch.id, card);
        }
        for (Button button : card.loadButtons) button.setEnabled(enabled);
        return card.view;
    }

    /** Reconcile stable cards in place. Detail changes invalidate only their owning batch. */
    public Map<String, View> render(LinearLayout latest, LinearLayout history, List<RollBatch> batches, boolean enabled) {
        Set<String> ids = new HashSet<>();
        for (RollBatch batch : batches) ids.add(batch.id);
        retained.keySet().retainAll(ids);
        invalidated.retainAll(ids);
        List<View> newest = new ArrayList<>(), older = new ArrayList<>();
        Map<String, View> anchors = new LinkedHashMap<>();
        if (batches.isEmpty()) {
            if (ready == null) {
                LinearLayout card = ui.card(ui.colors.secondaryContainer); card.setTag("latest_result");
                card.addView(ui.text(string(R.string.ready_title), 24, ui.colors.onSecondaryContainer));
                card.addView(ui.text(string(R.string.ready_hint), 15, ui.colors.onSecondaryContainer));
                spaced(card); ready = card;
            }
            newest.add(ready);
        } else newest.add(build(batches.get(0), true, enabled));
        if (batches.size() <= 1) {
            if (noEarlier == null) noEarlier = ui.text(string(R.string.no_earlier_batches), 14, ui.colors.onSurfaceVariant);
            older.add(noEarlier);
        }
        for (int i = 1; i < batches.size(); i++) {
            RollBatch batch = batches.get(i);
            View card = build(batch, false, enabled);
            older.add(card); anchors.put("history_" + batch.id, card);
        }
        reconcile(latest, newest);
        reconcile(history, older);
        return anchors;
    }

    private void spaced(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(12); view.setLayoutParams(params);
    }

    private static void reconcile(LinearLayout parent, List<View> desired) {
        for (int index = 0; index < desired.size(); index++) {
            View wanted = desired.get(index);
            if (index < parent.getChildCount() && parent.getChildAt(index) == wanted) continue;
            if (wanted.getParent() instanceof ViewGroup) ((ViewGroup) wanted.getParent()).removeView(wanted);
            parent.addView(wanted, index);
        }
        while (parent.getChildCount() > desired.size()) parent.removeViewAt(parent.getChildCount() - 1);
    }

    private View buildCard(Card retainedCard, boolean enabled) {
        RollBatch batch = retainedCard.batch;
        boolean latest = retainedCard.latest;
        boolean single = batch.count == 1;
        String expandedKey = "batch_expanded_" + batch.id;
        String pageKey = "batch_page_" + batch.id;
        boolean expanded = presentation.getBoolean(expandedKey, false);
        int foreground = latest ? ui.colors.onSecondaryContainer : ui.colors.onSurface;
        LinearLayout card = ui.card(latest ? ui.colors.secondaryContainer : ui.colors.surfaceContainer);
        spaced(card);
        card.setTag(latest ? "latest_result" : "history_" + batch.id);
        LinearLayout content = ui.column(); content.setTag("batch_" + batch.id);
        card.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (latest) {
            TextView title = addText(content, string(single ? R.string.latest_result : R.string.latest_batch), 14, foreground, null);
            title.setAccessibilityHeading(true); title.setTypeface(null, Typeface.BOLD);
        }
        if (!DiceEngine.trimWhitespace(batch.name).isEmpty()) addText(content, batch.name, 16, foreground, null).setTypeface(null, Typeface.BOLD);
        TextView total = addText(content, integer.format(batch.total), latest ? 34 : 24, foreground,
                latest && single ? "final_result" : "batch_total_" + batch.id);
        total.setTypeface(null, Typeface.BOLD);
        if (!single) addText(content, string(R.string.batch_total), 12, foreground, null);
        addText(content, string(R.string.result_meta, batch.notation, modeText(batch.mode)), 14, foreground, null);
        if (!single) {
            FlowLayout metrics = ui.flow();
            metric(metrics, string(R.string.batch_count), integer.format(batch.count), foreground);
            metric(metrics, string(R.string.batch_average), decimal.format(batch.average), foreground);
            metric(metrics, string(R.string.batch_minimum), integer.format(batch.minimum), foreground);
            metric(metrics, string(R.string.batch_maximum), integer.format(batch.maximum), foreground);
            content.addView(metrics); ui.addGap(content, 4);
        }
        addText(content, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(new Date(batch.timestamp)),
                12, foreground, null);
        FlowLayout controls = ui.flow();
        controls.addView(ui.button(string(expanded ? R.string.hide_details : single ? R.string.show_details : R.string.show_batch_details),
                latest ? "latest_details" : "details_" + batch.id, () -> toggle(batch.id, expandedKey, expanded), false));
        Button load = ui.button(string(R.string.load_settings), "load_batch_" + batch.id, () -> actions.loadBatch(batch), false);
        load.setEnabled(enabled); controls.addView(load); retainedCard.loadButtons.add(load);
        controls.addView(ui.button(string(R.string.copy_batch), "copy_batch_" + batch.id, () -> actions.copyBatch(batch), false));
        content.addView(controls);
        if (expanded) {
            divider(content);
            int pageCount = (batch.count + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE;
            int page = Math.max(0, Math.min(presentation.getInt(pageKey, 0), pageCount - 1));
            int start = page * RESULTS_PER_PAGE, end = Math.min(start + RESULTS_PER_PAGE, batch.count);
            if (!single) addText(content, string(R.string.results_page_range, start + 1, end, batch.count), 13, foreground, null);
            for (int index = start; index < end; index++) content.addView(singleResult(retainedCard, batch.results.get(index), index + 1, single, enabled, foreground));
            if (pageCount > 1) content.addView(pageControls(batch.id, page, pageCount, "batch_" + batch.id, pageKey));
        }
        return card;
    }

    private void metric(FlowLayout parent, String label, String value, int foreground) {
        LinearLayout column = ui.column(); column.setMinimumWidth(ui.dp(70));
        column.setPadding(0, ui.dp(4), ui.dp(16), ui.dp(4));
        column.addView(ui.text(label, 12, foreground));
        TextView number = ui.text(value, 18, foreground); number.setTypeface(null, Typeface.BOLD); column.addView(number);
        parent.addView(column);
    }

    private View singleResult(Card card, RollResult result, int ordinal, boolean single, boolean enabled, int foreground) {
        LinearLayout content = ui.column(); content.setTag("roll_" + result.id);
        String expandedKey = "result_expanded_" + result.id;
        boolean expanded = presentation.getBoolean(expandedKey, single);
        if (!single) {
            addText(content, string(R.string.roll_number_result, ordinal, integer.format(result.finalResult)), 16, foreground, null).setTypeface(null, Typeface.BOLD);
            content.addView(ui.button(string(expanded ? R.string.hide_details : R.string.show_details),
                    "result_details_" + result.id, () -> toggle(card.batch.id, expandedKey, expanded), false));
        }
        if (expanded) {
            content.addView(group(card.batch.id, result.id, "first", string(R.string.first_roll), result.firstRoll, foreground));
            if (result.secondRoll != null) {
                content.addView(group(card.batch.id, result.id, "second", string(R.string.second_roll), result.secondRoll, foreground));
                addText(content, string(R.string.chosen_total, integer.format(result.chosenRoll.sum)), 14, foreground, null);
            }
            addText(content, string(R.string.modifier_value, (result.modifier >= 0 ? "+" : "") + integer.format(result.modifier)), 14, foreground, null);
            addText(content, string(R.string.final_value, integer.format(result.finalResult)), 14, foreground, null);
        }
        FlowLayout controls = ui.flow();
        Button load = ui.button(string(R.string.load_one), "load_result_" + result.id, () -> actions.loadResult(result), false);
        load.setEnabled(enabled); controls.addView(load); card.loadButtons.add(load);
        controls.addView(ui.button(string(R.string.copy_one), "copy_result_" + result.id, () -> actions.copyResult(result), false));
        content.addView(controls);
        if (!single) divider(content);
        return content;
    }

    private View group(String batchId, String resultId, String groupId, String label, DiceRoll roll, int foreground) {
        String tag = "group_" + resultId + "_" + groupId, pageKey = tag + "_page";
        int pageCount = (roll.values.size() + VALUES_PER_PAGE - 1) / VALUES_PER_PAGE;
        int page = Math.max(0, Math.min(presentation.getInt(pageKey, 0), pageCount - 1));
        int start = page * VALUES_PER_PAGE, end = Math.min(start + VALUES_PER_PAGE, roll.values.size());
        LinearLayout content = ui.column(); content.setTag(tag);
        addText(content, string(R.string.group_total, label, integer.format(roll.sum)), 14, foreground, null).setTypeface(null, Typeface.BOLD);
        StringBuilder values = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) values.append(", ");
            values.append(integer.format(roll.values.get(i)));
        }
        addText(content, values.toString(), 14, foreground, tag + "_values");
        if (pageCount > 1) {
            addText(content, string(R.string.dice_page_range, start + 1, end, roll.values.size()), 12, foreground, null);
            content.addView(pageControls(batchId, page, pageCount, tag, pageKey));
        }
        return content;
    }

    private View pageControls(String batchId, int page, int pageCount, String tag, String key) {
        FlowLayout row = ui.flow();
        Button previous = ui.button(string(R.string.previous_page), tag + "_previous", () -> setPage(batchId, key, page - 1), false);
        previous.setEnabled(page > 0); row.addView(previous);
        Button next = ui.button(string(R.string.next_page), tag + "_next", () -> setPage(batchId, key, page + 1), false);
        next.setEnabled(page + 1 < pageCount); row.addView(next);
        return row;
    }

    private TextView addText(LinearLayout parent, CharSequence text, int size, int color, String tag) {
        TextView view = ui.text(text, size, color); if (tag != null) view.setTag(tag);
        parent.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ui.addGap(parent, 4); return view;
    }
    private void divider(LinearLayout parent) {
        ui.addGap(parent, 6); View line = new View(ui.context); line.setBackgroundColor(ui.colors.outlineVariant);
        parent.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(1))); ui.addGap(parent, 8);
    }
    private String string(int resource, Object... values) { return ui.context.getString(resource, values); }
    private String modeText(RollMode mode) {
        return string(mode == RollMode.NORMAL ? R.string.mode_normal : mode == RollMode.ADVANTAGE ? R.string.mode_advantage : R.string.mode_disadvantage);
    }
    private void toggle(String batchId, String key, boolean oldValue) {
        presentation.putBoolean(key, !oldValue); invalidated.add(batchId); actions.changed();
    }
    private void setPage(String batchId, String key, int value) {
        presentation.putInt(key, value); invalidated.add(batchId); actions.changed();
    }
}
