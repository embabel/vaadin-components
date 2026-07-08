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

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.agent.rag.model.NamedEntityData;
import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Reusable panel displaying a resolved entity with its type, name, and description.
 */
public class EntityPanel extends Div {

    private final NamedEntity entity;

    /**
     * Convenience constructor that doesn't explain related memories.
     */
    public EntityPanel(NamedEntity entity) {
        this(entity, null);
    }

    /**
     * Main constructor. When relatedPropositionsLoader is provided, renders a collapsible
     * section showing memories that mention this entity.
     *
     * @param entity the entity to display
     * @param relatedPropositionsLoader looks up propositions mentioning this entity's id,
     *                                  or null to omit the related-memories section
     */
    public EntityPanel(NamedEntity entity, Function<String, List<Proposition>> relatedPropositionsLoader) {
        this.entity = entity;
        addClassName("entity-card");

        var headerLayout = new HorizontalLayout();
        headerLayout.setSpacing(true);
        headerLayout.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerLayout.addClassName("entity-header");

        var typeBadge = new Span(getPrimaryLabel(entity.labels()));
        typeBadge.addClassName("entity-type-badge");

        var nameSpan = new Span(entity.getName());
        nameSpan.addClassName("entity-name");

        headerLayout.add(typeBadge, nameSpan);

        var descSpan = new Span(entity.getDescription());
        descSpan.addClassName("entity-description");

        var idSpan = new Span("id: " + entity.getId());
        idSpan.addClassName("entity-id");

        add(headerLayout, descSpan, idSpan);

        if (relatedPropositionsLoader != null) {
            var relatedPropositions = relatedPropositionsLoader.apply(entity.getId());
            if (relatedPropositions != null && !relatedPropositions.isEmpty()) {
                add(createRelatedSection(relatedPropositions));
            }
        }
    }

    private Details createRelatedSection(List<Proposition> propositions) {
        var content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.addClassName("entity-related-content");

        for (var prop : propositions) {
            var itemSpan = new Span(prop.getText());
            itemSpan.addClassName("entity-related-item");
            content.add(itemSpan);
        }

        var summary = new Span("Mentioned in " + propositions.size() + " memor"
                + (propositions.size() == 1 ? "y" : "ies"));
        var details = new Details(summary, content);
        details.setOpened(false);
        details.addClassName("entity-related-section");

        return details;
    }

    private String getPrimaryLabel(Set<String> labels) {
        return labels.stream()
                .filter(l -> !l.equals(NamedEntityData.ENTITY_LABEL) && !l.equals("Reference"))
                .findFirst()
                .orElse(labels.stream().findFirst().orElse(NamedEntityData.ENTITY_LABEL));
    }

    public NamedEntity getEntity() {
        return entity;
    }
}
