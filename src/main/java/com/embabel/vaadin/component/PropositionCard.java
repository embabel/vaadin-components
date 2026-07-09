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

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.dice.proposition.EntityMention;
import com.embabel.dice.proposition.Proposition;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Card component displaying a single proposition with its metadata.
 */
public class PropositionCard extends Div {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Proposition proposition;
    private final Button editButton;
    private final Button deleteButton;
    private final HorizontalLayout metaLayout;
    private Consumer<Proposition> onDelete;
    private Consumer<Proposition> onEdit;
    private final Function<String, NamedEntity> entityResolver;
    private LineageProvider lineageProvider;
    private Button lineageBadge;
    private Function<String, java.util.List<Proposition>> relatedPropositionsLoader;
    private Function<String, EntityPanel.RelatedRecords> relatedRecordsLoader;
    private BiConsumer<String, String> onUndoMember;

    /**
     * Convenience constructor for callers that don't explain collapses.
     */
    public PropositionCard(Proposition prop, Function<String, NamedEntity> entityResolver) {
        this(prop, entityResolver, null);
    }

    /**
     * Card displaying a proposition, its metadata (confidence, created time), resolved entity mentions,
     * and optionally why it was collapsed into another memory. The card provides inline edit and delete.
     *
     * @param prop the proposition to display
     * @param entityResolver resolves entity mention IDs to NamedEntity; null to show mentions unresolved
     * @param collapseExplanationProvider looks up why this proposition was collapsed, if at all; null skips collapse badge
     */
    public PropositionCard(
            Proposition prop,
            Function<String, NamedEntity> entityResolver,
            CollapseExplanationProvider collapseExplanationProvider) {
        this.proposition = prop;
        this.entityResolver = entityResolver;
        addClassName("proposition-card");

        var headerLayout = new HorizontalLayout();
        headerLayout.setWidthFull();
        headerLayout.setSpacing(true);
        headerLayout.addClassName("proposition-header");

        var textSpan = new Span(prop.getText());
        textSpan.addClassName("proposition-text");

        editButton = new Button(VaadinIcon.EDIT.create());
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        editButton.addClassName("proposition-edit");
        editButton.getElement().setAttribute("title", "Edit this memory");
        editButton.addClickListener(e -> startEditing(textSpan, headerLayout));
        editButton.setVisible(false);

        deleteButton = new Button(VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.addClassName("proposition-delete");
        deleteButton.getElement().setAttribute("title", "Delete this memory");
        deleteButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(proposition);
            }
        });
        deleteButton.setVisible(false);

        headerLayout.add(textSpan, editButton, deleteButton);
        headerLayout.setFlexGrow(1, textSpan);

        metaLayout = new HorizontalLayout();
        metaLayout.setSpacing(true);
        metaLayout.addClassName("proposition-meta");

        var confidencePercent = (int) (prop.getConfidence() * 100);
        var confidenceSpan = new Span(confidencePercent + "% confidence");
        confidenceSpan.addClassName("proposition-confidence");
        confidenceSpan.addClassName(confidencePercent >= 80 ? "high" :
                confidencePercent >= 50 ? "medium" : "low");

        var timeSpan = new Span(TIME_FORMATTER.format(prop.getCreated()));
        timeSpan.addClassName("proposition-time");

        metaLayout.add(confidenceSpan, timeSpan);

        if (collapseExplanationProvider != null) {
            collapseExplanationProvider.explain(prop.getId())
                    .filter(explanation -> !explanation.retired().isEmpty())
                    .ifPresent(explanation -> metaLayout.add(createCollapseBadge(explanation)));
        }

        var mentions = prop.getMentions();
        if (!mentions.isEmpty()) {
            var entitiesLayout = new HorizontalLayout();
            entitiesLayout.setSpacing(false);
            entitiesLayout.addClassName("proposition-entities");

            for (var mention : mentions) {
                entitiesLayout.add(createMentionBadge(mention));
            }
            add(headerLayout, metaLayout, entitiesLayout);
        } else {
            add(headerLayout, metaLayout);
        }
    }

    private Span createMentionBadge(EntityMention mention) {
        String label;
        NamedEntity resolved = null;

        if (mention.getResolvedId() != null && entityResolver != null) {
            resolved = entityResolver.apply(mention.getResolvedId());
        }

        if (resolved != null) {
            label = resolved.getName();
        } else {
            // Fallback: show span text or type, with ? to indicate unresolved
            var base = mention.getSpan() != null ? mention.getSpan() : mention.getType();
            label = base + " ?";
        }

        var badge = new Span(label);
        badge.addClassName("mention-badge");

        if (resolved != null) {
            var entity = resolved;
            badge.addClassName("clickable");
            badge.getElement().addEventListener("click", e -> showEntityDialog(entity));
        } else {
            badge.addClassName("unresolved");
        }

        return badge;
    }

    private Button createCollapseBadge(CollapseExplanation explanation) {
        var badge = new Button("Merged " + explanation.retired().size() + " duplicate"
                + (explanation.retired().size() == 1 ? "" : "s"), VaadinIcon.COMPRESS_SQUARE.create());
        badge.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        badge.addClassName("collapse-explanation-badge");
        badge.getElement().setAttribute("title", "Show why these memories were merged");
        badge.addClickListener(e -> showCollapseDialog(explanation));
        return badge;
    }

    private void showCollapseDialog(CollapseExplanation explanation) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Why this memory was merged");
        dialog.setCloseOnOutsideClick(true);
        // Esc-to-close: the built-in closeOnEsc only fires when focus is inside the overlay, and
        // this dialog opens over the user drawer, which keeps focus — so Esc lands on the body and
        // never reaches it. A UI-scoped shortcut tied to the dialog's lifecycle closes it wherever
        // focus happens to be, and is torn down automatically when the dialog detaches.
        Shortcuts.addShortcutListener(dialog, dialog::close, Key.ESCAPE);
        // Resizable + draggable + content-fit sizing with viewport caps.
        Dialogs.resizableContentFit(dialog);
        // Preserve the custom overlay class for styling.
        // Class must go on the teleported overlay, not the (hidden) dialog host, so styling
        // and tests can target the visible dialog.
        dialog.getElement().setProperty("overlayClass", "content-fit-dialog collapse-explanation-dialog");

        var content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.addClassName("collapse-explanation-content");

        var survivorLabel = new Span("Kept: " + explanation.survivorText());
        survivorLabel.addClassName("collapse-survivor-text");
        content.add(survivorLabel);

        for (var member : explanation.retired()) {
            content.add(createRetiredMemberSection(explanation, member));
        }

        dialog.add(content);
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    VerticalLayout createRetiredMemberSection(CollapseExplanation explanation, CollapseExplanation.RetiredMember member) {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.addClassName("collapse-retired-member");

        var text = member.text() != null ? member.text() : "(memory " + member.propositionId() + ")";
        var displayText = "Folded in: " + text;

        if (member.priorStatus() != null && !member.priorStatus().isBlank()) {
            displayText += "  (was " + member.priorStatus() + ")";
        }

        var textSpan = new Span(displayText);
        textSpan.addClassName("collapse-retired-text");
        section.add(textSpan);

        var edge = explanation.edges().stream()
                .filter(e -> e.anchorId().equals(member.propositionId()) || e.memberId().equals(member.propositionId()))
                .findFirst();

        edge.ifPresent(e -> {
            var signalsLayout = new VerticalLayout();
            signalsLayout.setPadding(false);
            signalsLayout.setSpacing(false);
            signalsLayout.addClassName("collapse-signals");

            for (var signal : e.signals()) {
                var scorePct = (int) Math.round(signal.score() * 100);
                var reason = signal.explanation() != null ? " — " + signal.explanation() : "";
                var signalSpan = new Span(signal.signal() + ": " + scorePct + "%" + reason);
                signalSpan.addClassName("collapse-signal");
                if (signal.veto()) {
                    signalSpan.addClassName("collapse-signal-veto");
                }
                signalsLayout.add(signalSpan);
            }
            section.add(signalsLayout);
        });

        return section;
    }

    /**
     * Give this card a way to trace where its memory came from. When set, a "Lineage" badge
     * appears offering a dialog with the grounding/provenance/collapse trail; when cleared, the
     * badge is removed.
     *
     * @param lineageProvider looks up lineage for a proposition id, or null to hide the affordance
     */
    public void setLineageProvider(LineageProvider lineageProvider) {
        this.lineageProvider = lineageProvider;
        if (lineageBadge != null) {
            metaLayout.remove(lineageBadge);
            lineageBadge = null;
        }
        if (lineageProvider != null) {
            lineageBadge = createLineageBadge(lineageProvider);
            metaLayout.add(lineageBadge);
        }
    }

    /**
     * Give entity dialogs a way to show memories mentioning the entity. When set,
     * entity panels display a collapsed "Mentioned in N memories" section with those propositions.
     *
     * @param relatedPropositionsLoader looks up propositions mentioning an entity id,
     *                                   or null to omit the related-memories section
     */
    public void setRelatedPropositionsLoader(Function<String, java.util.List<Proposition>> relatedPropositionsLoader) {
        this.relatedPropositionsLoader = relatedPropositionsLoader;
    }

    /**
     * Give entity dialogs additional related-records sections (contact facts, people, orgs,
     * emails, meetings, edge chips). When set, entity panels load and display these sections.
     *
     * @param relatedRecordsLoader looks up RelatedRecords by entity id,
     *                             or null to omit related records
     */
    public void setRelatedRecordsLoader(Function<String, EntityPanel.RelatedRecords> relatedRecordsLoader) {
        this.relatedRecordsLoader = relatedRecordsLoader;
    }

    /**
     * Set the handler to invoke when an "Undo this merge" button is clicked in the lineage section.
     *
     * @param onUndoMember callback receiving (survivorId, retiredMemberId) when undo is clicked,
     *                     or null to disable undo functionality
     */
    public void setOnUndoMember(BiConsumer<String, String> onUndoMember) {
        this.onUndoMember = onUndoMember;
    }

    private Button createLineageBadge(LineageProvider provider) {
        var badge = new Button("Lineage", VaadinIcon.CONNECT.create());
        badge.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        badge.addClassName("lineage-badge");
        badge.getElement().setAttribute("title", "Show where this memory came from");
        badge.addClickListener(e -> showLineageDialog(provider));
        return badge;
    }

    private void showLineageDialog(LineageProvider provider) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Lineage");
        Shortcuts.addShortcutListener(dialog, dialog::close, Key.ESCAPE);
        // Resizable + draggable + content-fit sizing with viewport caps.
        Dialogs.resizableContentFit(dialog);
        // Preserve the custom overlay class for styling.
        dialog.getElement().setProperty("overlayClass", "content-fit-dialog lineage-dialog");

        var section = new LineageSection(provider);
        if (onUndoMember != null) {
            section.setOnUndoMember(onUndoMember);
        }
        section.show(proposition.getId());
        dialog.add(section);
        dialog.getFooter().add(new Button("Close", e -> dialog.close()));
        dialog.open();
    }

    // Package-visible (not private) only so tests can drive the real dialog-opening path
    // directly, the same way clicking a mention badge does, without needing a browser to
    // fire the DOM click event.
    void showEntityDialog(NamedEntity entity) {
        var dialog = new Dialog();
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        // Add Esc-to-close shortcut (similar to other dialogs in this card)
        Shortcuts.addShortcutListener(dialog, dialog::close, Key.ESCAPE);
        // Resizable + draggable + content-fit sizing with viewport caps.
        Dialogs.resizableContentFit(dialog);
        // Preserve the custom overlay class for styling.
        dialog.getElement().setProperty("overlayClass", "content-fit-dialog entity-360-dialog");

        var panel = new EntityPanel(entity, relatedPropositionsLoader);
        panel.setOnClose(dialog::close);
        if (relatedRecordsLoader != null) {
            panel.setRelatedRecords(relatedRecordsLoader);
        }
        dialog.add(panel);

        dialog.open();
    }

    public void setOnDelete(Consumer<Proposition> handler) {
        this.onDelete = handler;
        deleteButton.setVisible(handler != null);
    }

    /**
     * Set handler for editing. Called with the updated proposition (new text applied via copy).
     */
    public void setOnEdit(Consumer<Proposition> handler) {
        this.onEdit = handler;
        editButton.setVisible(handler != null);
    }

    public Proposition getProposition() {
        return proposition;
    }

    private void startEditing(Span textSpan, HorizontalLayout headerLayout) {
        var editArea = new TextArea();
        editArea.setValue(proposition.getText());
        editArea.setWidthFull();
        editArea.addClassName("proposition-edit-area");

        var saveButton = new Button("Save", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        var cancelButton = new Button("Cancel");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        var editButtons = new HorizontalLayout(saveButton, cancelButton);
        editButtons.setSpacing(true);

        // Replace the header with the edit area
        var editContainer = new Div(editArea, editButtons);
        editContainer.addClassName("proposition-edit-container");

        var cardIndex = getElement().indexOfChild(headerLayout.getElement());
        headerLayout.setVisible(false);
        getElement().insertChild(cardIndex, editContainer.getElement());

        saveButton.addClickListener(e -> {
            var newText = editArea.getValue().trim();
            if (!newText.isEmpty() && !newText.equals(proposition.getText())) {
                var updated = proposition.withText(newText);
                textSpan.setText(newText);
                if (onEdit != null) {
                    onEdit.accept(updated);
                }
            }
            remove(editContainer);
            headerLayout.setVisible(true);
        });

        cancelButton.addClickListener(e -> {
            remove(editContainer);
            headerLayout.setVisible(true);
        });
    }
}
