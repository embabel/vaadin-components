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
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

import java.util.List;

/**
 * Base login view with logo, title, subtitle, and optional test credentials.
 * Subclasses should add {@code @Route}, {@code @PageTitle}, and {@code @AnonymousAllowed}.
 */
public abstract class BaseLoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    /**
     * @param appName     application name shown as title
     * @param tagline     subtitle text
     * @param logoPath    path to logo image (e.g. "images/logo.jpg")
     * @param credentials list of "username / password" strings to show as test hints, or empty
     */
    protected BaseLoginView(String appName, String tagline, String logoPath, List<String> credentials) {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loginForm.setAction("login");

        var logo = new Image(logoPath, appName);
        logo.setWidth("80px");
        logo.addClassName("login-logo");

        var titleText = new VerticalLayout();
        titleText.setPadding(false);
        titleText.setSpacing(false);

        var title = new H1(appName);
        title.addClassName("login-title");

        var subtitle = new Span(tagline);
        subtitle.addClassName("login-subtitle");

        titleText.add(title, subtitle);

        var header = new HorizontalLayout(logo, titleText);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);

        var legend = new Div();
        legend.addClassName("login-legend");
        for (var cred : credentials) {
            var span = new Span(cred);
            span.addClassName("login-legend-credentials");
            legend.add(span);
        }

        add(header, loginForm, legend);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
