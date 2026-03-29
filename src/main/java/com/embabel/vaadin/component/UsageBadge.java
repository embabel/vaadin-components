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
import com.vaadin.flow.component.html.Span;

import java.util.List;

/**
 * Reusable badge showing LLM token usage, cost, and models used.
 * Compact inline display suitable for footers, status bars, or process lists.
 *
 * <p>Example:
 * <pre>
 * var badge = new UsageBadge();
 * badge.update(1500, 300, 0.0028, List.of("gpt-4.1-mini"), 3);
 * layout.add(badge);
 * </pre>
 */
public class UsageBadge extends Div {

    private final Span tokensSpan = new Span();
    private final Span costSpan = new Span();
    private final Span modelSpan = new Span();

    public UsageBadge() {
        addClassName("usage-badge");
        getStyle()
                .set("display", "inline-flex")
                .set("gap", "0.5rem")
                .set("align-items", "center")
                .set("font-size", "0.75rem")
                .set("color", "var(--lumo-secondary-text-color, #6b7280)");

        tokensSpan.addClassName("usage-tokens");
        costSpan.addClassName("usage-cost");
        modelSpan.addClassName("usage-model");
        modelSpan.getStyle().set("opacity", "0.7");

        add(tokensSpan, costSpan, modelSpan);
    }

    /**
     * Update the badge with fresh usage data.
     *
     * @param promptTokens    input tokens
     * @param completionTokens output tokens
     * @param cost            total cost in USD
     * @param models          list of model names used
     * @param llmCalls        number of LLM invocations
     */
    public void update(int promptTokens, int completionTokens, double cost,
                       List<String> models, int llmCalls) {
        int total = promptTokens + completionTokens;
        tokensSpan.setText(formatTokens(total) + " tokens");
        costSpan.setText("$" + formatCost(cost));
        if (models != null && !models.isEmpty()) {
            modelSpan.setText(String.join(", ", models));
        } else {
            modelSpan.setText("");
        }
        getElement().setAttribute("title",
                String.format("Prompt: %,d | Completion: %,d | Calls: %d | Cost: $%.4f",
                        promptTokens, completionTokens, llmCalls, cost));
    }

    /** Clear the badge. */
    public void clear() {
        tokensSpan.setText("");
        costSpan.setText("");
        modelSpan.setText("");
    }

    private static String formatTokens(int tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fk", tokens / 1_000.0);
        return String.valueOf(tokens);
    }

    private static String formatCost(double cost) {
        if (cost < 0.001) return String.format("%.4f", cost);
        if (cost < 0.01) return String.format("%.3f", cost);
        return String.format("%.2f", cost);
    }
}
