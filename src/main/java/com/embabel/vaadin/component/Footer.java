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

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.time.Year;

/**
 * Footer component showing copyright and optional statistics.
 */
public class Footer extends HorizontalLayout {

    public Footer() {
        this(null);
    }

    public Footer(String stats) {
        setWidthFull();
        setPadding(false);
        setSpacing(true);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName("app-footer");

        var copyright = new Span("\u00a9 Embabel " + Year.now().getValue());
        copyright.addClassName("footer-copyright");

        add(copyright);

        if (stats != null && !stats.isBlank()) {
            var separator = new Span("\u00b7");
            separator.addClassName("footer-separator");

            var statsSpan = new Span(stats);
            statsSpan.addClassName("footer-stats");

            add(separator, statsSpan);
        }
    }
}
