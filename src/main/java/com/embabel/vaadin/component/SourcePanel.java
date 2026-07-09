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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.List;
import java.util.function.Consumer;

/**
 * Renders a single ingested source (email, document, connector message, etc.) along with
 * the memories derived from it. Host-agnostic: holds no REST, repository, or me-specific types.
 */
public class SourcePanel extends Div {

    /**
     * One source being rendered: its id, type ("email", "document", etc.), title, and
     * the memories and entities derived from it.
     */
    public record SourceDetail(
            String key,
            String kind,
            String title,
            List<DerivedProp> derived,
            List<EntityChip> entities
    ) {}

    /**
     * A memory (proposition) derived from the source.
     */
    public record DerivedProp(String propositionId, String text) {}

    /**
     * An entity mentioned in the source and derived memories.
     */
    public record EntityChip(String entityId, String label) {}

    private Consumer<String> onOpenEntity;
    private Runnable onClose;

    /**
     * Create a source panel rendering the given source detail.
     *
     * @param detail the source and its derived data to display
     */
    public SourcePanel(SourceDetail detail) {
        addClassName("source-panel");
        getStyle().set("display", "flex");
        getStyle().set("flex-direction", "column");
        getStyle().set("gap", "16px");
        getStyle().set("padding", "16px 22px");

        // Header: title + kind badge + optional close button
        var headerDiv = new Div();
        headerDiv.addClassName("source-header");
        headerDiv.getStyle().set("display", "flex");
        headerDiv.getStyle().set("align-items", "center");
        headerDiv.getStyle().set("gap", "10px");
        headerDiv.getStyle().set("padding-bottom", "12px");
        headerDiv.getStyle().set("border-bottom", "1px solid var(--lumo-border-color)");

        // Title
        var titleSpan = new Span(detail.title());
        titleSpan.addClassName("source-title");
        titleSpan.getStyle().set("font-size", "16px");
        titleSpan.getStyle().set("font-weight", "650");
        titleSpan.getStyle().set("margin", "0");

        // Kind badge
        var kindBadge = new Span(detail.kind());
        kindBadge.addClassName("source-kind-badge");
        kindBadge.getStyle().set("font-size", "10.5px");
        kindBadge.getStyle().set("font-weight", "600");
        kindBadge.getStyle().set("text-transform", "uppercase");
        kindBadge.getStyle().set("letter-spacing", ".03em");
        kindBadge.getStyle().set("padding", "2px 8px");
        kindBadge.getStyle().set("border-radius", "999px");
        kindBadge.getStyle().set("background", "var(--lumo-primary-color-10pct)");
        kindBadge.getStyle().set("color", "var(--lumo-primary-color)");
        kindBadge.getStyle().set("flex-shrink", "0");

        headerDiv.add(titleSpan, kindBadge);

        // Close button (flexes to the right)
        if (true) {  // close button is always present structurally but only active when handler set
            var spacer = new Div();
            spacer.getStyle().set("flex", "1");
            headerDiv.add(spacer);

            var closeBtn = new Div();
            closeBtn.addClassName("source-close");
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
            headerDiv.add(closeBtn);
        }

        add(headerDiv);

        // Derived memories section
        if (detail.derived() != null && !detail.derived().isEmpty()) {
            var derivedSection = new Div();
            derivedSection.addClassName("source-derived-section");
            derivedSection.getStyle().set("display", "flex");
            derivedSection.getStyle().set("flex-direction", "column");
            derivedSection.getStyle().set("gap", "8px");

            var derivedTitle = new Span("Derived " + detail.derived().size() + " memor"
                    + (detail.derived().size() == 1 ? "y" : "ies"));
            derivedTitle.addClassName("source-derived-title");
            derivedTitle.getStyle().set("font-size", "10.5px");
            derivedTitle.getStyle().set("text-transform", "uppercase");
            derivedTitle.getStyle().set("letter-spacing", ".04em");
            derivedTitle.getStyle().set("color", "var(--lumo-tertiary-text-color)");
            derivedTitle.getStyle().set("font-weight", "600");
            derivedTitle.getStyle().set("margin", "0");

            derivedSection.add(derivedTitle);

            for (var prop : detail.derived()) {
                var cardDiv = new Div();
                cardDiv.addClassName("source-derived-card");
                cardDiv.getStyle().set("display", "flex");
                cardDiv.getStyle().set("align-items", "center");
                cardDiv.getStyle().set("padding", "8px 10px");
                cardDiv.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
                cardDiv.getStyle().set("border-radius", "6px");
                cardDiv.getStyle().set("background", "var(--lumo-contrast-5pct)");
                cardDiv.getStyle().set("font-size", "12px");
                cardDiv.getStyle().set("overflow", "hidden");
                cardDiv.getStyle().set("text-overflow", "ellipsis");
                cardDiv.getStyle().set("white-space", "nowrap");

                var text = new Span(prop.text());
                text.getStyle().set("overflow", "hidden");
                text.getStyle().set("text-overflow", "ellipsis");
                text.getStyle().set("white-space", "nowrap");

                cardDiv.add(text);
                derivedSection.add(cardDiv);
            }

            add(derivedSection);
        }

        // Mentions (entity chips) row
        if (detail.entities() != null && !detail.entities().isEmpty()) {
            var mentionsDiv = new Div();
            mentionsDiv.addClassName("source-mentions");
            mentionsDiv.getStyle().set("display", "flex");
            mentionsDiv.getStyle().set("gap", "6px");
            mentionsDiv.getStyle().set("flex-wrap", "wrap");

            for (var entity : detail.entities()) {
                var chip = new Button(entity.label());
                chip.addClassName("source-entity-chip");
                chip.getStyle().set("display", "inline-flex");
                chip.getStyle().set("align-items", "center");
                chip.getStyle().set("font-size", "11px");
                chip.getStyle().set("font-weight", "600");
                chip.getStyle().set("padding", "4px 9px");
                chip.getStyle().set("border-radius", "999px");
                chip.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
                chip.getStyle().set("background", "var(--lumo-contrast-5pct)");
                chip.getStyle().set("color", "var(--lumo-secondary-text-color)");
                chip.getStyle().set("cursor", "pointer");

                final String entityId = entity.entityId();
                chip.addClickListener(e -> {
                    if (onOpenEntity != null) {
                        onOpenEntity.accept(entityId);
                    }
                });

                mentionsDiv.add(chip);
            }

            add(mentionsDiv);
        }
    }

    /**
     * Set a handler to be called when an entity chip is clicked.
     *
     * @param handler receives the clicked chip's entityId, or null to disable
     */
    public void setOnOpenEntity(Consumer<String> handler) {
        this.onOpenEntity = handler;
    }

    /**
     * Set a handler to be called when the close button is clicked.
     *
     * @param handler runnable to invoke when the close button is clicked, or null to disable
     */
    public void setOnClose(Runnable handler) {
        this.onClose = handler;
    }
}
