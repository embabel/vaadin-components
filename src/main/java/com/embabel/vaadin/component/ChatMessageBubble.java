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

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.AttributeProviderContext;
import org.commonmark.renderer.html.AttributeProviderFactory;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.List;
import java.util.Map;

/**
 * Chat message bubble component with sender name and text content.
 * Styled differently for user vs assistant messages.
 * Assistant messages render markdown as HTML.
 */
public class ChatMessageBubble extends Div {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser MARKDOWN_PARSER = Parser.builder().extensions(EXTENSIONS).build();

    /**
     * Adds {@code target="_blank" rel="noopener noreferrer"} to every rendered link.
     * Without this, Vaadin Flow's client-side router intercepts same-origin links
     * (e.g. {@code /apps/foo.html}) and fails with "Could not navigate". Opening in
     * a new tab forces the browser to make a normal HTTP request, so the link works.
     */
    private static final AttributeProviderFactory EXTERNAL_LINK_FACTORY =
            (AttributeProviderContext context) -> (AttributeProvider) (Node node, String tagName, Map<String, String> attributes) -> {
                if (node instanceof Link && "a".equals(tagName)) {
                    attributes.put("target", "_blank");
                    attributes.put("rel", "noopener noreferrer");
                }
            };

    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .attributeProviderFactory(EXTERNAL_LINK_FACTORY)
            .build();

    public ChatMessageBubble(String sender, String text, boolean isUser) {
        addClassName("chat-bubble-container");
        addClassName(isUser ? "user" : "assistant");

        var messageDiv = new Div();
        messageDiv.addClassName("chat-bubble");
        messageDiv.addClassName(isUser ? "user" : "assistant");

        var headerDiv = new Div();
        headerDiv.addClassName("chat-bubble-header");

        var senderSpan = new Span(sender);
        senderSpan.addClassName("chat-bubble-sender");

        var timestamp = new Span(java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        timestamp.addClassName("chat-bubble-timestamp");

        headerDiv.add(senderSpan, timestamp, copyButton(text));

        if (isUser) {
            var textSpan = new Span(text);
            textSpan.addClassName("chat-bubble-text");
            messageDiv.add(headerDiv, textSpan);
        } else {
            var contentDiv = new Div();
            contentDiv.addClassName("chat-bubble-text");
            contentDiv.add(new Html("<div>" + renderMarkdown(text) + "</div>"));
            messageDiv.add(headerDiv, contentDiv);
        }

        add(messageDiv);
    }

    private static Button copyButton(String text) {
        var button = new Button(VaadinIcon.COPY_O.create());
        button.addClassName("chat-bubble-copy-btn");
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getElement().setAttribute("title", "Copy message");
        button.getElement().setAttribute("aria-label", "Copy message");
        button.addClickListener(e -> button.getElement().executeJs(
                """
                navigator.clipboard.writeText($0);
                const icon = this.querySelector('vaadin-icon');
                if (icon) {
                  const orig = icon.getAttribute('icon');
                  icon.setAttribute('icon', 'vaadin:check');
                  setTimeout(() => icon.setAttribute('icon', orig), 1200);
                }
                """,
                text == null ? "" : text));
        return button;
    }

    private static String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        var document = MARKDOWN_PARSER.parse(markdown.strip());
        return HTML_RENDERER.render(document).strip();
    }

    public static ChatMessageBubble user(String text) {
        return new ChatMessageBubble("You", text, true);
    }

    public static ChatMessageBubble assistant(String persona, String text) {
        return new ChatMessageBubble(persona, text, false);
    }

    public static Div error(String text) {
        var messageDiv = new Div();
        messageDiv.addClassName("chat-bubble-error");
        messageDiv.setText(text);
        return messageDiv;
    }
}
