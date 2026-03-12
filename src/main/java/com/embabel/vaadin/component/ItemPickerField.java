/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.vaadin.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.*;

/**
 * Reusable picker that opens a dialog showing items with name and description,
 * each with a checkbox. Displays a summary button showing selected count.
 * <p>
 * Used for selecting tools, references/skills, and other named items
 * in action compiler, cron, and webhook UIs.
 */
public class ItemPickerField extends Div {

    public record Item(String name, String description) {}

    private final List<Item> options;
    private final Set<String> selected = new LinkedHashSet<>();
    private final Button triggerButton;
    private final String label;

    public ItemPickerField(String label, List<Item> options) {
        this.label = label;
        this.options = List.copyOf(options);

        triggerButton = new Button(formatLabel(0));
        triggerButton.addClassName("item-picker-trigger");
        triggerButton.addClickListener(e -> openDialog());
        add(triggerButton);
    }

    private void openDialog() {
        var dialog = new Dialog();
        dialog.setHeaderTitle(label);
        dialog.setWidth("500px");
        dialog.setMaxHeight("80vh");

        // Search filter
        var search = new TextField();
        search.setPlaceholder("Filter...");
        search.setWidthFull();
        search.setClearButtonVisible(true);
        search.addClassName("item-picker-search");

        // Item list
        var itemList = new VerticalLayout();
        itemList.setPadding(false);
        itemList.setSpacing(false);
        itemList.addClassName("item-picker-list");
        itemList.getStyle().set("overflow-y", "auto");
        itemList.getStyle().set("max-height", "60vh");

        // Working copy of selections so Cancel discards changes
        var workingSelection = new LinkedHashSet<>(selected);

        // Build rows
        var rows = new ArrayList<ItemRow>();
        for (var option : options) {
            var row = new ItemRow(option, workingSelection.contains(option.name()));
            row.checkbox.addValueChangeListener(e -> {
                if (e.getValue()) {
                    workingSelection.add(option.name());
                } else {
                    workingSelection.remove(option.name());
                }
            });
            rows.add(row);
            itemList.add(row.layout);
        }

        // Filter handler
        search.addValueChangeListener(e -> {
            var filter = e.getValue().toLowerCase();
            for (var row : rows) {
                boolean matches = filter.isEmpty()
                        || row.option.name().toLowerCase().contains(filter)
                        || row.option.description().toLowerCase().contains(filter);
                row.layout.setVisible(matches);
            }
        });

        var content = new VerticalLayout(search, itemList);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);

        // Footer buttons
        var cancel = new Button("Cancel", e -> dialog.close());
        var confirm = new Button("OK", e -> {
            selected.clear();
            selected.addAll(workingSelection);
            triggerButton.setText(formatLabel(selected.size()));
            dialog.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, confirm);

        dialog.open();
    }

    public Set<String> getValue() {
        return Collections.unmodifiableSet(selected);
    }

    public void clear() {
        selected.clear();
        triggerButton.setText(formatLabel(0));
    }

    private String formatLabel(int count) {
        return count == 0 ? label + "..." : label + " (" + count + " selected)";
    }

    private record ItemRow(Item option, Checkbox checkbox, Div layout) {
        ItemRow(Item option, boolean checked) {
            this(option, new Checkbox(), createRow(option));
            checkbox.setValue(checked);
            layout.getElement().insertChild(0, checkbox.getElement());
        }

        private static Div createRow(Item option) {
            var name = new Span(option.name());
            name.addClassName("item-picker-name");
            name.getStyle().set("font-weight", "600");

            var desc = new Span(option.description());
            desc.addClassName("item-picker-desc");
            desc.getStyle().set("font-size", "var(--lumo-font-size-s)");
            desc.getStyle().set("color", "var(--lumo-secondary-text-color)");

            var textBlock = new Div(name, desc);
            textBlock.getStyle().set("display", "flex");
            textBlock.getStyle().set("flex-direction", "column");
            textBlock.getStyle().set("flex", "1");

            var row = new Div();
            row.addClassName("item-picker-row");
            row.getStyle().set("display", "flex");
            row.getStyle().set("align-items", "center");
            row.getStyle().set("gap", "var(--lumo-space-s)");
            row.getStyle().set("padding", "var(--lumo-space-xs) 0");
            row.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
            row.add(textBlock);
            return row;
        }
    }
}
