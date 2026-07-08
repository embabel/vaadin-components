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
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@link DedupPreviewPanel} renders a sweep preview's clusters and vetoed edges with their
 * signal evidence, and that Apply/Undo fire the callbacks a host wires up. Only public API is
 * exercised — no reflection.
 */
class DedupPreviewPanelTest {

    @Test
    void rendersClustersAndVetoChipsAndFiresCallbacks() {
        var vetoedSignal = new DedupPreviewPanel.DedupPreview.Signal("vector", 0.2, 1.0, true, "below threshold");
        var okSignal = new DedupPreviewPanel.DedupPreview.Signal("lexical", 0.9, 1.0, false, "close match");

        var mergeEdge = new DedupPreviewPanel.DedupPreview.Edge("p-1", "p-2", 0.85, false, List.of(okSignal));
        var cluster = new DedupPreviewPanel.DedupPreview.Cluster(
                "p-1",
                "Jim lives in Brisbane",
                List.of(new DedupPreviewPanel.DedupPreview.Member("p-2", "Jim resides in Brisbane")),
                List.of(mergeEdge));

        var nonMergeEdge = new DedupPreviewPanel.DedupPreview.Edge("p-3", "p-4", 0.3, true, List.of(vetoedSignal));

        var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of(nonMergeEdge));

        var panel = new DedupPreviewPanel();
        panel.show(preview);

        var clusterDivs = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("dedup-cluster"))
                .toList();
        assertEquals(1, clusterDivs.size(), "must render one cluster");

        var vetoSpans = allComponents(panel).stream()
                .filter(c -> c instanceof Span && ((Span) c).getClassNames().contains("collapse-signal-veto"))
                .toList();
        assertEquals(1, vetoSpans.size(), "vetoed non-merge edge must render one veto-styled signal chip");

        var okSpans = allComponents(panel).stream()
                .filter(c -> c instanceof Span
                        && ((Span) c).getClassNames().contains("collapse-signal")
                        && !((Span) c).getClassNames().contains("collapse-signal-veto"))
                .toList();
        assertTrue(!okSpans.isEmpty(), "non-vetoed signal must render as a plain signal chip");

        var applyFired = new AtomicBoolean(false);
        var undoRunId = new AtomicReference<String>();
        panel.setOnApply(() -> applyFired.set(true));
        panel.setOnUndo(undoRunId::set);

        findButton(panel, "dedup-apply").click();
        findButton(panel, "dedup-undo").click();

        assertTrue(applyFired.get(), "Apply must fire the onApply callback");
        assertEquals("run-1", undoRunId.get(), "Undo must fire onUndo with the preview's runId");
    }

    private static Button findButton(Component root, String className) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains(className))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("button ." + className + " not found"));
    }

    private static List<Component> allComponents(Component root) {
        var out = new ArrayList<Component>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }
}
