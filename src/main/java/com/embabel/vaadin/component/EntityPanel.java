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
    private Runnable onClose;

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
        addClassName("entity-panel-360");

        // Use inline styles to apply Lumo-based design matching the entity-360 mock
        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("gap", "18px");
        getStyle().set("padding", "16px 22px 22px");

        // Header section
        var headerDiv = new Div();
        headerDiv.addClassName("entity-header-360");
        headerDiv.getStyle().set("display", "flex");
        headerDiv.getStyle().set("align-items", "flex-start");
        headerDiv.getStyle().set("gap", "14px");
        headerDiv.getStyle().set("padding-bottom", "16px");
        headerDiv.getStyle().set("border-bottom", "1px solid var(--lumo-border-color)");

        // Avatar
        var avatar = new Div();
        avatar.addClassName("entity-avatar");
        avatar.getStyle().set("width", "44px");
        avatar.getStyle().set("height", "44px");
        avatar.getStyle().set("border-radius", "50%");
        avatar.getStyle().set("background", "var(--lumo-primary-color-10pct)");
        avatar.getStyle().set("color", "var(--lumo-primary-color)");
        avatar.getStyle().set("display", "flex");
        avatar.getStyle().set("align-items", "center");
        avatar.getStyle().set("justify-content", "center");
        avatar.getStyle().set("font-weight", "700");
        avatar.getStyle().set("font-size", "15px");
        avatar.getStyle().set("flex-shrink", "0");
        String initials = entity.getName().split("\\s+").length >= 2
            ? entity.getName().split("\\s+")[0].substring(0, 1) + entity.getName().split("\\s+")[1].substring(0, 1)
            : entity.getName().substring(0, Math.min(2, entity.getName().length())).toUpperCase();
        avatar.setText(initials.toUpperCase());

        // Header main content
        var headerMain = new Div();
        headerMain.getStyle().set("flex", "1");
        headerMain.getStyle().set("min-width", "0");

        var nameRow = new Div();
        nameRow.getStyle().set("display", "flex");
        nameRow.getStyle().set("align-items", "center");
        nameRow.getStyle().set("gap", "8px");
        nameRow.getStyle().set("margin-bottom", "4px");

        var nameSpan = new Span(entity.getName());
        nameSpan.getStyle().set("font-size", "16px");
        nameSpan.getStyle().set("font-weight", "650");
        nameSpan.getStyle().set("margin", "0");

        var typeBadge = new Span(getPrimaryLabel(entity.labels()));
        typeBadge.addClassName("entity-type-badge-360");
        typeBadge.getStyle().set("font-size", "10.5px");
        typeBadge.getStyle().set("font-weight", "600");
        typeBadge.getStyle().set("text-transform", "uppercase");
        typeBadge.getStyle().set("letter-spacing", ".03em");
        typeBadge.getStyle().set("padding", "2px 8px");
        typeBadge.getStyle().set("border-radius", "999px");
        typeBadge.getStyle().set("background", "var(--lumo-primary-color-10pct)");
        typeBadge.getStyle().set("color", "var(--lumo-primary-color)");

        nameRow.add(nameSpan, typeBadge);

        var descSpan = new Span(entity.getDescription());
        descSpan.addClassName("entity-description-360");
        descSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        descSpan.getStyle().set("font-size", "12.5px");
        descSpan.getStyle().set("margin", "0");

        headerMain.add(nameRow, descSpan);

        // Close button
        var closeBtn = new Div();
        closeBtn.addClassName("entity-close-btn");
        closeBtn.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        closeBtn.getStyle().set("cursor", "pointer");
        closeBtn.getStyle().set("padding", "2px");
        closeBtn.getStyle().set("flex-shrink", "0");
        closeBtn.setText("✕");
        closeBtn.getStyle().set("font-size", "16px");
        closeBtn.getStyle().set("line-height", "1");
        closeBtn.getElement().addEventListener("click", e -> {
            if (onClose != null) {
                onClose.run();
            }
        });

        headerDiv.add(avatar, headerMain, closeBtn);
        add(headerDiv);

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
        content.setSpacing(false);
        content.addClassName("entity-related-content");
        content.getStyle().set("gap", "6px");

        for (var prop : propositions) {
            var itemSpan = new Span(prop.getText());
            itemSpan.addClassName("entity-related-item");
            itemSpan.getStyle().set("display", "flex");
            itemSpan.getStyle().set("align-items", "center");
            itemSpan.getStyle().set("gap", "10px");
            itemSpan.getStyle().set("padding", "8px 10px");
            itemSpan.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
            itemSpan.getStyle().set("border-radius", "6px");
            itemSpan.getStyle().set("background", "var(--lumo-contrast-5pct)");
            itemSpan.getStyle().set("font-size", "12px");
            itemSpan.getStyle().set("overflow", "hidden");
            itemSpan.getStyle().set("text-overflow", "ellipsis");
            itemSpan.getStyle().set("white-space", "nowrap");

            content.add(itemSpan);
        }

        var summaryText = "Mentioned in " + propositions.size() + " memor"
                + (propositions.size() == 1 ? "y" : "ies");
        var summary = new Span(summaryText);
        summary.getStyle().set("font-size", "10.5px");
        summary.getStyle().set("text-transform", "uppercase");
        summary.getStyle().set("letter-spacing", ".04em");
        summary.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        summary.getStyle().set("font-weight", "600");

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

    /**
     * Set handler to be called when the close button is clicked.
     *
     * @param onClose runnable to invoke when user clicks the close X button, or null to disable
     */
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public NamedEntity getEntity() {
        return entity;
    }
}
