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

import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;

/**
 * Section displaying registered agents from the agent platform.
 * Each agent is an expandable card that reveals its actions and goals
 * using {@link ActionsSection} and {@link GoalsSection} card renderers.
 * Uses generic records so this component has no dependency on the agent module.
 */
public class AgentsSection extends VerticalLayout {

    /**
     * Generic agent info for display purposes, including its actions and goals.
     */
    public record AgentInfo(
            String name,
            String description,
            String provider,
            List<ActionsSection.ActionInfo> actions,
            List<GoalsSection.GoalInfo> goals
    ) {}

    public AgentsSection(List<AgentInfo> agents) {
        setPadding(true);
        setSpacing(true);

        var title = new H4("Agents (" + agents.size() + ")");
        title.addClassName("section-title");
        add(title);

        if (agents.isEmpty()) {
            var emptyLabel = new Span("No agents registered");
            emptyLabel.addClassName("empty-list-label");
            add(emptyLabel);
            return;
        }

        for (var agent : agents) {
            add(createAgentDetails(agent));
        }
    }

    private Details createAgentDetails(AgentInfo agent) {
        // Summary line: name + badges
        var summary = new Div();
        summary.addClassName("agent-summary");

        var name = new Span(agent.name());
        name.addClassName("agent-card-name");
        summary.add(name);

        var badgesDiv = new Div();
        badgesDiv.addClassName("agent-card-badges");

        if (agent.provider() != null && !agent.provider().isBlank()) {
            var providerBadge = new Span(agent.provider());
            providerBadge.addClassName("agent-provider-badge");
            badgesDiv.add(providerBadge);
        }

        var actionsBadge = new Span(agent.actions().size() + " actions");
        actionsBadge.addClassName("agent-count-badge");
        badgesDiv.add(actionsBadge);

        var goalsBadge = new Span(agent.goals().size() + " goals");
        goalsBadge.addClassName("agent-count-badge");
        badgesDiv.add(goalsBadge);

        summary.add(badgesDiv);

        // Expandable content
        var content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        // Description
        if (agent.description() != null && !agent.description().isBlank()) {
            var desc = new Span(agent.description());
            desc.addClassName("agent-card-desc");
            content.add(desc);
        }

        // Actions
        if (!agent.actions().isEmpty()) {
            var actionsLabel = new Span("Actions (" + agent.actions().size() + ")");
            actionsLabel.addClassName("agent-subsection-label");
            content.add(actionsLabel);
            for (var action : agent.actions()) {
                content.add(ActionsSection.createActionCard(action));
            }
        }

        // Goals
        if (!agent.goals().isEmpty()) {
            var goalsLabel = new Span("Goals (" + agent.goals().size() + ")");
            goalsLabel.addClassName("agent-subsection-label");
            content.add(goalsLabel);
            for (var goal : agent.goals()) {
                content.add(GoalsSection.createGoalCard(goal));
            }
        }

        var details = new Details(summary, content);
        details.addClassName("agent-card");
        return details;
    }
}
