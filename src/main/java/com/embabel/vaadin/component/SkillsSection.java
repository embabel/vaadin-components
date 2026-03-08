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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;

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
        setPadding(true);
        setSpacing(true);

        var title = new H4("Skills (" + skills.size() + ")");
        title.addClassName("section-title");
        add(title);

        if (skills.isEmpty()) {
            var emptyLabel = new Span("No skills loaded");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var skill : skills) {
            add(createSkillCard(skill));
        }
    }

    private Div createSkillCard(SkillInfo skill) {
        var card = new Div();
        card.addClassName("skill-card");

        var name = new Span(skill.name());
        name.addClassName("skill-card-name");
        card.add(name);

        if (skill.description() != null && !skill.description().isBlank()) {
            var desc = new Span(skill.description());
            desc.addClassName("skill-card-desc");
            card.add(desc);
        }

        return card;
    }
}
