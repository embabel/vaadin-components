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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void openingOnePopoverClosesAnyPreviouslyOpenPopover() {
        var panel = twoLoserClusterPanel();

        var infoButtons = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains("dedup-signal-btn"))
                .map(c -> (Button) c)
                .toList();
        assertEquals(3, infoButtons.size(), "expected one signal button per row: survivor + 2 losers");

        var popovers = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("dedup-signal-popover"))
                .toList();
        assertEquals(3, popovers.size());

        infoButtons.get(0).click();
        assertEquals(1, visiblePopovers(popovers).size(), "opening the first popover must show exactly one");

        infoButtons.get(1).click();
        var visibleAfterSecond = visiblePopovers(popovers);
        assertEquals(1, visibleAfterSecond.size(), "opening a second popover must close the first: at most one open at a time");
        assertEquals(popovers.get(1), visibleAfterSecond.get(0), "the newly-opened popover must be the one left visible");
    }

    /**
     * Escape is wired via {@code Shortcuts.addShortcutListener(this, this::closeOpenPopover,
     * Key.ESCAPE)} in the constructor — the same pattern {@code Dialogs.enableEscClose} already
     * uses elsewhere in this module. Firing a real key event needs a browser, so this test drives
     * the exact method the shortcut calls; the wiring itself is a one-line library call with
     * established precedent, not custom logic that needs its own coverage.
     */
    @Test
    void escapeClosesTheOpenPopover() {
        var panel = twoLoserClusterPanel();
        var infoButtons = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.getElement().getClassList().contains("dedup-signal-btn"))
                .map(c -> (Button) c)
                .toList();
        infoButtons.get(0).click();

        var popovers = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("dedup-signal-popover"))
                .toList();
        assertEquals(1, visiblePopovers(popovers).size(), "popover must be open before Escape");

        panel.closeOpenPopover();

        assertEquals(0, visiblePopovers(popovers).size(), "Escape must close the open popover");
    }

    @Test
    void loserCheckboxesAreCheckedByDefault() {
        var panel = twoLoserClusterPanel();
        var checkboxes = allComponents(panel).stream()
                .filter(c -> c instanceof Checkbox)
                .map(c -> (Checkbox) c)
                .toList();

        assertEquals(2, checkboxes.size(), "each loser row must have one checkbox");
        assertTrue(checkboxes.stream().allMatch(Checkbox::getValue), "checkboxes must be checked by default");
    }

    @Test
    void uncheckingALoserDimsItsRowAndUpdatesTheApplyLabel() {
        var panel = twoLoserClusterPanel();
        var applyBtn = findButton(panel, "dedup-cluster-apply");
        assertEquals("Apply selected (2)", applyBtn.getText(), "both losers checked by default");

        var checkboxes = allComponents(panel).stream()
                .filter(c -> c instanceof Checkbox)
                .map(c -> (Checkbox) c)
                .toList();
        checkboxes.get(0).setValue(false);

        assertEquals("Apply selected (1)", applyBtn.getText(), "label must reflect the checked count");
        assertTrue(applyBtn.isEnabled(), "one loser still checked: button stays enabled");

        var unpickedRows = allComponents(panel).stream()
                .filter(c -> c.getElement().getClassList().contains("dedup-member-row-unpicked"))
                .toList();
        assertEquals(1, unpickedRows.size(), "unchecking a loser must dim exactly its own row");

        checkboxes.get(1).setValue(false);
        assertEquals("Apply selected (0)", applyBtn.getText());
        assertFalse(applyBtn.isEnabled(), "unchecking every loser must disable the apply button");
    }

    @Test
    void clusterApplyRequestCarriesOnlyTheCheckedLoserIds() {
        var panel = twoLoserClusterPanel();
        var checkboxes = allComponents(panel).stream()
                .filter(c -> c instanceof Checkbox)
                .map(c -> (Checkbox) c)
                .toList();
        checkboxes.get(0).setValue(false); // uncheck loser p-2, leave p-3 checked

        var captured = new AtomicReference<DedupPreviewPanel.ClusterApplyRequest>();
        panel.setOnApplyCluster(captured::set);

        findButton(panel, "dedup-cluster-apply").click();

        assertEquals("p-1", captured.get().survivorId());
        assertEquals(List.of("p-3"), captured.get().selectedLoserIds(), "unchecked loser p-2 must be excluded from the request");
    }

    @Test
    void applyAllFiresAPerClusterRequestForEachClusterWithItsCurrentSelection() {
        var clusterA = twoLoserCluster("p-1", "p-2", "p-3");
        var clusterB = twoLoserCluster("q-1", "q-2", "q-3");
        var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(clusterA, clusterB), List.of());

        var panel = new DedupPreviewPanel();
        panel.show(preview);

        // Uncheck one loser in cluster B only.
        var checkboxes = allComponents(panel).stream()
                .filter(c -> c instanceof Checkbox)
                .map(c -> (Checkbox) c)
                .toList();
        assertEquals(4, checkboxes.size());
        checkboxes.get(2).setValue(false); // first checkbox belonging to cluster B (q-2)

        var captured = new ArrayList<DedupPreviewPanel.ClusterApplyRequest>();
        panel.setOnApplyCluster(captured::add);

        findButton(panel, "dedup-apply").click(); // toolbar "Apply all"

        assertEquals(2, captured.size(), "Apply all must fire one request per cluster");
        var forA = captured.stream().filter(r -> r.survivorId().equals("p-1")).findFirst().orElseThrow();
        var forB = captured.stream().filter(r -> r.survivorId().equals("q-1")).findFirst().orElseThrow();
        assertEquals(List.of("p-2", "p-3"), forA.selectedLoserIds(), "cluster A's selection is untouched: both checked");
        assertEquals(List.of("q-3"), forB.selectedLoserIds(), "cluster B's unchecked loser q-2 must be excluded");
    }

    private static DedupPreviewPanel.DedupPreview.Cluster twoLoserCluster(String survivorId, String loser1Id, String loser2Id) {
        var signal = new DedupPreviewPanel.DedupPreview.Signal("lexical", 0.9, 1.0, false, "close match");
        var edge1 = new DedupPreviewPanel.DedupPreview.Edge(survivorId, loser1Id, 0.85, false, List.of(signal));
        var edge2 = new DedupPreviewPanel.DedupPreview.Edge(survivorId, loser2Id, 0.80, false, List.of(signal));
        return new DedupPreviewPanel.DedupPreview.Cluster(
                survivorId,
                "Jim lives in Brisbane",
                List.of(new DedupPreviewPanel.DedupPreview.Member(loser1Id, "Jim resides in Brisbane"),
                        new DedupPreviewPanel.DedupPreview.Member(loser2Id, "Jim's based in Brisbane")),
                List.of(edge1, edge2));
    }

    private static DedupPreviewPanel twoLoserClusterPanel() {
        var cluster = twoLoserCluster("p-1", "p-2", "p-3");
        var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());
        var panel = new DedupPreviewPanel();
        panel.show(preview);
        return panel;
    }

    private static List<Component> visiblePopovers(List<Component> popovers) {
        return popovers.stream()
                .filter(c -> "block".equals(c.getStyle().get("display")))
                .toList();
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
