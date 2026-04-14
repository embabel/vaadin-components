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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;
import java.util.function.Consumer;

/**
 * Section displaying loaded skills with name and description.
 * Uses a generic record so this component has no dependency on the skills module.
 */
public class SkillsSection extends VerticalLayout {

    /**
     * Generic skill info for display purposes.
     */
    public record SkillInfo(String name, String description) {
    }

    public SkillsSection(List<SkillInfo> skills) {
        this(skills, null, null);
    }

    public SkillsSection(List<SkillInfo> skills, Consumer<String> onDelete) {
        this(skills, onDelete, null);
    }

    /**
     * Callback for creating a new skill.
     */
    public interface SkillCreateHandler {
        void create(String name, String description, String instructions);
    }

    public SkillsSection(List<SkillInfo> skills, Consumer<String> onDelete, Runnable onRefreshGitHub) {
        this(skills, onDelete, onRefreshGitHub, null);
    }

    public SkillsSection(List<SkillInfo> skills, Consumer<String> onDelete, Runnable onRefreshGitHub,
                          SkillCreateHandler onCreate) {
        setPadding(true);
        setSpacing(true);

        var titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);

        var title = new H4("Skills (" + skills.size() + ")");
        title.addClassName("section-title");
        titleRow.add(title);
        titleRow.setFlexGrow(1, title);

        if (onCreate != null) {
            var createBtn = new Button("Create");
            createBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            createBtn.addClickListener(e -> showCreateDialog(onCreate));
            titleRow.add(createBtn);
        }

        if (onRefreshGitHub != null) {
            var refreshBtn = new Button("Refresh");
            refreshBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            refreshBtn.addClassName("skills-refresh-btn");
            refreshBtn.addClickListener(e -> onRefreshGitHub.run());
            titleRow.add(refreshBtn);
        }

        add(titleRow);

        if (skills.isEmpty()) {
            var emptyLabel = new Span("No skills loaded");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var skill : skills) {
            add(createSkillCard(skill, onDelete));
        }
    }

    private void showCreateDialog(SkillCreateHandler onCreate) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Create Skill");
        dialog.setWidth("500px");

        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        var nameField = new TextField("Name");
        nameField.setPlaceholder("my-skill (lowercase, hyphens)");
        nameField.setWidthFull();
        nameField.setPattern("[a-z0-9][a-z0-9-]*[a-z0-9]");

        var descField = new TextField("Description");
        descField.setPlaceholder("What this skill does");
        descField.setWidthFull();

        var instructionsField = new TextArea("Instructions");
        instructionsField.setPlaceholder("Detailed instructions for the assistant...");
        instructionsField.setWidthFull();
        instructionsField.setMinHeight("150px");

        layout.add(nameField, descField, instructionsField);
        dialog.add(layout);

        var saveBtn = new Button("Create", e -> {
            var name = nameField.getValue().trim();
            var desc = descField.getValue().trim();
            var instructions = instructionsField.getValue().trim();
            if (name.isEmpty() || desc.isEmpty() || instructions.isEmpty()) return;
            onCreate.create(name, desc, instructions);
            dialog.close();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), saveBtn);
        dialog.open();
    }

    private Div createSkillCard(SkillInfo skill, Consumer<String> onDelete) {
        var card = new Div();
        card.addClassName("skill-card");

        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        var name = new Span(skill.name());
        name.addClassName("skill-card-name");
        header.add(name);
        header.setFlexGrow(1, name);

        if (onDelete != null) {
            var deleteBtn = new Button("Remove");
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteBtn.addClickListener(e -> onDelete.accept(skill.name()));
            header.add(deleteBtn);
        }

        card.add(header);

        if (skill.description() != null && !skill.description().isBlank()) {
            var desc = new Span(skill.description());
            desc.addClassName("skill-card-desc");
            card.add(desc);
        }

        return card;
    }
}
