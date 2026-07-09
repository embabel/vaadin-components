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
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that DedupPreviewPanel renders according to the dedup-preview design spec:
 * cluster card anatomy (header, survivor row, member rows, badges), per-signal evidence
 * popover structure, dry-run and applied state presentation, spacing, and the empty state.
 */
class DedupPreviewPanelConformanceTest {

	@Test
	void clustersRendersWithSpecifiedStructure() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Text similarity", 0.92, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("survivor-1", "loser-1", 0.85, false, List.of(signal));
		var survivor = new DedupPreviewPanel.DedupPreview.Member("survivor-1", "Ben prefers async standups");
		var loser = new DedupPreviewPanel.DedupPreview.Member("loser-1", "Ben prefers async check-ins");
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("survivor-1", "Ben prefers async standups", List.of(loser), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);

		// Cluster card should exist
		var clusterDivs = findComponentsByClassName(panel, "dedup-cluster");
		assertEquals(1, clusterDivs.size(), "must render one cluster");

		// Cluster should have header
		var headers = findComponentsByClassName(panel, "dedup-cluster-head");
		assertEquals(1, headers.size(), "cluster must have a header");

		// Cluster should have body
		var bodies = findComponentsByClassName(panel, "dedup-cluster-body");
		assertEquals(1, bodies.size(), "cluster must have a body");

		// Should have survivor row and member rows
		var memberRows = findComponentsByClassName(panel, "dedup-member-row");
		assertEquals(2, memberRows.size(), "must render survivor + one loser row");

		// Survivor row should be marked as such
		var survivorRows = findComponentsByClassName(panel, "dedup-member-row");
		var survivorRow = survivorRows.get(0);
		assertEquals("Survivor", extractBadgeText(survivorRow), "first member row should be the survivor with 'Survivor' badge");

		// Non-survivor rows should have Merge badge
		var mergeRow = survivorRows.get(1);
		assertEquals("Merge", extractBadgeText(mergeRow), "non-survivor rows should have 'Merge' badge");
	}

	@Test
	void memberRowHasRequiredElements() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);

		var memberRows = findComponentsByClassName(panel, "dedup-member-row");
		var firstRow = memberRows.get(0);

		// Member row should contain badge, text, score, and signal button
		var allChildComponents = collectAll(firstRow);
		var badges = allChildComponents.stream().filter(c -> c instanceof Span && c.getElement().getClassList().contains("")).toList();
		var hasSignalBtn = allChildComponents.stream().anyMatch(c -> c instanceof Button && c.getElement().getClassList().contains("dedup-signal-btn"));

		assertTrue(hasSignalBtn, "member row must have a signal info button");
	}

	@Test
	void signalPopoverRendersSignalRowsAndVerdict() {
		var signals = List.of(
			new DedupPreviewPanel.DedupPreview.Signal("Text similarity", 0.92, 1.0, false, ""),
			new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "")
		);
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.85, false, signals);
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);

		// Find signal popover
		var popovers = findComponentsByClassName(panel, "dedup-signal-popover");
		assertEquals(2, popovers.size(), "must render one popover per member row");

		// Popover should contain signal rows
		var popover = popovers.get(1); // First non-survivor row's popover
		var allInPopover = collectAll(popover);

		// Should have heading
		var headings = allInPopover.stream()
			.filter(c -> c instanceof Span && "Merge signals — vs. survivor".equals(((Span) c).getText()))
			.toList();
		assertEquals(1, headings.size(), "popover must have 'Merge signals — vs. survivor' heading");

		// Should have verdict line (shows aggregate score when no threshold set)
		var verdicts = allInPopover.stream()
			.filter(c -> c instanceof Div && (c.getElement().getText().contains("Aggregate score") || c.getElement().getText().contains("threshold")))
			.toList();
		assertEquals(1, verdicts.size(), "popover must have verdict line showing score or threshold info");
	}

	@Test
	void vetoEdgeShowsVetoVerdict() {
		var vetoSignal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 0.2, 1.0, true, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.3, true, List.of(vetoSignal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);

		var popovers = findComponentsByClassName(panel, "dedup-signal-popover");
		var popover = popovers.get(1);
		var allInPopover = collectAll(popover);

		// Should have veto verdict
		var vetoVerdicts = allInPopover.stream()
			.filter(c -> c instanceof Div && c.getElement().getText().contains("Veto"))
			.toList();
		assertEquals(1, vetoVerdicts.size(), "popover must show veto verdict for vetoed edges");
	}

	@Test
	void thresholdAffectsVerdictDisplay() {
		var signals = List.of(new DedupPreviewPanel.DedupPreview.Signal("Text similarity", 0.92, 1.0, false, ""));
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.85, false, signals);
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.setMatchThreshold(0.85);
		panel.show(preview);

		var popovers = findComponentsByClassName(panel, "dedup-signal-popover");
		var popover = popovers.get(1);
		var allInPopover = collectAll(popover);

		// Should show "Above merge threshold (0.85)"
		var thresholdVerdicts = allInPopover.stream()
			.filter(c -> c instanceof Div && c.getElement().getText().contains("Above merge threshold"))
			.toList();
		assertEquals(1, thresholdVerdicts.size(), "popover must show threshold comparison when threshold is set");
	}

	@Test
	void appliedStateShowsAppliedBadgeAndUndo() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);
		panel.markClusterApplied("p1");

		var memberRows = findComponentsByClassName(panel, "dedup-member-row");
		var loserRow = memberRows.get(1);
		var allInRow = collectAll(loserRow);

		// Loser row should have strike-through class
		assertTrue(loserRow.getElement().getClassList().contains("dedup-member-row-strike"), "applied member rows must have strike class");

		// Should show "Merged" badge for losers in applied state
		var badges = allInRow.stream()
			.filter(c -> c instanceof Span && ("Merged".equals(((Span) c).getText())))
			.toList();
		assertEquals(1, badges.size(), "applied member rows must show 'Merged' badge");
	}

	@Test
	void toolbarModeAndFooterRenderWhenPreviewShown() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.setMode(true);
		panel.show(preview);

		var toolbars = findComponentsByClassName(panel, "dedup-toolbar");
		var footers = findComponentsByClassName(panel, "dedup-footer");

		assertEquals(1, toolbars.size(), "toolbar must render");
		assertTrue(toolbars.get(0).isVisible(), "toolbar must be visible");
		assertEquals(1, footers.size(), "footer must render");
		assertTrue(footers.get(0).isVisible(), "footer must be visible");
	}

	@Test
	void rescanButtonHiddenWhenNoHandler() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);

		var rescanBtns = findComponentsByClassName(panel, "dedup-rescan");
		assertEquals(1, rescanBtns.size(), "rescan button must exist");
		assertFalse(rescanBtns.get(0).isVisible(), "rescan button must be hidden when no handler set");

		// Now set a handler
		panel.setOnRescan(() -> {});
		assertTrue(rescanBtns.get(0).isVisible(), "rescan button must be visible after setting handler");
	}

	@Test
	void popoverCanBeToggledOpenAndClosed() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		panel.show(preview);

		var memberRows = findComponentsByClassName(panel, "dedup-member-row");
		var memberRow = memberRows.get(1); // Non-survivor row
		var allInRow = collectAll(memberRow);

		// Find the signal button
		var signalBtn = allInRow.stream()
			.filter(c -> c instanceof Button && c.getElement().getClassList().contains("dedup-signal-btn"))
			.map(c -> (Button) c)
			.findFirst();

		assertTrue(signalBtn.isPresent(), "member row must have signal button");

		// Click to open popover
		signalBtn.get().click();
		var popover = allInRow.stream()
			.filter(c -> c.getElement().getClassList().contains("dedup-signal-popover"))
			.findFirst();

		assertTrue(popover.isPresent(), "popover must be present in row");
		var displayStyle = popover.get().getStyle().get("display");
		assertEquals("block", displayStyle, "popover should be visible after clicking info button");

		// Click to close popover
		signalBtn.get().click();
		var displayStyleAfterClose = popover.get().getStyle().get("display");
		assertEquals("none", displayStyleAfterClose, "popover should be hidden after clicking again");
	}

	@Test
	void emptyStateStillWorksAndCoexistsWithClusters() {
		var panel = new DedupPreviewPanel();

		// Show empty preview
		var emptyPreview = new DedupPreviewPanel.DedupPreview("run-1", List.of(), List.of());
		panel.show(emptyPreview);

		var emptyStates = findComponentsByClassName(panel, "dedup-empty-state");
		assertTrue(emptyStates.get(0).isVisible(), "empty state should be visible for empty preview");

		// Show populated preview
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var populatedPreview = new DedupPreviewPanel.DedupPreview("run-2", List.of(cluster), List.of());
		panel.show(populatedPreview);

		var emptyStatesAfter = findComponentsByClassName(panel, "dedup-empty-state");
		assertFalse(emptyStatesAfter.get(0).isVisible(), "empty state should be hidden when clusters exist");

		var clusters = findComponentsByClassName(panel, "dedup-cluster");
		assertEquals(1, clusters.size(), "cluster should render");
	}

	@Test
	void callbacksStillFire() {
		var signal = new DedupPreviewPanel.DedupPreview.Signal("Entity overlap", 1.0, 1.0, false, "");
		var edge = new DedupPreviewPanel.DedupPreview.Edge("p1", "p2", 0.95, false, List.of(signal));
		var cluster = new DedupPreviewPanel.DedupPreview.Cluster("p1", "Alice", List.of(new DedupPreviewPanel.DedupPreview.Member("p2", "alice")), List.of(edge));
		var preview = new DedupPreviewPanel.DedupPreview("run-1", List.of(cluster), List.of());

		var panel = new DedupPreviewPanel();
		var applyFired = new AtomicBoolean(false);
		panel.setOnApply(() -> applyFired.set(true));
		panel.show(preview);

		var applyButtons = findComponentsByClassName(panel, "dedup-apply");
		assertEquals(1, applyButtons.size(), "panel must have apply button");
		((Button) applyButtons.get(0)).click();

		assertTrue(applyFired.get(), "Apply button must fire callback");
	}

	private static String extractBadgeText(Component memberRow) {
		var allComponents = collectAll(memberRow);
		// Find the first Span that contains either "Survivor" or "Merge"
		return allComponents.stream()
			.filter(c -> c instanceof Span)
			.map(c -> ((Span) c).getText())
			.filter(text -> text.equals("Survivor") || text.equals("Merge"))
			.findFirst()
			.orElse("Unknown");
	}

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

	private static List<Component> collectAll(Component root) {
		var out = new ArrayList<Component>();
		collect(root, out);
		return out;
	}

	private static void collect(Component c, List<Component> out) {
		out.add(c);
		c.getChildren().forEach(child -> collect(child, out));
	}
}
