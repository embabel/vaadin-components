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

import com.embabel.agent.core.*;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Schema section displaying all domain types from the DataDictionary.
 */
public class SchemaSection extends VerticalLayout {

    public SchemaSection(DataDictionary dataDictionary) {
        setPadding(true);
        setSpacing(true);

        var types = dataDictionary.getDomainTypes().stream()
                .sorted((a, b) -> a.getOwnLabel().compareToIgnoreCase(b.getOwnLabel()))
                .toList();

        var title = new H4("Schema (" + types.size() + " types)");
        title.addClassName("section-title");
        add(title);

        if (types.isEmpty()) {
            var emptyLabel = new Span("No domain types registered");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var type : types) {
            add(createTypeCard(type));
        }
    }

    private Div createTypeCard(DomainType type) {
        var card = new Div();
        card.addClassName("schema-type-card");

        var label = new Span(type.getOwnLabel());
        label.addClassName("schema-type-label");
        card.add(label);

        var desc = type.getDescription();
        if (!desc.equals(type.getOwnLabel())) {
            var descSpan = new Span(desc);
            descSpan.addClassName("schema-type-desc");
            card.add(descSpan);
        }

        var props = type.getValues();
        if (!props.isEmpty()) {
            var propsDiv = new Div();
            propsDiv.addClassName("schema-props");
            for (var prop : props) {
                var propSpan = new Span(prop.getName());
                propSpan.addClassName("schema-prop");
                propsDiv.add(propSpan);
            }
            card.add(propsDiv);
        }

        var rels = type.getRelationships();
        if (!rels.isEmpty()) {
            var relsDiv = new Div();
            relsDiv.addClassName("schema-rels");
            for (var rel : rels) {
                var relSpan = new Span(rel.getName() + " -> " + rel.getType().getOwnLabel());
                relSpan.addClassName("schema-rel");
                relsDiv.add(relSpan);
            }
            card.add(relsDiv);
        }

        return card;
    }
}
