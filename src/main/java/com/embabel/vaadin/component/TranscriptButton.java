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

import com.embabel.vaadin.component.Dialogs;
import com.embabel.chat.Conversation;
import com.embabel.chat.Message;
import com.embabel.chat.MessageRole;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.function.Supplier;

/**
 * Button that opens a dialog showing a plain-text transcript of the conversation.
 * Shows user and assistant messages only, with a copy-to-clipboard button.
 */
public class TranscriptButton extends Button {

    private static final String DEFAULT_ASSISTANT_NAME = "Assistant";

    private final Supplier<Conversation> conversationSupplier;
    private final String assistantName;

    public TranscriptButton(Supplier<Conversation> conversationSupplier) {
        this(conversationSupplier, DEFAULT_ASSISTANT_NAME);
    }

    /**
     * @param assistantName the assistant's identity name, used to label assistant messages
     *                      (falls back to "Assistant" when null or blank)
     */
    public TranscriptButton(Supplier<Conversation> conversationSupplier, String assistantName) {
        super(VaadinIcon.CLIPBOARD_TEXT.create());
        this.conversationSupplier = conversationSupplier;
        this.assistantName = assistantName == null || assistantName.isBlank()
                ? DEFAULT_ASSISTANT_NAME : assistantName;
        addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        getElement().setAttribute("title", "Show conversation transcript");
        addClickListener(e -> showTranscript());
    }

    private void showTranscript() {
        var conversation = conversationSupplier.get();
        if (conversation == null) return;

        var plainText = buildPlainTranscript(conversation);

        var dialog = new Dialog();
        dialog.setHeaderTitle("Transcript");
        Dialogs.resizableContentFit(dialog);

        var content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.setSizeFull();

        var messagesDiv = new Div();
        messagesDiv.getStyle().set("padding", "var(--lumo-space-s)");
        buildHtmlTranscript(conversation, messagesDiv);

        var scroller = new Scroller(messagesDiv);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        content.add(scroller);

        var copyButton = new Button("Copy to clipboard", VaadinIcon.COPY.create());
        copyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        copyButton.addClickListener(e -> {
            dialog.getElement().executeJs(
                "navigator.clipboard.writeText($0).then(() => $1.textContent = 'Copied!')",
                plainText, copyButton.getElement()
            );
        });

        var closeButton = new Button("Close");
        closeButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        closeButton.addClickListener(e -> dialog.close());

        dialog.getFooter().add(copyButton, closeButton);
        dialog.add(content);
        dialog.open();
    }

    // Package-private (not static) for tests: labels depend on the configured assistant name.
    void buildHtmlTranscript(Conversation conversation, Div container) {
        for (var message : conversation.getMessages()) {
            var role = message.getRole();
            var text = message.getContent();
            if (text == null || text.isBlank()) continue;

            String label;
            if (role == MessageRole.USER) {
                label = "You";
            } else if (role == MessageRole.ASSISTANT) {
                label = assistantName;
            } else {
                continue;
            }

            var entry = new Div();
            entry.getStyle().set("margin-bottom", "var(--lumo-space-m)");

            var nameSpan = new Span(label);
            nameSpan.getStyle().set("font-weight", "700");
            nameSpan.getStyle().set("display", "block");
            nameSpan.getStyle().set("margin-bottom", "var(--lumo-space-xs)");

            var contentSpan = new Span(text.strip());
            contentSpan.getStyle().set("white-space", "pre-wrap");
            contentSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");
            contentSpan.getStyle().set("line-height", "1.6");
            contentSpan.getStyle().set("display", "block");

            entry.add(nameSpan, contentSpan);
            container.add(entry);
        }
    }

    String buildPlainTranscript(Conversation conversation) {
        var sb = new StringBuilder();
        for (var message : conversation.getMessages()) {
            var role = message.getRole();
            var content = message.getContent();
            if (content == null || content.isBlank()) continue;

            if (role == MessageRole.USER) {
                sb.append("You: ").append(content.strip()).append("\n\n");
            } else if (role == MessageRole.ASSISTANT) {
                sb.append(assistantName).append(": ").append(content.strip()).append("\n\n");
            }
        }
        return sb.toString().strip();
    }
}
