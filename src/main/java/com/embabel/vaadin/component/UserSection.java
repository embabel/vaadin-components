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

import com.embabel.agent.api.identity.User;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * User section component showing clickable avatar and name.
 * No logout button in shared version — apps add their own.
 */
public class UserSection extends HorizontalLayout {

    private Runnable onClickHandler;

    public UserSection(User user, Runnable onProfileClick) {
        this.onClickHandler = onProfileClick;
        setAlignItems(FlexComponent.Alignment.CENTER);
        setSpacing(true);

        // Profile chip with avatar and name
        var profileChip = new HorizontalLayout();
        profileChip.addClassName("profile-chip");
        profileChip.addClassName("profile-chip-clickable");
        profileChip.setAlignItems(FlexComponent.Alignment.CENTER);
        profileChip.setSpacing(false);

        // Avatar with initials
        var initials = getInitials(user.getDisplayName());
        var avatar = new Div();
        avatar.setText(initials);
        avatar.addClassName("user-avatar");

        var userName = new Span(user.getDisplayName());
        userName.addClassName("user-name");

        profileChip.add(avatar, userName);
        profileChip.getElement().addEventListener("click", e -> {
            if (onClickHandler != null) {
                onClickHandler.run();
            }
        });

        add(profileChip);
    }

    public void setOnClickHandler(Runnable handler) {
        this.onClickHandler = handler;
    }

    public static String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        var parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
