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
 * Section displaying loaded APIs with name, description, spec URL, and auth info.
 */
public class ApisSection extends VerticalLayout {

    /**
     * Generic API info for display purposes.
     */
    public record ApiInfo(String name, String description, String specUrl, String auth) {
    }

    public ApisSection(List<ApiInfo> apis) {
        setPadding(true);
        setSpacing(true);

        var title = new H4("APIs (" + apis.size() + ")");
        title.addClassName("section-title");
        add(title);

        if (apis.isEmpty()) {
            var emptyLabel = new Span("No APIs loaded");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var api : apis) {
            add(createApiCard(api));
        }
    }

    private Div createApiCard(ApiInfo api) {
        var card = new Div();
        card.addClassName("api-card");

        var name = new Span(api.name());
        name.addClassName("api-card-name");
        card.add(name);

        if (api.description() != null && !api.description().isBlank()) {
            var desc = new Span(api.description());
            desc.addClassName("api-card-desc");
            card.add(desc);
        }

        var url = new Span(api.specUrl());
        url.addClassName("api-card-url");
        card.add(url);

        if (api.auth() != null && !api.auth().isBlank()) {
            var auth = new Span(api.auth());
            auth.addClassName("api-card-auth");
            card.add(auth);
        }

        return card;
    }
}
