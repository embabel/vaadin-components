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

import com.embabel.agent.rag.model.NamedEntityData;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Comparator;
import java.util.function.Supplier;

/**
 * Section displaying all entities linked to propositions in the current context.
 */
public class EntitiesSection extends VerticalLayout {

    private final NamedEntityDataRepository entityRepository;
    private final Supplier<String> contextIdSupplier;
    private final VerticalLayout entitiesContent;
    private final Span countSpan;

    public EntitiesSection(NamedEntityDataRepository entityRepository,
                           Supplier<String> contextIdSupplier) {
        this.entityRepository = entityRepository;
        this.contextIdSupplier = contextIdSupplier;

        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // Header row
        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setSpacing(true);
        headerLayout.setWidthFull();
        headerLayout.addClassName("entities-section-header");

        var titleSpan = new Span("Entities");
        titleSpan.addClassName("panel-title");

        countSpan = new Span("(0 entities)");
        countSpan.addClassName("entities-count");

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, countSpan, refreshButton);
        headerLayout.setFlexGrow(1, countSpan);

        // Scrollable content area
        entitiesContent = new VerticalLayout();
        entitiesContent.setPadding(false);
        entitiesContent.setSpacing(false);
        entitiesContent.setWidthFull();

        var scroller = new Scroller(entitiesContent);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.addClassName("panel-scroller");

        add(headerLayout, scroller);
        setFlexGrow(1, scroller);
    }

    public void refresh() {
        entitiesContent.removeAll();
        try {
            var contextId = contextIdSupplier.get();
            var entities = entityRepository.withContextScope(contextId)
                    .findByLabel(NamedEntityData.ENTITY_LABEL);

            entities.stream()
                    .sorted(Comparator.comparing(NamedEntityData::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(entity -> entitiesContent.add(new EntityPanel(entity)));

            var count = entities.size();
            countSpan.setText("(" + count + (count == 1 ? " entity" : " entities") + ")");

            if (entities.isEmpty()) {
                var emptyLabel = new Span("No entities found in this context");
                emptyLabel.addClassName("panel-empty-message");
                entitiesContent.add(emptyLabel);
            }
        } catch (Exception e) {
            var errorLabel = new Span("Failed to load entities");
            errorLabel.addClassName("panel-empty-message");
            entitiesContent.add(errorLabel);
        }
    }

    public void setContextId(String contextId) {
        // contextIdSupplier handles state; just refresh
        refresh();
    }
}
