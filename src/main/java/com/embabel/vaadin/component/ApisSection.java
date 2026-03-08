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

import com.embabel.agent.api.client.AuthRequirement;
import com.embabel.agent.api.client.LearnedApi;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Section displaying loaded APIs with name, description, and auth info.
 */
public class ApisSection extends VerticalLayout {

    public ApisSection(List<LearnedApi> apis) {
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

    private Div createApiCard(LearnedApi api) {
        var card = new Div();
        card.addClassName("api-card");

        var name = new Span(api.getName());
        name.addClassName("api-card-name");
        card.add(name);

        var description = api.getDescription();
        if (description != null && !description.isBlank()) {
            var desc = new Span(description);
            desc.addClassName("api-card-desc");
            card.add(desc);
        }

        var authInfo = formatAuth(api.getAuthRequirements());
        if (!authInfo.isBlank()) {
            var auth = new Span(authInfo);
            auth.addClassName("api-card-auth");
            card.add(auth);
        }

        return card;
    }

    private String formatAuth(List<AuthRequirement> requirements) {
        return requirements.stream()
                .filter(r -> !(r instanceof AuthRequirement.None))
                .map(this::formatRequirement)
                .collect(Collectors.joining(", "));
    }

    private String formatRequirement(AuthRequirement requirement) {
        if (requirement instanceof AuthRequirement.ApiKey apiKey) {
            return "API Key: " + apiKey.getName() + " (" + apiKey.getLocation() + ")";
        } else if (requirement instanceof AuthRequirement.Bearer bearer) {
            return "Bearer (" + bearer.getScheme() + ")";
        } else if (requirement instanceof AuthRequirement.OAuth2 oauth2) {
            var scopes = oauth2.getScopes();
            return scopes.isEmpty() ? "OAuth2" : "OAuth2 [" + String.join(", ", scopes) + "]";
        }
        return requirement.toString();
    }
}
