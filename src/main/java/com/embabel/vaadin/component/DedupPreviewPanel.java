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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
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
    private final Button applyButton;
    private final Button undoButton;

    private Runnable onApply;
    private Consumer<String> onUndo;
    private DedupPreview current;

    /**
     * Builds an empty panel. Call {@link #show(DedupPreview)} to populate it with a sweep preview.
     */
    public DedupPreviewPanel() {
        addClassName("dedup-preview-panel");
        setPadding(false);
        setSpacing(true);

        clustersLayout.setPadding(false);
        clustersLayout.setSpacing(true);
        clustersLayout.addClassName("dedup-clusters");

        nonMergesLayout.setPadding(false);
        nonMergesLayout.setSpacing(false);
        nonMergesLayout.addClassName("dedup-nonmerges");

        applyButton = new Button("Apply");
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

        var actions = new HorizontalLayout(applyButton, undoButton);
        actions.addClassName("dedup-actions");
        actions.setSpacing(true);

        add(clustersLayout, nonMergesLayout, actions);
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

        for (var cluster : preview.clusters()) {
            clustersLayout.add(renderCluster(cluster));
        }

        if (!preview.nonMerges().isEmpty()) {
            var heading = new Span("Did not merge");
            heading.addClassName("dedup-nonmerge-heading");
            nonMergesLayout.add(heading);
            for (var edge : preview.nonMerges()) {
                nonMergesLayout.add(renderNonMerge(edge));
            }
        }
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
        var div = new Div();
        div.addClassName("dedup-cluster");

        var survivorSpan = new Span("Keep: " + cluster.survivorText());
        survivorSpan.addClassName("dedup-survivor-text");
        div.add(survivorSpan);

        for (var loser : cluster.losers()) {
            div.add(renderLoser(cluster, loser));
        }

        return div;
    }

    private VerticalLayout renderLoser(DedupPreview.Cluster cluster, DedupPreview.Member loser) {
        var section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.addClassName("dedup-loser");

        var textSpan = new Span("Fold in: " + loser.text());
        textSpan.addClassName("dedup-loser-text");
        section.add(textSpan);

        cluster.edges().stream()
                .filter(e -> e.anchorId().equals(loser.id()) || e.memberId().equals(loser.id()))
                .findFirst()
                .ifPresent(edge -> section.add(renderSignals(edge)));

        return section;
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
