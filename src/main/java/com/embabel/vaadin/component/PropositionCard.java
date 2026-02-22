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
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Card component displaying a single proposition with its metadata.
 */
public class PropositionCard extends Div {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Proposition proposition;
    private Consumer<Proposition> onDelete;
    private final Function<String, NamedEntity> entityResolver;

    public PropositionCard(Proposition prop, Function<String, NamedEntity> entityResolver) {
        this.proposition = prop;
        this.entityResolver = entityResolver;
        addClassName("proposition-card");

        var headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setSpacing(true);
        headerLayout.addClassName("proposition-header");

        var textSpan = new Span(prop.getText());
        textSpan.addClassName("proposition-text");

        var deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.addClassName("proposition-delete");
        deleteButton.getElement().setAttribute("title", "Delete this memory");
        deleteButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(proposition);
            }
        });

        headerLayout.add(textSpan, deleteButton);
        headerLayout.setFlexGrow(1, textSpan);

        var metaLayout = new HorizontalLayout();
        metaLayout.setSpacing(true);
        metaLayout.addClassName("proposition-meta");

        var confidencePercent = (int) (prop.getConfidence() * 100);
        var confidenceSpan = new Span(confidencePercent + "% confidence");
        confidenceSpan.addClassName("proposition-confidence");
        confidenceSpan.addClassName(confidencePercent >= 80 ? "high" :
                confidencePercent >= 50 ? "medium" : "low");

        var timeSpan = new Span(TIME_FORMATTER.format(prop.getCreated()));
        timeSpan.addClassName("proposition-time");

        metaLayout.add(confidenceSpan, timeSpan);

        var mentions = prop.getMentions();
        if (!mentions.isEmpty()) {
            var entitiesLayout = new HorizontalLayout();
            entitiesLayout.setSpacing(false);
            entitiesLayout.addClassName("proposition-entities");

            for (var mention : mentions) {
                entitiesLayout.add(createMentionBadge(mention));
            }
            add(headerLayout, metaLayout, entitiesLayout);
        } else {
            add(headerLayout, metaLayout);
        }
    }

    private Span createMentionBadge(EntityMention mention) {
        String label;
        NamedEntity resolved = null;

        if (mention.getResolvedId() != null && entityResolver != null) {
            resolved = entityResolver.apply(mention.getResolvedId());
        }

        if (resolved != null) {
            label = resolved.getName();
        } else {
            // Fallback: show span text or type, with ? to indicate unresolved
            var base = mention.getSpan() != null ? mention.getSpan() : mention.getType();
            label = base + " ?";
        }

        var badge = new Span(label);
        badge.addClassName("mention-badge");

        if (resolved != null) {
            var entity = resolved;
            badge.addClassName("clickable");
            badge.getElement().addEventListener("click", e -> showEntityDialog(entity));
        } else {
            badge.addClassName("unresolved");
        }

        return badge;
    }

    private void showEntityDialog(NamedEntity entity) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(entity.getName());
        dialog.setWidth("400px");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.addDialogCloseActionListener(e -> dialog.close());
        dialog.add(new EntityPanel(entity));
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    public void setOnDelete(Consumer<Proposition> handler) {
        this.onDelete = handler;
    }

    public Proposition getProposition() {
        return proposition;
    }
}
