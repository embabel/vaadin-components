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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Section showing a single proposition's lineage: what it's grounded on, its provenance trail,
 * and (if it was ever involved in a collapse) the merge history behind it. Data comes from a
 * {@link LineageProvider} supplied by the host, so this component doesn't know or care where the
 * trace is stored.
 */
public class LineageSection extends VerticalLayout {

    private static final String STYLES = """
            .lineage-section {
              --lumo-space-xs: 4px;
              --lumo-space-s: 8px;
              --lumo-space-m: 16px;
              --lumo-space-l: 24px;
              --lumo-border-radius: 6px;
              --lumo-border-radius-l: 10px;
              --lumo-shadow: 0 1px 2px rgba(20,22,28,.06), 0 4px 12px rgba(20,22,28,.05);
              --lumo-text-secondary: var(--lumo-secondary-text-color, #5c6370);
              --lumo-text-tertiary: var(--lumo-tertiary-text-color, #8a909c);
              --lumo-surface-weak: var(--lumo-contrast-10pct, #eef0f3);
            }

            @media (prefers-color-scheme: dark) {
              .lineage-section {
                --lumo-text-secondary: #a4aab4;
                --lumo-text-tertiary: #767c87;
                --lumo-surface-weak: #252932;
              }
            }

            .lineage-section .lineage-card {
              background: var(--lumo-base-color, #ffffff);
              border: 1px solid var(--lumo-contrast-20pct, #e2e5ea);
              border-radius: var(--lumo-border-radius-l);
              box-shadow: var(--lumo-shadow);
              margin-bottom: var(--lumo-space-m);
            }

            .lineage-card-header {
              display: flex;
              align-items: center;
              gap: var(--lumo-space-s);
              padding: var(--lumo-space-s) calc(var(--lumo-space-s) + 6px);
              border-bottom: 1px solid var(--lumo-contrast-20pct, #e2e5ea);
            }

            .lineage-card-header .lineage-icon {
              width: 22px;
              height: 22px;
              border-radius: 5px;
              display: flex;
              align-items: center;
              justify-content: center;
              flex-shrink: 0;
            }

            .lineage-card-header .lineage-icon.icon-grounding {
              background: rgba(176, 121, 11, 0.15);
              color: var(--lumo-primary-color, #1676f3);
            }

            .lineage-card-header .lineage-icon.icon-provenance {
              background: rgba(22, 118, 243, 0.15);
              color: var(--lumo-primary-color, #1676f3);
            }

            .lineage-card-header .lineage-icon.icon-collapse {
              background: rgba(117, 72, 214, 0.15);
              color: #7548d6;
            }

            @media (prefers-color-scheme: dark) {
              .lineage-card-header .lineage-icon.icon-grounding {
                background: rgba(224, 168, 64, 0.15);
              }
              .lineage-card-header .lineage-icon.icon-provenance {
                background: rgba(76, 154, 255, 0.15);
              }
              .lineage-card-header .lineage-icon.icon-collapse {
                background: rgba(166, 134, 240, 0.15);
              }
            }

            .lineage-card-header h3 {
              margin: 0;
              font-size: 12.5px;
              font-weight: 600;
              flex: 1;
            }

            .lineage-card-header .lineage-count {
              font-size: 11px;
              color: var(--lumo-text-tertiary);
              background: var(--lumo-surface-weak);
              padding: 2px 7px;
              border-radius: 999px;
            }

            .lineage-card-body {
              padding: var(--lumo-space-s) calc(var(--lumo-space-s) + 6px) calc(var(--lumo-space-s) + 2px);
            }

            /* Grounding refs */
            .lineage-grounding {
              display: flex;
              flex-direction: column;
              gap: 6px;
            }

            .lineage-grounding .lineage-ref {
              display: flex;
              align-items: center;
              gap: var(--lumo-space-s);
              padding: 7px 10px;
              border: 1px solid var(--lumo-contrast-20pct, #e2e5ea);
              border-radius: var(--lumo-border-radius);
              background: var(--lumo-contrast-5pct, #f7f8fa);
              font-size: 12px;
            }

            .lineage-grounding .lineage-ref .ref-src {
              color: var(--lumo-text-tertiary);
              font-size: 11px;
              flex-shrink: 0;
              width: 96px;
            }

            .lineage-grounding .lineage-ref .ref-txt {
              color: var(--lumo-body-text-color, #1f2329);
              flex: 1;
              font-style: italic;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }

            .lineage-grounding .lineage-ref .ref-go {
              color: var(--lumo-primary-color, #1676f3);
              font-size: 11px;
              font-weight: 600;
              flex-shrink: 0;
              cursor: pointer;
            }

            /* Provenance steps */
            .lineage-provenance .lineage-steps {
              display: flex;
              flex-direction: column;
            }

            .lineage-provenance .lineage-step {
              display: flex;
              gap: 10px;
              position: relative;
              margin-bottom: 4px;
            }

            .lineage-provenance .lineage-rail {
              display: flex;
              flex-direction: column;
              align-items: center;
              width: 20px;
              flex-shrink: 0;
            }

            .lineage-provenance .lineage-node-dot {
              width: 8px;
              height: 8px;
              border-radius: 50%;
              background: var(--lumo-primary-color, #1676f3);
              margin-top: 5px;
              flex-shrink: 0;
            }

            .lineage-provenance .lineage-line {
              width: 1.5px;
              flex: 1;
              background: var(--lumo-contrast-20pct, #e2e5ea);
              margin-top: 2px;
            }

            .lineage-provenance .lineage-step:last-child .lineage-line {
              display: none;
            }

            .lineage-step-body {
              padding: 0 0 16px;
              flex: 1;
            }

            .lineage-step-body .t1 {
              font-weight: 600;
              font-size: 12.5px;
            }

            .lineage-step-body .t2 {
              color: var(--lumo-text-tertiary);
              font-size: 11.5px;
              margin-top: 1px;
            }

            .lineage-step-body .lineage-badge {
              display: inline-block;
              font-size: 10.5px;
              font-weight: 600;
              padding: 1px 7px;
              border-radius: 999px;
              background: rgba(28, 154, 108, 0.15);
              color: #1c9a6c;
              margin-left: 6px;
            }

            @media (prefers-color-scheme: dark) {
              .lineage-step-body .lineage-badge {
                background: rgba(62, 207, 142, 0.15);
                color: #3ecf8e;
              }
            }

            /* Collapse merge chain */
            .lineage-merge-chain {
              display: flex;
              align-items: stretch;
              gap: 0;
              overflow-x: auto;
              padding: 2px 0 6px;
            }

            .lineage-merge-node {
              flex-shrink: 0;
              width: 150px;
              border: 1px solid var(--lumo-contrast-20pct, #e2e5ea);
              border-radius: var(--lumo-border-radius);
              padding: 8px 10px;
              background: var(--lumo-contrast-5pct, #f7f8fa);
              font-size: 11.5px;
            }

            .lineage-merge-node .merge-tag {
              font-size: 10px;
              text-transform: uppercase;
              letter-spacing: 0.03em;
              color: var(--lumo-text-tertiary);
              margin-bottom: 3px;
            }

            .lineage-merge-node .merge-txt {
              color: var(--lumo-text-secondary);
            }

            .lineage-merge-node.merge-survivor {
              background: rgba(28, 154, 108, 0.15);
              border-color: #1c9a6c;
            }

            .lineage-merge-node.merge-survivor .merge-tag {
              color: #1c9a6c;
            }

            @media (prefers-color-scheme: dark) {
              .lineage-merge-node.merge-survivor {
                background: rgba(62, 207, 142, 0.15);
                border-color: #3ecf8e;
              }
              .lineage-merge-node.merge-survivor .merge-tag {
                color: #3ecf8e;
              }
            }

            .lineage-merge-arrow {
              flex-shrink: 0;
              width: 26px;
              display: flex;
              align-items: center;
              justify-content: center;
              color: var(--lumo-text-tertiary);
            }

            .lineage-empty {
              color: var(--lumo-text-tertiary);
              font-size: 12px;
              padding: 6px 0;
            }

            .lineage-undo-member {
              font-size: 10px;
              padding: 2px 6px;
              margin-left: 6px;
              vertical-align: middle;
              height: auto;
              min-height: auto;
            }
            """;

    private final LineageProvider lineageProvider;
    private BiConsumer<String, String> onUndoMember;
    private Consumer<String> onOpenRef;
    private Predicate<String> openable;

    /**
     * @param lineageProvider looks up lineage for a proposition id
     */
    public LineageSection(LineageProvider lineageProvider) {
        this.lineageProvider = lineageProvider;
        addClassName("lineage-section");
        setPadding(false);
        setSpacing(false);
        addClassName("lineage-styled");

        // Inject styles once
        if (getElement().getParent() == null) {
            add(new Html("<style>" + STYLES + "</style>"));
        }
    }

    /**
     * Sets the callback to invoke when an "Undo this merge" button is clicked for a retired member.
     *
     * @param callback receives (survivorId, retiredMemberId) when undo is clicked; may be null to disable
     */
    public void setOnUndoMember(BiConsumer<String, String> callback) {
        this.onUndoMember = callback;
    }

    /**
     * Sets the callback to invoke when a grounding or provenance ref is opened.
     *
     * @param handler receives the ref string when a ref is clicked; may be null to disable
     */
    public void setOnOpenRef(Consumer<String> handler) {
        this.onOpenRef = handler;
    }

    /**
     * Sets a predicate to determine which refs are openable. Refs that fail the test
     * render without the "Open →" affordance.
     *
     * @param predicate returns true if the ref should be openable; null means all refs are openable
     */
    public void setOpenable(Predicate<String> predicate) {
        this.openable = predicate;
    }

    /**
     * Renders the lineage for the given proposition, replacing whatever was shown before.
     *
     * @param propositionId id of the proposition to trace
     */
    public void show(String propositionId) {
        // Remove all but the style tag
        var children = getChildren().toList();
        for (var child : children) {
            if (!(child instanceof Html)) {
                remove(child);
            }
        }

        var lineage = lineageProvider.lineageFor(propositionId);
        if (lineage.isEmpty()) {
            var empty = new Span("No lineage available for this memory.");
            empty.addClassName("lineage-empty");
            add(empty);
            return;
        }

        var data = lineage.get();
        add(createGroundingSection(data.grounding()));
        add(createProvenanceSection(data.provenance()));
        data.collapse().ifPresent(explanation -> add(createCollapseSection(explanation)));
    }

    private Div createGroundingSection(java.util.List<String> grounding) {
        var card = new Div();
        card.addClassName("lineage-card");

        var header = new Div();
        header.addClassName("lineage-card-header");

        var icon = new Div();
        icon.addClassName("lineage-icon");
        icon.addClassName("icon-grounding");
        var iconComponent = VaadinIcon.BOOK.create();
        iconComponent.setSize("12px");
        icon.add(iconComponent);
        header.add(icon);

        var heading = new com.vaadin.flow.component.html.H3();
        heading.setText("Grounding");
        heading.getStyle().set("margin", "0");
        heading.getStyle().set("font-size", "12.5px");
        heading.getStyle().set("font-weight", "600");
        heading.getStyle().set("flex", "1");
        header.add(heading);

        var count = new Span(grounding.size() + " refs");
        count.addClassName("lineage-count");
        header.add(count);

        card.add(header);

        var body = new Div();
        body.addClassName("lineage-card-body");

        if (grounding.isEmpty()) {
            var none = new Span("No grounding references.");
            none.addClassName("lineage-grounding-empty");
            body.add(none);
        } else {
            var refList = new Div();
            refList.addClassName("lineage-grounding");

            for (var ref : grounding) {
                var refDiv = new Div();
                refDiv.addClassName("lineage-ref");

                var src = new Span("Source");
                src.addClassName("ref-src");
                refDiv.add(src);

                var txt = new Span(ref);
                txt.addClassName("ref-txt");
                txt.setTitle(ref);
                refDiv.add(txt);

                boolean isOpenable = openable == null || openable.test(ref);
                if (isOpenable) {
                    var go = new Span("Open →");
                    go.addClassName("ref-go");
                    go.getStyle().set("cursor", "pointer");
                    go.addClickListener(e -> {
                        if (onOpenRef != null) {
                            onOpenRef.accept(ref);
                        }
                    });
                    refDiv.add(go);
                }

                refList.add(refDiv);
            }
            body.add(refList);
        }

        card.add(body);
        return card;
    }

    private Div createProvenanceSection(java.util.List<LineageProvider.Lineage.ProvenanceRef> provenance) {
        var card = new Div();
        card.addClassName("lineage-card");

        var header = new Div();
        header.addClassName("lineage-card-header");

        var icon = new Div();
        icon.addClassName("lineage-icon");
        icon.addClassName("icon-provenance");
        var iconComponent = VaadinIcon.PACKAGE.create();
        iconComponent.setSize("12px");
        icon.add(iconComponent);
        header.add(icon);

        var heading = new com.vaadin.flow.component.html.H3();
        heading.setText("Provenance");
        heading.getStyle().set("margin", "0");
        heading.getStyle().set("font-size", "12.5px");
        heading.getStyle().set("font-weight", "600");
        heading.getStyle().set("flex", "1");
        header.add(heading);

        var count = new Span(provenance.size() + " events");
        count.addClassName("lineage-count");
        header.add(count);

        card.add(header);

        var body = new Div();
        body.addClassName("lineage-card-body");

        if (provenance.isEmpty()) {
            var none = new Span("No provenance recorded.");
            none.addClassName("lineage-provenance-empty");
            body.add(none);
        } else {
            var steps = new Div();
            steps.addClassName("lineage-steps");

            for (var entry : provenance) {
                var step = new Div();
                step.addClassName("lineage-step");

                var rail = new Div();
                rail.addClassName("lineage-rail");

                var dot = new Div();
                dot.addClassName("lineage-node-dot");
                rail.add(dot);

                var line = new Div();
                line.addClassName("lineage-line");
                rail.add(line);

                step.add(rail);

                var stepBody = new Div();
                stepBody.addClassName("lineage-step-body");

                // Show detail (or source as fallback) as the primary label
                var displayText = (entry.detail() != null && !entry.detail().isBlank())
                        ? entry.detail()
                        : entry.source();

                var title = new Div();
                title.addClassName("t1");
                title.add(new Span(displayText));
                stepBody.add(title);

                var detail = new Div();
                detail.addClassName("t2");
                var detailSpan = new Span(entry.ref());
                detailSpan.setTitle(entry.ref());
                detail.add(detailSpan);
                stepBody.add(detail);

                boolean isOpenable = openable == null || openable.test(entry.ref());
                if (isOpenable) {
                    var go = new Span("Open →");
                    go.addClassName("ref-go");
                    go.getStyle().set("cursor", "pointer");
                    go.addClickListener(e -> {
                        if (onOpenRef != null) {
                            onOpenRef.accept(entry.ref());
                        }
                    });
                    detail.add(go);
                }

                step.add(stepBody);
                steps.add(step);
            }

            body.add(steps);
        }

        card.add(body);
        return card;
    }

    private Div createCollapseSection(CollapseExplanation explanation) {
        var card = new Div();
        card.addClassName("lineage-card");

        var header = new Div();
        header.addClassName("lineage-card-header");

        var icon = new Div();
        icon.addClassName("lineage-icon");
        icon.addClassName("icon-collapse");
        var iconComponent = VaadinIcon.PENCIL.create();
        iconComponent.setSize("12px");
        icon.add(iconComponent);
        header.add(icon);

        var heading = new com.vaadin.flow.component.html.H3();
        heading.add(new Span("Collapse history"));
        heading.getStyle().set("margin", "0");
        heading.getStyle().set("font-size", "12.5px");
        heading.getStyle().set("font-weight", "600");
        heading.getStyle().set("flex", "1");
        header.add(heading);

        int mergedInCount = explanation.retired().size();
        var count = new Span(mergedInCount + " merged in");
        count.addClassName("lineage-count");
        header.add(count);

        card.add(header);

        var body = new Div();
        body.addClassName("lineage-card-body");

        var chain = new Div();
        chain.addClassName("lineage-merge-chain");

        // Add retired members as merge nodes with full text for test compatibility
        for (var member : explanation.retired()) {
            var node = new Div();
            node.addClassName("lineage-merge-node");

            var tag = new Div();
            tag.addClassName("merge-tag");
            tag.setText("Retired");
            node.add(tag);

            var memberText = member.text() != null ? member.text() : "(memory " + member.propositionId() + ")";
            var fullText = "Folded in: " + memberText;
            if (member.priorStatus() != null && !member.priorStatus().isBlank()) {
                fullText += "  (was " + member.priorStatus() + ")";
            }

            var text = new Div();
            text.addClassName("merge-txt");
            var textSpan = new Span(fullText);
            text.add(textSpan);

            // Add undo button if callback is set
            if (onUndoMember != null) {
                var undoButton = new Button("Undo");
                undoButton.addClassName("lineage-undo-member");
                undoButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                var survivorId = explanation.survivorId();
                var retiredId = member.propositionId();
                undoButton.addClickListener(event -> onUndoMember.accept(survivorId, retiredId));
                text.add(undoButton);
            }

            node.add(text);
            chain.add(node);

            // Add arrow between nodes (except after last one)
            var arrow = new Div();
            arrow.addClassName("lineage-merge-arrow");
            arrow.getElement().setProperty("innerHTML", "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M5 12h14M13 6l6 6-6 6\"/></svg>");
            chain.add(arrow);
        }

        // Add survivor node
        if (!explanation.retired().isEmpty()) {
            var survivor = new Div();
            survivor.addClassName("lineage-merge-node");
            survivor.addClassName("merge-survivor");

            var tag = new Div();
            tag.addClassName("merge-tag");
            tag.setText("Survivor");
            survivor.add(tag);

            var text = new Div();
            text.addClassName("merge-txt");
            var textSpan = new Span("Kept: " + explanation.survivorText());
            text.add(textSpan);
            survivor.add(text);

            chain.add(survivor);
        }

        body.add(chain);
        card.add(body);
        return card;
    }
}
