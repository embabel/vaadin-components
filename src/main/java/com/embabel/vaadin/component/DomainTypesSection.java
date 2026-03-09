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

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DomainTypePropertyDefinition;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.PropertyDefinition;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Collection;
import java.util.Comparator;

/**
 * Section displaying domain types with expandable cards showing properties.
 * Accepts a collection of {@link DomainType} instances — static and dynamic
 * types are shown together, sorted alphabetically. Dynamic types are
 * visually distinguished with an italic name and a "dynamic" badge.
 */
public class DomainTypesSection extends VerticalLayout {

    public DomainTypesSection(Collection<? extends DomainType> domainTypes) {
        setPadding(true);
        setSpacing(true);

        var types = domainTypes.stream()
                .sorted(Comparator.comparing(DomainType::getOwnLabel, String.CASE_INSENSITIVE_ORDER))
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
            add(createTypeDetails(type));
        }
    }

    private Details createTypeDetails(DomainType type) {
        boolean isDynamic = type instanceof DynamicType;

        // Summary line: name + badges
        var summaryLayout = new HorizontalLayout();
        summaryLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        summaryLayout.setSpacing(true);
        summaryLayout.setPadding(false);

        var nameSpan = new Span(type.getOwnLabel());
        nameSpan.addClassName("domain-type-name");
        if (isDynamic) {
            nameSpan.getStyle().set("font-style", "italic");
        }
        summaryLayout.add(nameSpan);

        if (isDynamic) {
            var dynamicBadge = new Span("dynamic");
            dynamicBadge.addClassName("domain-type-badge-dynamic");
            summaryLayout.add(dynamicBadge);
        }

        int propCount = type.getProperties().size();
        if (propCount > 0) {
            var propBadge = new Span(propCount + " props");
            propBadge.addClassName("domain-type-props-badge");
            summaryLayout.add(propBadge);
        }

        // Content: description + properties + relationships
        var contentLayout = new VerticalLayout();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(false);
        contentLayout.addClassName("domain-type-content");

        var desc = type.getDescription();
        if (!desc.equals(type.getOwnLabel()) && !desc.equals(type.getName())) {
            var descSpan = new Span(desc);
            descSpan.addClassName("domain-type-description");
            contentLayout.add(descSpan);
        }

        // Value properties
        var values = type.getValues();
        if (!values.isEmpty()) {
            var propsDiv = new Div();
            propsDiv.addClassName("domain-type-properties");
            for (var prop : values.stream().sorted(Comparator.comparing(PropertyDefinition::getName)).toList()) {
                var propDiv = new Div();
                propDiv.addClassName("domain-type-property");

                var propName = new Span(prop.getName());
                propName.addClassName("property-name");
                propDiv.add(propName);

                if (!prop.getDescription().isBlank() && !prop.getDescription().equals(prop.getName())) {
                    var propDesc = new Span(prop.getDescription());
                    propDesc.addClassName("property-description");
                    propDiv.add(propDesc);
                }
                propsDiv.add(propDiv);
            }
            contentLayout.add(propsDiv);
        }

        // Relationships
        var rels = type.getRelationships();
        if (!rels.isEmpty()) {
            var relsDiv = new Div();
            relsDiv.addClassName("schema-rels");
            for (var rel : rels) {
                var relSpan = new Span(rel.getName() + " -> " + rel.getType().getOwnLabel());
                relSpan.addClassName("schema-rel");
                relsDiv.add(relSpan);
            }
            contentLayout.add(relsDiv);
        }

        var details = new Details(summaryLayout, contentLayout);
        details.setOpened(false);
        details.addClassName("domain-type-item");
        return details;
    }
}
