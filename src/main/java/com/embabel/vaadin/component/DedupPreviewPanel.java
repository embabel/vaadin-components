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

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final HorizontalLayout toolbarLayout = new HorizontalLayout();
    private final HorizontalLayout footerLayout = new HorizontalLayout();
    private final Div modePill = new Div();
    private final Button rescanButton = new Button("Rescan");
    private final Span footerLabel = new Span();
    private final Map<Div, Div> memberRowToPopoverMap = new HashMap<>();
    private final Set<String> appliedClusterIds = new HashSet<>();
    // survivorId -> loser ids currently checked for that cluster (unchecking excludes a loser
    // from the merge; a cluster with no entry here defaults to "everything checked").
    private final Map<String, Set<String>> clusterSelections = new HashMap<>();
    // proposed survivorId -> the id the user has chosen as the actual survivor (defaults to the
    // proposed survivorId itself until the user picks a different row).
    private final Map<String, String> clusterSurvivorChoice = new HashMap<>();

    private Runnable onApply;
    private Consumer<ClusterApplyRequest> onApplyCluster;
    private Consumer<String> onUndo;
    private Runnable onRescan;
    private DedupPreview current;
    private boolean dryRunMode = true;
    private String runMetaLabel;
    private double matchThreshold = Double.NaN;
    private Div openPopover;

    /**
     * Builds an empty panel. Call {@link #show(DedupPreview)} to populate it with a sweep preview.
     */
    public DedupPreviewPanel() {
        addClassName("dedup-preview-panel");
        setPadding(true);
        setSpacing(true);
        getStyle().set("padding", "calc(var(--lumo-space-m) * 1.5)");

        // Escape always closes an open signal popover, wherever focus is.
        Shortcuts.addShortcutListener(this, this::closeOpenPopover, Key.ESCAPE);
        // Clicking anywhere in the panel that isn't the popover or its trigger closes it.
        // Popovers and signal buttons stop this event from reaching them (see createPopover /
        // renderMemberRow), so this only fires for genuine "outside" clicks.
        getElement().addEventListener("click", e -> closeOpenPopover());

        // Toolbar
        toolbarLayout.addClassName("dedup-toolbar");
        toolbarLayout.setWidthFull();
        toolbarLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbarLayout.setSpacing(true);
        toolbarLayout.setVisible(false);

        var title = new Span("Dedup sweep");
        title.getStyle().set("font-weight", "600");
        title.getStyle().set("font-size", "14.5px");
        title.getStyle().set("flex", "0");
        toolbarLayout.add(title);

        modePill.addClassName("dedup-mode-pill");
        modePill.getStyle().set("font-size", "11px");
        modePill.getStyle().set("font-weight", "600");
        modePill.getStyle().set("padding", "3px 9px");
        modePill.getStyle().set("border-radius", "999px");
        modePill.getStyle().set("background", "var(--lumo-primary-color-10pct)");
        modePill.getStyle().set("color", "var(--lumo-primary-color)");
        modePill.getStyle().set("display", "inline-flex");
        modePill.getStyle().set("align-items", "center");
        modePill.getStyle().set("gap", "5px");
        modePill.setText("• Dry run");
        toolbarLayout.add(modePill);

        rescanButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        rescanButton.addClassName("dedup-rescan");
        rescanButton.setVisible(false);
        rescanButton.addClickListener(e -> {
            if (onRescan != null) {
                onRescan.run();
            }
        });
        toolbarLayout.add(rescanButton);

        applyButton = new Button("Apply all");
        applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        applyButton.addClassName("dedup-apply");
        applyButton.getElement().setAttribute("title", "Apply this sweep: retire the duplicates shown, keeping each cluster's survivor");
        applyButton.addClickListener(e -> {
            // "Apply all" fires a per-cluster request for every cluster with its current
            // selection, so hosts that migrated to setOnApplyCluster see the same selective
            // behavior a single cluster's Apply button gives. Hosts still on the old
            // parameterless onApply keep working unchanged.
            if (onApplyCluster != null && current != null) {
                for (var cluster : current.clusters()) {
                    onApplyCluster.accept(buildApplyRequest(cluster));
                }
            }
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

        clustersLayout.setPadding(false);
        clustersLayout.setSpacing(true);
        clustersLayout.addClassName("dedup-clusters");

        nonMergesLayout.setPadding(false);
        nonMergesLayout.setSpacing(false);
        nonMergesLayout.addClassName("dedup-nonmerges");

        // Footer
        footerLayout.addClassName("dedup-footer");
        footerLayout.setWidthFull();
        footerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        footerLayout.getStyle().set("padding", "10px 14px");
        footerLayout.getStyle().set("background", "var(--lumo-base-color)");
        footerLayout.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        footerLayout.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        footerLayout.getStyle().set("box-shadow", "var(--lumo-box-shadow-xs)");
        footerLayout.getStyle().set("font-size", "12px");
        footerLayout.getStyle().set("color", "var(--lumo-secondary-text-color)");
        footerLayout.setVisible(false);

        footerLabel.addClassName("dedup-footer-label");
        footerLayout.add(footerLabel);

        var footerSpacer = new Div();
        footerSpacer.setWidthFull();
        footerLayout.add(footerSpacer);

        var footerTime = new Span();
        footerTime.addClassName("dedup-footer-time");
        footerLayout.add(footerTime);

        actionsLayout = new HorizontalLayout(applyButton, undoButton);
        actionsLayout.addClassName("dedup-actions");
        actionsLayout.setSpacing(true);

        emptyStateDiv.addClassName("dedup-empty-state");
        emptyStateDiv.setVisible(false);

        add(toolbarLayout, emptyStateDiv, clustersLayout, nonMergesLayout, actionsLayout, footerLayout);
    }

    /**
     * Renders a sweep preview: proposed clusters with their survivor/losers and per-signal
     * evidence, plus any edges that were considered but vetoed.
     *
     * @param preview the sweep preview to render, as a plain host-supplied record
     */
    public void show(DedupPreview preview) {
        this.current = preview;
        this.appliedClusterIds.clear();
        this.clusterSelections.clear();
        this.clusterSurvivorChoice.clear();
        this.openPopover = null;
        clustersLayout.removeAll();
        nonMergesLayout.removeAll();
        emptyStateDiv.removeAll();

        boolean hasClusters = !preview.clusters().isEmpty();
        boolean hasNonMerges = !preview.nonMerges().isEmpty();

        // Always show toolbar and footer when preview is shown
        toolbarLayout.setVisible(true);
        updateModePill();

        if (!hasClusters && !hasNonMerges) {
            // Empty state: no clusters and no non-merges
            renderEmptyState();
            emptyStateDiv.setVisible(true);
            actionsLayout.setVisible(false);
            footerLayout.setVisible(false);
        } else {
            emptyStateDiv.setVisible(false);
            actionsLayout.setVisible(true);
            footerLayout.setVisible(true);
            updateFooter(preview);

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

    private void updateFooter(DedupPreview preview) {
        var clusterCount = preview.clusters().size();
        var appliedCount = (int) preview.clusters().stream()
                .filter(c -> appliedClusterIds.contains(c.survivorId()))
                .count();

        var label = clusterCount + " clusters found · " + appliedCount + " applied · " + (clusterCount - appliedCount) + " pending";
        if (dryRunMode) {
            label += " in dry run";
        } else {
            label = clusterCount + " clusters found · " + appliedCount + " applied";
        }
        footerLabel.setText(label);

        var footerTime = (Span) footerLayout.getComponentAt(2);
        if (runMetaLabel != null && !runMetaLabel.isEmpty()) {
            footerTime.setText(runMetaLabel);
            footerTime.setVisible(true);
        } else {
            footerTime.setVisible(false);
        }
    }

    /**
     * Marks a cluster as applied, triggering a re-render with applied-state anatomy
     * (strikethrough, disabled Apply button, Undo affordance).
     *
     * @param survivorId the survivor id of the cluster to mark as applied
     */
    public void markClusterApplied(String survivorId) {
        appliedClusterIds.add(survivorId);
        if (current != null) {
            updateFooter(current);
            // Re-render all clusters to update their state
            refreshClusters();
        }
    }

    /**
     * Closes whichever signal popover is currently open, if any. Called when another popover
     * opens, on Escape, and on outside clicks — so at most one popover is ever visible.
     */
    void closeOpenPopover() {
        if (openPopover != null) {
            openPopover.getStyle().set("display", "none");
            openPopover = null;
        }
    }

    private Set<String> checkedLoserIds(DedupPreview.Cluster cluster) {
        // Default: every loser is checked (whole cluster merges, unchanged from before selective
        // merge existed). Once the user touches a checkbox, the explicit set takes over.
        return clusterSelections.computeIfAbsent(cluster.survivorId(),
                id -> cluster.losers().stream().map(DedupPreview.Member::id)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private void updateClusterApplyButton(Button applyBtn, DedupPreview.Cluster cluster) {
        int checkedCount = checkedLoserIds(cluster).size();
        applyBtn.setText("Apply selected (" + checkedCount + ")");
        applyBtn.setEnabled(checkedCount > 0);
    }

    /**
     * The id of the row the user has chosen as the merge target for this cluster — the sweep's
     * proposed survivor until the user picks a different row.
     */
    private String effectiveSurvivorId(DedupPreview.Cluster cluster) {
        return clusterSurvivorChoice.getOrDefault(cluster.survivorId(), cluster.survivorId());
    }

    /**
     * Switches the chosen survivor for a cluster: the old survivor becomes an ordinary, checked
     * merge row, and the new survivor loses whatever checked/unchecked state it had as a loser.
     */
    private void chooseSurvivor(DedupPreview.Cluster cluster, String newSurvivorId) {
        var oldSurvivorId = effectiveSurvivorId(cluster);
        if (oldSurvivorId.equals(newSurvivorId)) {
            return;
        }
        clusterSurvivorChoice.put(cluster.survivorId(), newSurvivorId);
        var checked = checkedLoserIds(cluster);
        checked.remove(newSurvivorId);
        checked.add(oldSurvivorId);
        refreshClusters();
    }

    private ClusterApplyRequest buildApplyRequest(DedupPreview.Cluster cluster) {
        var effectiveSurvivor = effectiveSurvivorId(cluster);
        var checked = checkedLoserIds(cluster);
        var allMemberIds = new java.util.ArrayList<String>();
        allMemberIds.add(cluster.survivorId());
        cluster.losers().forEach(l -> allMemberIds.add(l.id()));
        var selected = allMemberIds.stream()
                .filter(id -> !id.equals(effectiveSurvivor))
                .filter(checked::contains)
                .toList();
        return new ClusterApplyRequest(effectiveSurvivor, selected);
    }

    private void refreshClusters() {
        if (current != null) {
            clustersLayout.removeAll();
            for (var cluster : current.clusters()) {
                clustersLayout.add(renderCluster(cluster));
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
     * Sets the handler invoked when a cluster is applied, either from its own "Apply selected"
     * button or (once per cluster) from the toolbar's "Apply all". Carries the survivor id and
     * only the loser ids the user left checked, so a host can merge exactly what was selected
     * instead of the whole cluster.
     *
     * @param handler receives one {@link ClusterApplyRequest} per applied cluster
     */
    public void setOnApplyCluster(Consumer<ClusterApplyRequest> handler) {
        this.onApplyCluster = handler;
    }

    /**
     * Sets the handler invoked when the user clicks Undo.
     *
     * @param handler receives the run id of the currently shown preview to undo
     */
    public void setOnUndo(Consumer<String> handler) {
        this.onUndo = handler;
    }

    /**
     * Sets the handler invoked when the user clicks Rescan.
     *
     * @param handler runs when the user requests a rescan; if unset, the Rescan button is hidden
     */
    public void setOnRescan(Runnable handler) {
        this.onRescan = handler;
        rescanButton.setVisible(handler != null);
    }

    /**
     * Sets the mode (dry-run vs applied) and updates the mode pill display.
     *
     * @param dryRun true for dry-run mode, false for applied mode
     */
    public void setMode(boolean dryRun) {
        this.dryRunMode = dryRun;
        updateModePill();
    }

    /**
     * Sets optional run metadata (e.g., "Last sweep: Jun 30, 08:00") to display in the footer.
     *
     * @param label the metadata label; if null, the footer time is hidden
     */
    public void setRunMeta(String label) {
        this.runMetaLabel = label;
    }

    /**
     * Sets the merge threshold for verdict display in signal popovers. If unset, the verdict
     * shows only the aggregate score without threshold comparison.
     *
     * @param threshold the threshold value (e.g., 0.85)
     */
    public void setMatchThreshold(double threshold) {
        this.matchThreshold = threshold;
    }

    private void updateModePill() {
        if (dryRunMode) {
            modePill.getStyle().set("background", "var(--lumo-primary-color-10pct)");
            modePill.getStyle().set("color", "var(--lumo-primary-color)");
            modePill.setText("• Dry run");
        } else {
            modePill.getStyle().set("background", "var(--lumo-success-color-10pct)");
            modePill.getStyle().set("color", "var(--lumo-success-color)");
            modePill.setText("✓ Applied");
        }
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
        header.getStyle().set("gap", "8px");
        header.getStyle().set("padding", "11px 14px");
        header.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        var title = new Span(cluster.survivorText());
        title.getStyle().set("font-weight", "600");
        title.getStyle().set("font-size", "12.5px");
        title.getStyle().set("flex", "1");
        header.add(title);

        // Compute and display average similarity. An applied cluster always shows
        // "applied" regardless of whether edge data is present; otherwise show the
        // computed average only when we actually have edges to compute it from —
        // never fabricate a number.
        var avgSimilarity = computeAverageSimilarity(cluster);
        var sigInfoText = cluster.losers().size() + 1 + " propositions";
        if (appliedClusterIds.contains(cluster.survivorId())) {
            sigInfoText += " · applied";
        } else if (!Double.isNaN(avgSimilarity)) {
            sigInfoText += " · " + String.format("%.2f avg similarity", avgSimilarity);
        }
        var sigInfo = new Span(sigInfoText);
        sigInfo.getStyle().set("font-size", "11px");
        sigInfo.getStyle().set("color", "var(--lumo-secondary-text-color)");
        header.add(sigInfo);

        clusterCard.add(header);

        // Body
        var body = new Div();
        body.addClassName("dedup-cluster-body");
        body.getStyle().set("padding", "10px 14px 14px");
        body.getStyle().set("display", "flex");
        body.getStyle().set("flex-direction", "column");
        body.getStyle().set("gap", "6px");

        boolean isApplied = appliedClusterIds.contains(cluster.survivorId());

        if (isApplied) {
            // Survivor row: derive confidence from survivor edge if available
            var survivorConfidence = deriveSurvivorConfidence(cluster);
            var survivorRow = renderMemberRow(cluster.survivorId(), cluster.survivorText(), survivorConfidence, true, cluster, true);
            body.add(survivorRow);
        }

        // Action buttons — built before the loser rows so each loser's checkbox can update the
        // apply button's label/enabled state as the user (un)checks it.
        var actions = new Div();
        actions.addClassName("dedup-cluster-actions");
        actions.getStyle().set("display", "flex");
        actions.getStyle().set("gap", "8px");
        actions.getStyle().set("padding-top", "4px");

        if (!isApplied) {
            var applyBtn = new Button();
            applyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            applyBtn.addClassName("dedup-cluster-apply");
            updateClusterApplyButton(applyBtn, cluster);
            applyBtn.addClickListener(e -> {
                markClusterApplied(cluster.survivorId());
                if (onApplyCluster != null) {
                    onApplyCluster.accept(buildApplyRequest(cluster));
                }
                if (onApply != null) {
                    onApply.run();
                }
            });

            // Every row — the currently chosen survivor and every merge row — carries a "pick
            // survivor" radio, defaulting to the sweep's proposed survivor. Selecting another
            // row moves the SURVIVOR badge/highlight to it, and the old survivor becomes an
            // ordinary, checked merge row (see chooseSurvivor). The row list stays in a fixed
            // order (proposed survivor first, then losers) — only which row plays the survivor
            // changes.
            var effectiveSurvivor = effectiveSurvivorId(cluster);
            var checked = checkedLoserIds(cluster);

            var rowIds = new java.util.ArrayList<String>();
            rowIds.add(cluster.survivorId());
            cluster.losers().forEach(l -> rowIds.add(l.id()));

            for (var rowId : rowIds) {
                boolean rowIsSurvivor = rowId.equals(effectiveSurvivor);
                String text;
                double confidence;
                if (rowId.equals(cluster.survivorId())) {
                    text = cluster.survivorText();
                    confidence = deriveSurvivorConfidence(cluster);
                } else {
                    var member = cluster.losers().stream().filter(l -> l.id().equals(rowId)).findFirst().orElseThrow();
                    var edge = cluster.edges().stream()
                            .filter(e -> e.anchorId().equals(rowId) || e.memberId().equals(rowId))
                            .findFirst();
                    text = member.text();
                    confidence = edge.map(DedupPreview.Edge::aggregateScore).orElse(0.0);
                }

                var row = renderMemberRow(rowId, text, confidence, rowIsSurvivor, cluster, false);

                var survivorRadio = new Checkbox();
                survivorRadio.addClassName("dedup-pick-survivor");
                survivorRadio.setValue(rowIsSurvivor);
                survivorRadio.getElement().setAttribute("title", rowIsSurvivor
                        ? "Survivor — the memory the others merge into"
                        : "Make this the survivor");
                survivorRadio.addValueChangeListener(ev -> {
                    if (Boolean.TRUE.equals(ev.getValue())) {
                        chooseSurvivor(cluster, rowId);
                    } else {
                        // A radio can't be unchecked by itself — there's always exactly one
                        // survivor.
                        survivorRadio.setValue(true);
                    }
                });
                row.getElement().insertChild(0, survivorRadio.getElement());

                if (!rowIsSurvivor) {
                    row.addClassName("dedup-member-row-mergeable");
                    if (!checked.contains(rowId)) {
                        row.addClassName("dedup-member-row-unpicked");
                    }

                    var pick = new Checkbox();
                    pick.addClassName("dedup-pick");
                    pick.setValue(checked.contains(rowId));
                    pick.getElement().setAttribute("title", "Include this proposition in the merge");
                    pick.addValueChangeListener(ev -> {
                        var selection = checkedLoserIds(cluster);
                        if (Boolean.TRUE.equals(ev.getValue())) {
                            selection.add(rowId);
                            row.removeClassName("dedup-member-row-unpicked");
                        } else {
                            selection.remove(rowId);
                            row.addClassName("dedup-member-row-unpicked");
                        }
                        updateClusterApplyButton(applyBtn, cluster);
                    });
                    row.getElement().insertChild(1, pick.getElement());
                }

                body.add(row);
            }

            var skipBtn = new Button("Skip");
            skipBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            actions.add(applyBtn, skipBtn);
        } else {
            // Applied clusters render plainly: losers are struck through, no checkboxes —
            // selection only matters before a cluster is applied.
            for (var loser : cluster.losers()) {
                var loserEdge = cluster.edges().stream()
                        .filter(e -> e.anchorId().equals(loser.id()) || e.memberId().equals(loser.id()))
                        .findFirst();
                var confidence = loserEdge.map(DedupPreview.Edge::aggregateScore).orElse(0.0);
                var loserRow = renderMemberRow(loser.id(), loser.text(), confidence, false, cluster, isApplied);
                body.add(loserRow);
            }

            var appliedBtn = new Button("Applied ✓");
            appliedBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
            appliedBtn.setEnabled(false);

            var undoClusterBtn = new Button("Undo");
            undoClusterBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            undoClusterBtn.addClickListener(e -> {
                if (onUndo != null && current != null) {
                    onUndo.accept(current.runId());
                }
            });

            actions.add(appliedBtn, undoClusterBtn);
        }

        body.add(actions);

        clusterCard.add(body);
        return clusterCard;
    }

    private double computeAverageSimilarity(DedupPreview.Cluster cluster) {
        if (cluster.edges().isEmpty()) {
            return Double.NaN;
        }
        var sum = cluster.edges().stream()
                .mapToDouble(DedupPreview.Edge::aggregateScore)
                .sum();
        return sum / cluster.edges().size();
    }

    private double deriveSurvivorConfidence(DedupPreview.Cluster cluster) {
        // Try to find an edge involving the survivor
        var survivorEdge = cluster.edges().stream()
                .filter(e -> e.anchorId().equals(cluster.survivorId()) || e.memberId().equals(cluster.survivorId()))
                .findFirst();
        if (survivorEdge.isPresent()) {
            return survivorEdge.get().aggregateScore();
        }
        // No edge found for survivor, don't show a fake value
        return Double.NaN;
    }

    private Div renderMemberRow(String memberId, String text, double confidence, boolean isSurvivor, DedupPreview.Cluster cluster, boolean isApplied) {
        var row = new Div();
        row.addClassName("dedup-member-row");
        if (isApplied && !isSurvivor) {
            row.addClassName("dedup-member-row-strike");
        }
        row.getStyle().set("display", "flex");
        row.getStyle().set("align-items", "center");
        row.getStyle().set("gap", "10px");
        row.getStyle().set("padding", "8px 10px");
        row.getStyle().set("border-radius", "6px");
        row.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        row.getStyle().set("position", "relative");

        if (isSurvivor) {
            row.getStyle().set("background", "var(--lumo-success-color-10pct)");
            row.getStyle().set("border-color", "var(--lumo-success-color)");
        } else {
            row.getStyle().set("background", "var(--lumo-base-color)");
        }

        // Badge
        String badgeText = isSurvivor ? "Survivor" : (isApplied ? "Merged" : "Merge");
        var badge = new Span(badgeText);
        badge.getStyle().set("font-size", "9.5px");
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
        textSpan.getStyle().set("font-size", "12.5px");
        textSpan.getStyle().set("overflow", "hidden");
        textSpan.getStyle().set("text-overflow", "ellipsis");
        textSpan.getStyle().set("white-space", "nowrap");

        if (isApplied && !isSurvivor) {
            textSpan.getStyle().set("text-decoration", "line-through");
            textSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        }

        // Score
        var scoreSpan = new Span();
        if (!Double.isNaN(confidence)) {
            scoreSpan.setText(String.format("conf %.2f", confidence));
        } else {
            scoreSpan.setText("");
        }
        scoreSpan.getStyle().set("font-size", "11px");
        scoreSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        scoreSpan.getStyle().set("flex-shrink", "0");
        scoreSpan.getStyle().set("width", "56px");
        scoreSpan.getStyle().set("text-align", "right");

        // Info button for signals
        var infoBtn = new Button("i");
        infoBtn.addClassName("dedup-signal-btn");
        infoBtn.getStyle().set("flex-shrink", "0");
        infoBtn.getStyle().set("width", "20px");
        infoBtn.getStyle().set("height", "20px");
        infoBtn.getStyle().set("border-radius", "50%");
        infoBtn.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        infoBtn.getStyle().set("background", "var(--lumo-base-color)");
        infoBtn.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        infoBtn.getStyle().set("padding", "0");
        infoBtn.getStyle().set("min-width", "20px");
        infoBtn.getStyle().set("font-size", "11px");
        infoBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        infoBtn.getElement().setAttribute("title", "View merge signals");
        // Its own click must not bubble to the panel's outside-click handler, or the popover
        // this click is about to open would immediately be closed again.
        infoBtn.getElement().executeJs("this.addEventListener('click', function(e){ e.stopPropagation(); });");

        row.add(badge, textSpan, scoreSpan, infoBtn);

        // Create popover for signals (hidden by default)
        var popover = createPopover(memberId, cluster);
        popover.getStyle().set("display", "none");
        // Clicks inside the popover are not "outside" clicks.
        popover.getElement().executeJs("this.addEventListener('click', function(e){ e.stopPropagation(); });");
        row.add(popover);

        // Popovers are exclusive: opening this one closes whichever one was open first, and
        // clicking the same button again just closes it.
        infoBtn.addClickListener(e -> {
            boolean wasOpen = openPopover == popover;
            closeOpenPopover();
            if (!wasOpen) {
                popover.getStyle().set("display", "block");
                openPopover = popover;
            }
        });

        memberRowToPopoverMap.put(row, popover);

        return row;
    }

    private Div createPopover(String memberId, DedupPreview.Cluster cluster) {
        var popover = new Div();
        popover.addClassName("dedup-signal-popover");
        popover.getStyle().set("position", "absolute");
        popover.getStyle().set("top", "38px");
        popover.getStyle().set("right", "44px");
        popover.getStyle().set("width", "260px");
        popover.getStyle().set("background", "var(--lumo-base-color)");
        popover.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
        popover.getStyle().set("border-radius", "6px");
        popover.getStyle().set("box-shadow", "0 6px 24px rgba(20,22,28,.18)");
        popover.getStyle().set("z-index", "5");
        popover.getStyle().set("padding", "12px 14px");

        var heading = new Span("Merge signals — vs. survivor");
        heading.getStyle().set("display", "block");
        heading.getStyle().set("margin-bottom", "8px");
        heading.getStyle().set("font-size", "11.5px");
        heading.getStyle().set("font-weight", "650");
        popover.add(heading);

        var edge = cluster.edges().stream()
                .filter(e -> e.anchorId().equals(memberId) || e.memberId().equals(memberId))
                .findFirst();

        if (edge.isPresent()) {
            var edgeValue = edge.get();
            for (var signal : edgeValue.signals()) {
                var sigRow = new Div();
                sigRow.getStyle().set("display", "flex");
                sigRow.getStyle().set("align-items", "center");
                sigRow.getStyle().set("justify-content", "space-between");
                sigRow.getStyle().set("padding", "4px 0");
                sigRow.getStyle().set("font-size", "11.5px");

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
                sigBar.getStyle().set("margin", "0 8px");

                var sigBarInner = new Div();
                sigBarInner.getStyle().set("height", "100%");
                sigBarInner.getStyle().set("background", "var(--lumo-primary-color)");
                sigBarInner.getStyle().set("width", Math.round(signal.score() * 100) + "%");
                sigBar.add(sigBarInner);

                var sigVal = new Span(String.format("%.2f", signal.score()));
                sigVal.getStyle().set("width", "32px");
                sigVal.getStyle().set("text-align", "right");
                sigVal.getStyle().set("color", "var(--lumo-tertiary-text-color)");
                sigVal.getStyle().set("font-variant-numeric", "tabular-nums");

                sigRow.add(sigName, sigBar, sigVal);
                popover.add(sigRow);
            }

            var verdict = new Div();
            verdict.getStyle().set("margin-top", "8px");
            verdict.getStyle().set("padding-top", "8px");
            verdict.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");
            verdict.getStyle().set("font-size", "11px");
            verdict.getStyle().set("font-weight", "600");

            if (edgeValue.vetoed()) {
                // Find and display the vetoing signal
                var vetoingSignal = edgeValue.signals().stream()
                        .filter(DedupPreview.Signal::veto)
                        .findFirst();
                var vetoLabel = vetoingSignal.isPresent()
                        ? "✗ Veto: " + vetoingSignal.get().signal()
                        : "✗ Vetoed";
                verdict.getStyle().set("color", "var(--lumo-error-color)");
                verdict.setText(vetoLabel);
            } else if (!Double.isNaN(matchThreshold)) {
                // If threshold is set, compare aggregate score to it
                verdict.getStyle().set("color", "var(--lumo-success-color)");
                if (edgeValue.aggregateScore() >= matchThreshold) {
                    verdict.setText("✓ Above merge threshold (" + String.format("%.2f", matchThreshold) + ")");
                } else {
                    verdict.getStyle().set("color", "var(--lumo-warning-color)");
                    verdict.setText("Below merge threshold (" + String.format("%.2f", matchThreshold) + ")");
                }
            } else {
                // No threshold set, just show the aggregate score
                verdict.getStyle().set("color", "var(--lumo-secondary-text-color)");
                verdict.setText("Aggregate score: " + String.format("%.2f", edgeValue.aggregateScore()));
            }

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
     * A request to apply one cluster's merge, carrying only the loser ids the user left checked —
     * unchecked losers are excluded from the merge rather than being merged wholesale.
     *
     * @param survivorId       id of the proposition the losers merge into
     * @param selectedLoserIds ids of the checked losers to fold into the survivor
     */
    public record ClusterApplyRequest(String survivorId, List<String> selectedLoserIds) {
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
