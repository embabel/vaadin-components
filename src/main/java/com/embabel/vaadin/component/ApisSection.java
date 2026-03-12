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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Section displaying loaded APIs with a two-step form to learn new APIs.
 */
public class ApisSection extends VerticalLayout {

    /**
     * Generic API info for display purposes.
     */
    public record ApiInfo(String name, String description, String specUrl, String auth) {
    }

    /**
     * Result of learning an API from a spec URL.
     */
    public record LearnResult(String name, String description, List<String> authRequirements) {
    }

    /**
     * Request to save a new API entry.
     */
    public record SaveRequest(String specUrl, String auth, String tokenEnv) {
    }

    private final VerticalLayout cardsLayout = new VerticalLayout();
    private final VerticalLayout formArea = new VerticalLayout();

    public ApisSection(
            List<ApiInfo> apis,
            Function<String, LearnResult> onLearn,
            Consumer<SaveRequest> onSave
    ) {
        setPadding(true);
        setSpacing(true);

        var title = new H4("APIs (" + apis.size() + ")");
        title.addClassName("section-title");
        add(title);

        // Add API form
        formArea.setPadding(false);
        formArea.setSpacing(false);
        buildStep1Form(onLearn, onSave);
        add(formArea);

        // Cards
        cardsLayout.setPadding(false);
        cardsLayout.setSpacing(false);
        add(cardsLayout);

        if (apis.isEmpty()) {
            var emptyLabel = new Span("No APIs loaded");
            emptyLabel.addClassName("empty-list-label");
            cardsLayout.add(emptyLabel);
        } else {
            for (var api : apis) {
                cardsLayout.add(createApiCard(api));
            }
        }
    }

    private void buildStep1Form(Function<String, LearnResult> onLearn, Consumer<SaveRequest> onSave) {
        formArea.removeAll();

        var urlField = new TextField();
        urlField.setPlaceholder("OpenAPI spec URL");
        urlField.setWidthFull();
        urlField.setClearButtonVisible(true);
        urlField.addClassName("api-learn-url");

        var learnButton = new Button("Learn", VaadinIcon.SEARCH.create());
        learnButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        var row = new HorizontalLayout(urlField, learnButton);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setFlexGrow(1.0, urlField);
        row.setPadding(false);

        formArea.add(row);

        learnButton.addClickListener(e -> {
            var url = urlField.getValue();
            if (url == null || url.isBlank()) {
                urlField.setInvalid(true);
                urlField.setErrorMessage("Enter a spec URL");
                return;
            }
            urlField.setInvalid(false);
            learnApi(url.trim(), onLearn, onSave);
        });
    }

    private void learnApi(String url, Function<String, LearnResult> onLearn, Consumer<SaveRequest> onSave) {
        formArea.removeAll();

        var progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.addClassName("api-learn-progress");

        var statusText = new Span("Learning API from spec...");
        statusText.addClassName("api-learn-status");

        formArea.add(progressBar, statusText);

        var ui = UI.getCurrent();
        Thread.startVirtualThread(() -> {
            try {
                var result = onLearn.apply(url);
                ui.access(() -> buildStep2Form(url, result, onLearn, onSave));
            } catch (Exception ex) {
                ui.access(() -> {
                    formArea.removeAll();
                    var error = new Span("Failed: " + ex.getMessage());
                    error.addClassName("api-learn-error");
                    formArea.add(error);

                    var retryButton = new Button("Try again", VaadinIcon.REFRESH.create());
                    retryButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    retryButton.addClickListener(ev -> buildStep1Form(onLearn, onSave));
                    formArea.add(retryButton);
                });
            }
        });
    }

    private void buildStep2Form(
            String url,
            LearnResult result,
            Function<String, LearnResult> onLearn,
            Consumer<SaveRequest> onSave
    ) {
        formArea.removeAll();

        var resultCard = new Div();
        resultCard.addClassName("api-learn-result");

        var name = new Span(result.name());
        name.addClassName("api-card-name");
        resultCard.add(name);

        if (result.description() != null && !result.description().isBlank()) {
            var desc = new Span(result.description());
            desc.addClassName("api-card-desc");
            resultCard.add(desc);
        }

        var specUrl = new Span(url);
        specUrl.addClassName("api-card-url");
        resultCard.add(specUrl);

        if (!result.authRequirements().isEmpty()) {
            var authLabel = new Span("Requires: " + String.join(", ", result.authRequirements()));
            authLabel.addClassName("api-learn-auth-info");
            resultCard.add(authLabel);
        }

        formArea.add(resultCard);

        // Auth config
        var authCombo = new ComboBox<String>("Auth type");
        authCombo.setItems("none", "api-key", "bearer");
        authCombo.setWidthFull();
        authCombo.addClassName("api-learn-auth-combo");

        var tokenEnvField = new TextField("Token env variable");
        tokenEnvField.setPlaceholder("e.g. MY_API_KEY");
        tokenEnvField.setWidthFull();
        tokenEnvField.setVisible(false);
        tokenEnvField.addClassName("api-learn-token-env");

        // Pre-select auth based on what was detected
        if (result.authRequirements().isEmpty()) {
            authCombo.setValue("none");
        } else {
            var first = result.authRequirements().get(0).toLowerCase();
            if (first.contains("api key")) {
                authCombo.setValue("api-key");
                tokenEnvField.setVisible(true);
            } else if (first.contains("bearer")) {
                authCombo.setValue("bearer");
                tokenEnvField.setVisible(true);
            } else {
                authCombo.setValue("none");
            }
        }

        authCombo.addValueChangeListener(e -> {
            var val_ = e.getValue();
            tokenEnvField.setVisible(val_ != null && !val_.equals("none"));
        });

        formArea.add(authCombo, tokenEnvField);

        // Buttons
        var saveButton = new Button("Save", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        var cancelButton = new Button("Cancel");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        cancelButton.addClickListener(e -> buildStep1Form(onLearn, onSave));

        var buttons = new HorizontalLayout(saveButton, cancelButton);
        buttons.setPadding(false);
        formArea.add(buttons);

        saveButton.addClickListener(e -> {
            var auth = authCombo.getValue();
            if (auth == null) auth = "none";
            var tokenEnv = tokenEnvField.getValue();
            if (!auth.equals("none") && (tokenEnv == null || tokenEnv.isBlank())) {
                tokenEnvField.setInvalid(true);
                tokenEnvField.setErrorMessage("Required for " + auth + " auth");
                return;
            }
            try {
                onSave.accept(new SaveRequest(url, auth, auth.equals("none") ? null : tokenEnv.trim()));
                Notification.show("API saved — refresh workspace to activate",
                        3000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                buildStep1Form(onLearn, onSave);
            } catch (Exception ex) {
                Notification.show("Failed to save: " + ex.getMessage(),
                        5000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
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
