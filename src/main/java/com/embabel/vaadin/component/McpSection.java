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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.function.Consumer;

/**
 * Section displaying loaded MCP servers with name, description, and tool count.
 * Uses a generic record so this component has no dependency on the MCP module.
 */
public class McpSection extends VerticalLayout {

    /**
     * Generic MCP server info for display purposes.
     */
    public record McpServerInfo(String name, String description, int toolCount, List<String> toolNames) {
    }

    public McpSection(List<McpServerInfo> servers) {
        this(servers, null);
    }

    public McpSection(List<McpServerInfo> servers, Consumer<String> onDelete) {
        setPadding(true);
        setSpacing(true);

        var title = new H4("MCP Servers (" + servers.size() + ")");
        title.addClassName("section-title");
        add(title);

        if (servers.isEmpty()) {
            var emptyLabel = new Span("No MCP servers loaded");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var server : servers) {
            add(createServerCard(server, onDelete));
        }
    }

    private Div createServerCard(McpServerInfo server, Consumer<String> onDelete) {
        var card = new Div();
        card.addClassName("mcp-card");

        var header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        var name = new Span(server.name());
        name.addClassName("mcp-card-name");
        header.add(name);
        header.setFlexGrow(1, name);

        if (onDelete != null) {
            var deleteBtn = new Button("Remove");
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            deleteBtn.addClickListener(e -> onDelete.accept(server.name()));
            header.add(deleteBtn);
        }

        card.add(header);

        if (server.description() != null && !server.description().isBlank()) {
            var desc = new Span(server.description());
            desc.addClassName("mcp-card-desc");
            card.add(desc);
        }

        var toolCount = new Span(server.toolCount() + " tool" + (server.toolCount() != 1 ? "s" : ""));
        toolCount.addClassName("mcp-card-tools");
        card.add(toolCount);

        if (!server.toolNames().isEmpty()) {
            var toolList = new Span(String.join(", ", server.toolNames()));
            toolList.addClassName("mcp-card-tool-list");
            card.add(toolList);
        }

        return card;
    }
}
