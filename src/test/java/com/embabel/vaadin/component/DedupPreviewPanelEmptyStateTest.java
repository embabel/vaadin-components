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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that DedupPreviewPanel renders an appropriate empty state when there are no clusters
 * and no non-merges, and transitions correctly between empty and populated states.
 */
class DedupPreviewPanelEmptyStateTest {

    @Test
    void emptyPreviewRendersEmptyState() {
        var panel = new DedupPreviewPanel();
        var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(), List.of());

        panel.show(preview);

        var emptyStates = findComponentsByClassName(panel, "dedup-empty-state");
        var clusters = findComponentsByClassName(panel, "dedup-cluster");
        var actionButtons = findComponentsByClassName(panel, "dedup-actions");

        assertEquals(1, emptyStates.size(), "must render exactly one empty state div");
        assertTrue(emptyStates.get(0).isVisible(), "empty state div must be visible");
        assertEquals(0, clusters.size(), "must render zero cluster elements");
        assertEquals(1, actionButtons.size(), "actions layout should exist but be hidden");
        assertFalse(actionButtons.get(0).isVisible(), "actions layout must be hidden in empty state");
    }

    @Test
    void showingClusterAfterEmptyStateHidesEmptyStateAndShowsCluster() {
        var panel = new DedupPreviewPanel();

        // First show an empty preview
        var emptyPreview = new DedupPreviewPanel.DedupPreview("run-1", List.of(), List.of());
        panel.show(emptyPreview);

        var emptyStatesInitial = findComponentsByClassName(panel, "dedup-empty-state");
        assertTrue(emptyStatesInitial.get(0).isVisible(), "empty state should be visible initially");

        // Now show a preview with one cluster
        var survivor = new DedupPreviewPanel.DedupPreview.Member("survivor-1", "Alice");
        var loser = new DedupPreviewPanel.DedupPreview.Member("loser-1", "alice");
        var edge = new DedupPreviewPanel.DedupPreview.Edge("survivor-1", "loser-1", 0.90, false, List.of());
        var cluster = new DedupPreviewPanel.DedupPreview.Cluster("survivor-1", "Alice", List.of(loser), List.of(edge));
        var populatedPreview = new DedupPreviewPanel.DedupPreview("run-2", List.of(cluster), List.of());

        panel.show(populatedPreview);

        var emptyStatesFinal = findComponentsByClassName(panel, "dedup-empty-state");
        var clustersFinal = findComponentsByClassName(panel, "dedup-cluster");
        var actionButtonsFinal = findComponentsByClassName(panel, "dedup-actions");

        assertFalse(emptyStatesFinal.get(0).isVisible(), "empty state must be hidden after showing clusters");
        assertEquals(1, clustersFinal.size(), "must render one cluster");
        assertTrue(actionButtonsFinal.get(0).isVisible(), "actions must be visible when clusters exist");
    }

    @Test
    void previewWithOnlyNonMergesShowsNonMergesAndNoMergesNote() {
        var panel = new DedupPreviewPanel();

        var signal = new DedupPreviewPanel.DedupPreview.Signal("entity-overlap", 0.42, 1.0, false, "some reason");
        var edge = new DedupPreviewPanel.DedupPreview.Edge("alice-1", "alice-2", 0.42, false, List.of(signal));
        var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(), List.of(edge));

        panel.show(preview);

        var emptyStates = findComponentsByClassName(panel, "dedup-empty-state");
        var clusters = findComponentsByClassName(panel, "dedup-cluster");
        var nonMergesNotes = findComponentsByClassName(panel, "dedup-no-merges-note");
        var nonMergeEdges = findComponentsByClassName(panel, "dedup-nonmerge-edge");
        var actionButtons = findComponentsByClassName(panel, "dedup-actions");

        assertFalse(emptyStates.get(0).isVisible(), "empty state should not be visible when non-merges exist");
        assertEquals(0, clusters.size(), "must render zero clusters");
        assertEquals(1, nonMergesNotes.size(), "must render 'No merges proposed' note when only non-merges exist");
        assertEquals(1, nonMergeEdges.size(), "must render the non-merge edge");
        assertTrue(actionButtons.get(0).isVisible(), "actions should be visible even with only non-merges");
    }

    /**
     * Finds all components with the given class name in the component tree. Uses depth-first
     * tree walk to collect all descendants.
     */
    private static List<Component> findComponentsByClassName(Component root, String className) {
        var results = new ArrayList<Component>();
        collectByClassName(root, className, results);
        return results;
    }

    private static void collectByClassName(Component component, String className, List<Component> results) {
        if (component.getClassNames().contains(className)) {
            results.add(component);
        }
        component.getChildren().forEach(child -> collectByClassName(child, className, results));
    }
}
