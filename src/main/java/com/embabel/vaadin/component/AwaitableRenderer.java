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

import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.hitl.*;
import com.embabel.ux.form.FormSubmission;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Generic renderer for {@link Awaitable} payloads attached to chat messages.
 * <p>
 * When an agent action calls {@code confirm()} or {@code fromForm()}, the process
 * pauses and an {@link com.embabel.chat.AssistantMessage} is emitted with an
 * {@code awaitable} field. This component detects the awaitable type and renders
 * the appropriate just-in-time UI:
 * <ul>
 *   <li>{@link ConfirmationRequest} — payload summary with Accept / Reject buttons</li>
 *   <li>{@link FormBindingRequest} — full form rendered from the {@link com.embabel.ux.form.Form} model</li>
 * </ul>
 * On user action, the response is submitted to the {@link AgentProcess} which resumes execution.
 */
public class AwaitableRenderer extends Div {

    private static final Logger logger = LoggerFactory.getLogger(AwaitableRenderer.class);

    /**
     * Render an awaitable as a Vaadin component.
     *
     * @param awaitable     the awaitable from {@code AssistantMessage.awaitable}
     * @param agentProcess  the paused agent process to resume on response
     * @param onCompleted   optional callback after the response has been submitted
     */
    public AwaitableRenderer(
            Awaitable<?, ?> awaitable,
            AgentProcess agentProcess,
            Consumer<ResponseImpact> onCompleted
    ) {
        addClassName("awaitable-renderer");

        if (awaitable instanceof ConfirmationRequest<?> confirmation) {
            add(renderConfirmation(confirmation, agentProcess, onCompleted));
        } else if (awaitable instanceof FormBindingRequest<?> formBinding) {
            add(renderFormBinding(formBinding, agentProcess, onCompleted));
        } else {
            add(new Paragraph("Waiting for input (unsupported awaitable type: "
                    + awaitable.getClass().getSimpleName() + ")"));
        }
    }

    /**
     * Convenience constructor without completion callback.
     */
    public AwaitableRenderer(Awaitable<?, ?> awaitable, AgentProcess agentProcess) {
        this(awaitable, agentProcess, impact -> {});
    }

    private Component renderConfirmation(
            ConfirmationRequest<?> request,
            AgentProcess agentProcess,
            Consumer<ResponseImpact> onCompleted
    ) {
        var layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.addClassName("awaitable-confirmation");

        // Message
        var message = new Paragraph(request.getMessage());
        message.addClassName("awaitable-confirmation-message");
        layout.add(message);

        // Payload summary
        var payload = request.getPayload();
        if (payload != null) {
            var payloadDiv = new Div();
            payloadDiv.addClassName("awaitable-confirmation-payload");
            payloadDiv.setText(payload.toString());
            layout.add(payloadDiv);
        }

        // Buttons
        var buttons = new HorizontalLayout();
        buttons.setSpacing(true);

        var acceptButton = new Button("Accept");
        acceptButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        acceptButton.addClassName("awaitable-accept");

        var rejectButton = new Button("Reject");
        rejectButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        rejectButton.addClassName("awaitable-reject");

        acceptButton.addClickListener(e -> {
            acceptButton.setEnabled(false);
            rejectButton.setEnabled(false);
            var response = new ConfirmationResponse(
                    UUID.randomUUID().toString(),
                    request.getId(),
                    true,
                    false,
                    Instant.now()
            );
            var impact = submitResponse(request, response, agentProcess);
            showOutcome(layout, true);
            onCompleted.accept(impact);
        });

        rejectButton.addClickListener(e -> {
            acceptButton.setEnabled(false);
            rejectButton.setEnabled(false);
            var response = new ConfirmationResponse(
                    UUID.randomUUID().toString(),
                    request.getId(),
                    false,
                    false,
                    Instant.now()
            );
            var impact = submitResponse(request, response, agentProcess);
            showOutcome(layout, false);
            onCompleted.accept(impact);
        });

        var dismissButton = new Button("Dismiss");
        dismissButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dismissButton.addClassName("awaitable-dismiss");
        dismissButton.addClickListener(e -> {
            acceptButton.setEnabled(false);
            rejectButton.setEnabled(false);
            dismissButton.setEnabled(false);
            showDismissed(layout);
        });

        buttons.add(acceptButton, rejectButton, dismissButton);
        layout.add(buttons);

        // Escape key dismisses
        final ShortcutRegistration[] escRef = {null};
        layout.addAttachListener(e ->
            escRef[0] = e.getUI().addShortcutListener(event -> {
                acceptButton.setEnabled(false);
                rejectButton.setEnabled(false);
                dismissButton.setEnabled(false);
                showDismissed(layout);
                if (escRef[0] != null) escRef[0].remove();
            }, Key.ESCAPE)
        );
        layout.addDetachListener(e -> {
            if (escRef[0] != null) escRef[0].remove();
        });

        return layout;
    }

    @SuppressWarnings("unchecked")
    private Component renderFormBinding(
            FormBindingRequest<?> request,
            AgentProcess agentProcess,
            Consumer<ResponseImpact> onCompleted
    ) {
        var form = request.getPayload(); // Form is the payload
        var renderer = new FormRenderer(form, (FormSubmission submission) -> {
            // Check for empty submissions — don't submit if all fields are blank
            var allBlank = submission.getValues().values().stream()
                    .allMatch(v -> v == null || v.toString().isBlank());
            if (allBlank) {
                logger.warn("Form submission rejected: all fields are empty");
                return;
            }
            try {
                var response = new FormResponse(
                        UUID.randomUUID().toString(),
                        request.getId(),
                        submission,
                        false,
                        Instant.now()
                );
                var impact = submitResponse(
                        (Awaitable<Object, FormResponse>) (Awaitable<?, ?>) request,
                        response,
                        agentProcess
                );
                onCompleted.accept(impact);
            } catch (Exception e) {
                logger.error("Form submission failed: {}", e.getMessage());
            }
        });
        return renderer;
    }

    @SuppressWarnings("unchecked")
    private <R extends AwaitableResponse> ResponseImpact submitResponse(
            Awaitable<?, R> awaitable,
            R response,
            AgentProcess agentProcess
    ) {
        try {
            var impact = awaitable.onResponse(response, agentProcess);
            logger.info("Awaitable response submitted: {} -> {}", awaitable.getId(), impact);
            // Resume the agent process after updating blackboard
            agentProcess.run();
            return impact;
        } catch (Exception e) {
            logger.error("Failed to submit awaitable response for {}", awaitable.getId(), e);
            return ResponseImpact.UNCHANGED;
        }
    }

    private void showOutcome(VerticalLayout layout, boolean accepted) {
        var badge = new Span(accepted ? "\u2713 Accepted" : "\u2717 Rejected");
        badge.addClassName(accepted ? "awaitable-outcome-accepted" : "awaitable-outcome-rejected");
        layout.add(badge);
    }

    private void showDismissed(VerticalLayout layout) {
        var badge = new Span("Dismissed");
        badge.addClassName("awaitable-outcome-rejected");
        layout.add(badge);
    }
}
