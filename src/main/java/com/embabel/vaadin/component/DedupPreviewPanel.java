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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Panel that shows a sweep preview before a host applies it: which propositions would merge into
 * which survivor, the per-signal evidence behind each pairing, and the edges that were considered
 * but vetoed. It's host-agnostic — it only knows the plain {@link DedupPreview} record handed to
 * it, not how the host's dedup/collector pipeline works.
 */
public class DedupPreviewPanel extends VerticalLayout {

    private final VerticalLayout clustersLayout = new VerticalLayout();
    private final VerticalLayout nonMergesLayout = new VerticalLayout();
    private final Div emptyStateDiv = new Div();
    private final Button applyButton;
    private final Button undoButton;
    private final HorizontalLayout actionsLayout;
    private final Map<Div, Div> memberRowToPopoverMap = new HashMap<>();

    private Runnable onApply;
    private Consumer<String> onUndo;
    private DedupPreview current;

    /**
     * Builds an empty panel. Call {@link #show(DedupPreview)} to populate it with a sweep preview.
     */
    public DedupPreviewPanel() {
        addClassName("dedup-preview-panel");
        setPadding(true);
        setSpacing(true);
        getStyle().set("padding", "calc(var(--lumo-space-m) * 1.5)");

        clustersLayout.setPadding(false);
        clustersLayout.setSpacing(true);
        clustersLayout.addClassName("dedup-clusters");

        nonMergesLayout.setPadding(false);
        nonMergesLayout.setSpacing(false);
        nonMergesLayout.addClassName("dedup-nonmerges");

        applyButton = new Button("Apply all");
        applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        applyButton.addClassName("dedup-apply");
        applyButton.getElement().setAttribute("title", "Apply this sweep: retire the duplicates shown, keeping each cluster's survivor");
        applyButton.addClickListener(e -> {
            if (onApply != null) {
                onApply.run();
            }
        });

        undoButton = new Button("Undo");
        undoButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        undoButton.addClassName("dedup-undo");
        undoButton.getElement().setAttribute("title", "Undo the applied sweep: restore the retired duplicates");
        undoButton.addClickListener(e -> {
            if (onUndo != null && current != null) {
                onUndo.accept(current.runId());
            }
        });

        actionsLayout = new HorizontalLayout(applyButton, undoButton);
        actionsLayout.addClassName("dedup-actions");
        actionsLayout.setSpacing(true);

        emptyStateDiv.addClassName("dedup-empty-state");
        emptyStateDiv.setVisible(false);

        add(emptyStateDiv, clustersLayout, nonMergesLayout, actionsLayout);
    }

    /**
     * Renders a sweep preview: proposed clusters with their survivor/losers and per-signal
     * evidence, plus any edges that were considered but vetoed.
     *
     * @param preview the sweep preview to render, as a plain host-supplied record
     */
    public void show(DedupPreview preview) {
        this.current = preview;
        clustersLayout.removeAll();
        nonMergesLayout.removeAll();
        emptyStateDiv.removeAll();

        boolean hasClusters = !preview.clusters().isEmpty();
        boolean hasNonMerges = !preview.nonMerges().isEmpty();

        if (!hasClusters && !hasNonMerges) {
            // Empty state: no clusters and no non-merges
            renderEmptyState();
            emptyStateDiv.setVisible(true);
            actionsLayout.setVisible(false);
        } else {
            emptyStateDiv.setVisible(false);
            actionsLayout.setVisible(true);

            // Render clusters if present
            for (var cluster : preview.clusters()) {
                clustersLayout.add(renderCluster(cluster));
            }

            // Render non-merges if present
            if (hasNonMerges) {
                var heading = new Span("Did not merge");
                heading.addClassName("dedup-nonmerge-heading");
                nonMergesLayout.add(heading);

                // Show "No merges proposed" note if there are no clusters but there are non-merges
                if (!hasClusters) {
                    var noMergesNote = new Span("No merges proposed");
                    noMergesNote.addClassName("dedup-no-merges-note");
                    noMergesNote.getStyle().set("font-size", "var(--lumo-font-size-xs)");
                    noMergesNote.getStyle().set("color", "var(--lumo-secondary-text-color)");
                    noMergesNote.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
                    nonMergesLayout.add(noMergesNote);
                }

                for (var edge : preview.nonMerges()) {
                    nonMergesLayout.add(renderNonMerge(edge));
                }
            }
        }
    }

    private void renderEmptyState() {
        // Container for centered empty state content
        var container = new VerticalLayout();
        container.setAlignItems(FlexComponent.Alignment.CENTER);
        container.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle().set("min-height", "200px");
        container.getStyle().set("gap", "var(--lumo-space-m)");

        // Icon or dot (muted)
        var iconDiv = new Div();
        iconDiv.setText("•");
        iconDiv.getStyle().set("font-size", "32px");
        iconDiv.getStyle().set("color", "var(--lumo-secondary-text-color)");
        container.add(iconDiv);

        // Primary line
        var primaryLine = new Span("No merge candidates");
        primaryLine.getStyle().set("font-size", "var(--lumo-font-size-m)");
        primaryLine.getStyle().set("font-weight", "500");
        primaryLine.getStyle().set("color", "var(--lumo-body-text-color)");
        container.add(primaryLine);

        // Secondary line
        var secondaryLine = new Span("Your memories look distinct — nothing to collapse right now.");
        secondaryLine.getStyle().set("font-size", "var(--lumo-font-size-s)");
        secondaryLine.getStyle().set("color", "var(--lumo-secondary-text-color)");
        container.add(secondaryLine);

        emptyStateDiv.add(container);
    }

    /**
     * Sets the handler invoked when the user clicks Apply.
     *
     * @param handler runs when the currently shown preview should be applied
     */
    public void setOnApply(Runnable handler) {
        this.onApply = handler;
    }

    /**
     * Sets the handler invoked when the user clicks Undo.
     *
     * @param handler receives the run id of the currently shown preview to undo
     */
    public void setOnUndo(Consumer<String> handler) {
        this.onUndo = handler;
    }

    private Div renderCluster(DedupPreview.Cluster cluster) {
        var clusterCard = new Div();
        clusterCard.addClassName("dedup-cluster");
        clusterCard.getStyle().set("background", "var(--lumo-base-color)");
        clusterCard.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        clusterCard.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        clusterCard.getStyle().set("box-shadow", "var(--lumo-box-shadow-xs)");
        clusterCard.getStyle().set("margin-bottom", "var(--lumo-space-m)");

        // Header
        var header = new Div();
        header.addClassName("dedup-cluster-head");
        header.getStyle().set("display", "flex");
        header.getStyle().set("align-items", "center");
        header.getStyle().set("gap", "var(--lumo-space-s)");
        header.getStyle().set("padding", "var(--lumo-space-m)");
        header.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        var title = new Span(cluster.survivorText());
        title.getStyle().set("font-weight", "600");
        title.getStyle().set("font-size", "var(--lumo-font-size-m)");
        title.getStyle().set("flex", "1");
        header.add(title);

        var sigInfo = new Span(cluster.losers().size() + 1 + " propositions");
        sigInfo.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        sigInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");
        header.add(sigInfo);

        clusterCard.add(header);

        // Body
        var body = new Div();
        body.addClassName("dedup-cluster-body");
        body.getStyle().set("padding", "var(--lumo-space-m)");
        body.getStyle().set("display", "flex");
        body.getStyle().set("flex-direction", "column");
        body.getStyle().set("gap", "var(--lumo-space-xs)");

        // Survivor row
        var survivorRow = renderMemberRow(cluster.survivorId(), cluster.survivorText(), 1.0, true, cluster);
        body.add(survivorRow);

        // Loser rows
        for (var loser : cluster.losers()) {
            var loserEdge = cluster.edges().stream()
                    .filter(e -> e.anchorId().equals(loser.id()) || e.memberId().equals(loser.id()))
                    .findFirst();
            var confidence = loserEdge.map(DedupPreview.Edge::aggregateScore).orElse(0.0);
            var loserRow = renderMemberRow(loser.id(), loser.text(), confidence, false, cluster);
            body.add(loserRow);
        }

        // Action buttons
        var actions = new Div();
        actions.addClassName("dedup-cluster-actions");
        actions.getStyle().set("display", "flex");
        actions.getStyle().set("gap", "var(--lumo-space-xs)");
        actions.getStyle().set("padding-top", "var(--lumo-space-xs)");

        var applyBtn = new Button("Apply cluster");
        applyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        applyBtn.addClickListener(e -> {
            if (onApply != null) {
                onApply.run();
            }
        });

        var skipBtn = new Button("Skip");
        skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        actions.add(applyBtn, skipBtn);
        body.add(actions);

        clusterCard.add(body);
        return clusterCard;
    }

    private Div renderMemberRow(String memberId, String text, double confidence, boolean isSurvivor, DedupPreview.Cluster cluster) {
        var row = new Div();
        row.addClassName("dedup-member-row");
        row.getStyle().set("display", "flex");
        row.getStyle().set("align-items", "center");
        row.getStyle().set("gap", "var(--lumo-space-xs)");
        row.getStyle().set("padding", "var(--lumo-space-xs)");
        row.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        row.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        row.getStyle().set("position", "relative");

        if (isSurvivor) {
            row.getStyle().set("background", "var(--lumo-success-color-10pct)");
            row.getStyle().set("border-color", "var(--lumo-success-color)");
        } else {
            row.getStyle().set("background", "var(--lumo-base-color)");
        }

        // Badge
        var badge = new Span(isSurvivor ? "Survivor" : "Merge");
        badge.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        badge.getStyle().set("font-weight", "700");
        badge.getStyle().set("text-transform", "uppercase");
        badge.getStyle().set("letter-spacing", ".03em");
        badge.getStyle().set("padding", "2px 6px");
        badge.getStyle().set("border-radius", "4px");
        badge.getStyle().set("flex-shrink", "0");
        badge.getStyle().set("width", "58px");
        badge.getStyle().set("text-align", "center");

        if (isSurvivor) {
            badge.getStyle().set("background", "var(--lumo-success-color)");
            badge.getStyle().set("color", "white");
        } else {
            badge.getStyle().set("background", "var(--lumo-contrast-10pct)");
            badge.getStyle().set("color", "var(--lumo-secondary-text-color)");
        }

        // Text
        var textSpan = new Span(text);
        textSpan.getStyle().set("flex", "1");
        textSpan.getStyle().set("font-size", "var(--lumo-font-size-m)");
        textSpan.getStyle().set("overflow", "hidden");
        textSpan.getStyle().set("text-overflow", "ellipsis");
        textSpan.getStyle().set("white-space", "nowrap");

        // Score
        var score = new Span(String.format("conf %.2f", confidence));
        score.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        score.getStyle().set("color", "var(--lumo-secondary-text-color)");
        score.getStyle().set("flex-shrink", "0");
        score.getStyle().set("width", "56px");
        score.getStyle().set("text-align", "right");

        // Info button for signals
        var infoBtn = new Button("i");
        infoBtn.addClassName("dedup-signal-btn");
        infoBtn.getStyle().set("flex-shrink", "0");
        infoBtn.getStyle().set("width", "20px");
        infoBtn.getStyle().set("height", "20px");
        infoBtn.getStyle().set("border-radius", "50%");
        infoBtn.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        infoBtn.getStyle().set("background", "var(--lumo-base-color)");
        infoBtn.getStyle().set("color", "var(--lumo-secondary-text-color)");
        infoBtn.getStyle().set("padding", "0");
        infoBtn.getStyle().set("min-width", "20px");
        infoBtn.getStyle().set("font-size", "12px");
        infoBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        infoBtn.getElement().setAttribute("title", "View merge signals");

        row.add(badge, textSpan, score, infoBtn);

        // Create popover for signals (hidden by default)
        var popover = createPopover(memberId, cluster);
        popover.getStyle().set("display", "none");
        row.add(popover);

        // Toggle popover on button click
        infoBtn.addClickListener(e -> {
            var display = popover.getStyle().get("display");
            popover.getStyle().set("display", "none".equals(display) ? "block" : "none");
        });

        memberRowToPopoverMap.put(row, popover);

        return row;
    }

    private Div createPopover(String memberId, DedupPreview.Cluster cluster) {
        var popover = new Div();
        popover.addClassName("dedup-signal-popover");
        popover.getStyle().set("position", "absolute");
        popover.getStyle().set("top", "calc(100% + 8px)");
        popover.getStyle().set("right", "44px");
        popover.getStyle().set("width", "260px");
        popover.getStyle().set("background", "var(--lumo-base-color)");
        popover.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        popover.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        popover.getStyle().set("box-shadow", "var(--lumo-box-shadow-l)");
        popover.getStyle().set("z-index", "5");
        popover.getStyle().set("padding", "var(--lumo-space-s)");

        var heading = new Span("Merge signals — vs. survivor");
        heading.getStyle().set("display", "block");
        heading.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
        heading.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        heading.getStyle().set("font-weight", "650");
        popover.add(heading);

        var edge = cluster.edges().stream()
                .filter(e -> e.anchorId().equals(memberId) || e.memberId().equals(memberId))
                .findFirst();

        if (edge.isPresent()) {
            for (var signal : edge.get().signals()) {
                var sigRow = new Div();
                sigRow.getStyle().set("display", "flex");
                sigRow.getStyle().set("align-items", "center");
                sigRow.getStyle().set("justify-content", "space-between");
                sigRow.getStyle().set("padding", "4px 0");
                sigRow.getStyle().set("font-size", "var(--lumo-font-size-xs)");

                var sigName = new Span(signal.signal());
                sigName.addClassName("collapse-signal");
                if (signal.veto()) {
                    sigName.addClassName("collapse-signal-veto");
                }
                sigName.getStyle().set("color", "var(--lumo-secondary-text-color)");

                var sigBar = new Div();
                sigBar.getStyle().set("width", "70px");
                sigBar.getStyle().set("height", "5px");
                sigBar.getStyle().set("border-radius", "3px");
                sigBar.getStyle().set("background", "var(--lumo-contrast-10pct)");
                sigBar.getStyle().set("overflow", "hidden");
                sigBar.getStyle().set("margin", "0 var(--lumo-space-xs)");

                var sigBarInner = new Div();
                sigBarInner.getStyle().set("height", "100%");
                sigBarInner.getStyle().set("background", "var(--lumo-primary-color)");
                sigBarInner.getStyle().set("width", Math.round(signal.score() * 100) + "%");
                sigBar.add(sigBarInner);

                var sigVal = new Span(String.format("%.2f", signal.score()));
                sigVal.getStyle().set("width", "32px");
                sigVal.getStyle().set("text-align", "right");
                sigVal.getStyle().set("color", "var(--lumo-secondary-text-color)");
                sigVal.getStyle().set("font-variant-numeric", "tabular-nums");

                sigRow.add(sigName, sigBar, sigVal);
                popover.add(sigRow);
            }

            var verdict = new Div();
            verdict.getStyle().set("margin-top", "var(--lumo-space-xs)");
            verdict.getStyle().set("padding-top", "var(--lumo-space-xs)");
            verdict.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");
            verdict.getStyle().set("font-size", "var(--lumo-font-size-xs)");
            verdict.getStyle().set("color", "var(--lumo-success-color)");
            verdict.getStyle().set("font-weight", "600");
            verdict.setText("✓ Above merge threshold (0.85)");
            popover.add(verdict);
        }

        return popover;
    }

    private Div renderNonMerge(DedupPreview.Edge edge) {
        var div = new Div();
        div.addClassName("dedup-nonmerge-edge");

        var label = new Span(edge.anchorId() + " ↔ " + edge.memberId());
        label.addClassName("dedup-nonmerge-label");
        div.add(label);
        div.add(renderSignals(edge));

        return div;
    }

    private VerticalLayout renderSignals(DedupPreview.Edge edge) {
        var signalsLayout = new VerticalLayout();
        signalsLayout.setPadding(false);
        signalsLayout.setSpacing(false);
        signalsLayout.addClassName("dedup-signals");

        for (var signal : edge.signals()) {
            var scorePct = (int) Math.round(signal.score() * 100);
            var reason = signal.explanation() != null ? " — " + signal.explanation() : "";
            var signalSpan = new Span(signal.signal() + ": " + scorePct + "%" + reason);
            signalSpan.addClassName("collapse-signal");
            if (signal.veto()) {
                signalSpan.addClassName("collapse-signal-veto");
            }
            signalsLayout.add(signalSpan);
        }

        return signalsLayout;
    }

    /**
     * Plain, host-agnostic view of one sweep's proposed dedup outcome: what would merge into what,
     * scored with per-signal evidence, plus the edges that were considered but vetoed.
     *
     * @param runId     id of the sweep run this preview belongs to
     * @param clusters  the proposed merge clusters, survivor plus losers
     * @param nonMerges edges the sweep considered but did not merge (e.g. vetoed)
     */
    public record DedupPreview(String runId, List<Cluster> clusters, List<Edge> nonMerges) {

        /**
         * One proposed merge: the proposition that would survive, the ones that would fold into it,
         * and the scored edges behind the grouping.
         *
         * @param survivorId   id of the proposition that would be kept
         * @param survivorText text of the surviving proposition
         * @param losers       propositions that would be folded into the survivor
         * @param edges        the scored pairs behind this cluster's grouping
         */
        public record Cluster(String survivorId, String survivorText, List<Member> losers, List<Edge> edges) {
        }

        /**
         * A proposition referenced by id and text, independent of any host-specific type.
         *
         * @param id   the proposition's id
         * @param text the proposition's text
         */
        public record Member(String id, String text) {
        }

        /**
         * One scored candidate pair, with its per-signal breakdown.
         *
         * @param anchorId       id of the anchor proposition in the pair
         * @param memberId       id of the other proposition in the pair
         * @param aggregateScore the blended score across all signals
         * @param vetoed         true if a signal vetoed the merge outright
         * @param signals        the individual signal contributions behind the aggregate score
         */
        public record Edge(String anchorId, String memberId, double aggregateScore, boolean vetoed, List<Signal> signals) {
        }

        /**
         * One signal's contribution to an edge's score.
         *
         * @param signal      name of the signal (e.g. "vector", "lexical", "entity-overlap")
         * @param score       the signal's raw score
         * @param weight      the weight this signal carried in the blend
         * @param veto        true if this signal vetoed the merge
         * @param explanation a short human-readable reason for the score, if the signal provided one
         */
        public record Signal(String signal, double score, double weight, boolean veto, String explanation) {
        }
    }
}
