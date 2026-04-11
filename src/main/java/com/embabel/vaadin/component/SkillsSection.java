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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

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

    public SkillsSection(List<SkillInfo> skills, Consumer<String> onDelete, Runnable onRefreshGitHub) {
        setPadding(true);
        setSpacing(true);

        var titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);

        var title = new H4("Skills (" + skills.size() + ")");
        title.addClassName("section-title");
        titleRow.add(title);
        titleRow.setFlexGrow(1, title);

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
