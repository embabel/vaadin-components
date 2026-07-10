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
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.vaadin.component.MemoryClusters.AddEdgeRequest;
import com.embabel.vaadin.component.MemoryClusters.ClusterKind;
import com.embabel.vaadin.component.MemoryClusters.ClusterMemberView;
import com.embabel.vaadin.component.MemoryClusters.ClusteredMemories;
import com.embabel.vaadin.component.MemoryClusters.EdgeKind;
import com.embabel.vaadin.component.MemoryClusters.EdgeProvenance;
import com.embabel.vaadin.component.MemoryClusters.MemoryClusterView;
import com.embabel.vaadin.component.MemoryClusters.RemoveEdgeRequest;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the provider-driven Clusters mode from the approved design (memories-clusters.html):
 * edge rail with Auto/Manual provenance, per-member unlink, cluster footer actions, the
 * unclustered "Link…" popover flow, the legend, and search dimming.
 */
class ClustersViewTest {

    private static final String CTX = "ctx-1";
    private static final Pattern BARE_PERCENT = Pattern.compile("^\\d{1,3}%$");

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private Proposition prop(String id, String text) {
        return Proposition.create(
                id, CTX, text, List.of(), 0.9, 0.0, 0.5, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE);
    }

    private PropositionsPanel newPanel() {
        var repo = mock(PropositionRepository.class);
        var panel = new PropositionsPanel(repo, entityResolver);
        panel.setContextId(CTX);
        return panel;
    }

    /** Puts the panel into clustered mode by clicking the cluster toggle. */
    private void toggleClusters(PropositionsPanel panel) {
        var toggle = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("cluster-toggle"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(toggle);
    }

    private static void click(Button button) {
        ComponentUtil.fireEvent(button, new ClickEvent<>(button));
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

    private static String allText(Component root) {
        var sb = new StringBuilder();
        for (var c : allComponents(root)) {
            if (c instanceof Span span) {
                sb.append(span.getText()).append(' ');
            } else if (c instanceof Button button) {
                sb.append(button.getText()).append(' ');
            }
        }
        return sb.toString();
    }

    private ClusteredMemories twoClusterSnapshot() {
        var m1 = prop("m1", "Ben likes mountain hiking");
        var m2 = prop("m2", "Ben enjoys mountain hiking on weekdays");
        var m3 = prop("m3", "Ben really enjoys mountain hiking on weekends");
        var m4 = prop("m4", "Ben likes walking through the Alps");
        var m5 = prop("m5", "Ben is planning a Dolomites trip");
        var u1 = prop("u1", "Ben's favorite mountain range is the Rockies");
        var u2 = prop("u2", "Ben mentors junior hikers");

        var cluster1 = new MemoryClusterView("c1", "Mountain hiking preferences", ClusterKind.AUTO, List.of(
                new ClusterMemberView(m1, EdgeProvenance.AUTO, "similar"),
                new ClusterMemberView(m2, EdgeProvenance.AUTO, "similar"),
                new ClusterMemberView(m3, EdgeProvenance.MANUAL, null)));
        var cluster2 = new MemoryClusterView("c2", "Alps trips", ClusterKind.MANUAL, List.of(
                new ClusterMemberView(m4, EdgeProvenance.MANUAL, "related"),
                new ClusterMemberView(m5, EdgeProvenance.AUTO, null)));

        return new ClusteredMemories(List.of(cluster1, cluster2), List.of(u1, u2));
    }

    // ---- cluster rendering ----

    @Test
    void providerDrivenClusterRendersHeaderChipTitleAndMemberCount() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        var text = allText(panel);
        assertTrue(text.contains("Auto"), "expected the Auto kind chip: " + text);
        assertTrue(text.contains("Manual"), "expected the Manual kind chip: " + text);
        assertTrue(text.contains("Mountain hiking preferences"));
        assertTrue(text.contains("Alps trips"));

        var containers = allComponents(panel).stream().filter(c -> c.hasClassName("cluster-container")).toList();
        assertEquals(2, containers.size());
    }

    @Test
    void autoVsManualConnectorClassesOnMembers() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        var members = allComponents(panel).stream().filter(c -> c.hasClassName("member")).toList();
        // cluster1 has 3 members: 2 auto, 1 manual; cluster2 has 2: 1 manual, 1 auto -> 3 manual total across both.
        var manualCount = members.stream().filter(c -> c.hasClassName("manual-edge")).count();
        assertEquals(2, manualCount);
        assertEquals(5, members.size());
    }

    @Test
    void edgeTagsRenderOnConnectors() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        var tags = allComponents(panel).stream()
                .filter(c -> c.hasClassName("edge-tag"))
                .map(c -> ((Span) c).getText())
                .toList();
        assertTrue(tags.contains("similar"));
        assertTrue(tags.contains("related"));
    }

    @Test
    void unlinkButtonFiresRemoveEdgeRequest() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        RemoveEdgeRequest[] captured = new RemoveEdgeRequest[1];
        panel.setOnRemoveEdge(req -> captured[0] = req);
        toggleClusters(panel);

        var unlink = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("unlink"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        assertEquals("Remove from cluster", unlink.getElement().getAttribute("aria-label"));
        click(unlink);

        assertEquals("m1", captured[0].memberPropositionId());
        assertEquals("c1", captured[0].clusterId());
    }

    @Test
    void footerLinksFireSweepAndMergeCallbacks() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        String[] sweptId = new String[1];
        String[] mergedId = new String[1];
        panel.setOnSweepCluster(id -> sweptId[0] = id);
        panel.setOnMergeCluster(id -> mergedId[0] = id);
        toggleClusters(panel);

        var sweep = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("cluster-sweep-link"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(sweep);
        assertEquals("c1", sweptId[0]);

        var merge = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("cluster-merge-link"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(merge);
        assertEquals("c1", mergedId[0]);
    }

    @Test
    void dissolveShowsConfirmThenFires() {
        var ui = new UI();
        var session = Mockito.mock(VaadinSession.class);
        Mockito.when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        try {
            var panel = newPanel();
            panel.setClustersProvider(this::twoClusterSnapshot);
            String[] dissolvedId = new String[1];
            panel.setOnDissolveCluster(id -> dissolvedId[0] = id);
            toggleClusters(panel);

            var dissolve = allComponents(panel).stream()
                    .filter(c -> c instanceof Button && c.hasClassName("cluster-dissolve-link"))
                    .map(c -> (Button) c)
                    .findFirst()
                    .orElseThrow();
            click(dissolve);
            // Clicking only opens a ConfirmDialog — the callback must not fire yet.
            assertNull(dissolvedId[0]);
        } finally {
            UI.setCurrent(null);
        }
    }

    // ---- unclustered / link popover ----

    @Test
    void unclusteredLinkOpensPopoverSelectingClusterAndRelationFiresAddEdge() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        AddEdgeRequest[] captured = new AddEdgeRequest[1];
        panel.setOnAddEdge(req -> captured[0] = req);
        toggleClusters(panel);

        var text = allText(panel);
        assertTrue(text.contains("Unclustered"));

        var linkButtons = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("link-btn"))
                .map(c -> (Button) c)
                .toList();
        assertEquals(2, linkButtons.size());
        click(linkButtons.get(0));

        assertTrue(allComponents(panel).stream().anyMatch(c -> c.hasClassName("popover")),
                "popover should be open after Link…");

        var clusterTarget = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("target-cluster"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(clusterTarget);

        var relatedTo = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("rel") && "Related to".equals(c.getElement().getText()))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(relatedTo);

        var addEdge = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("primary"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(addEdge);

        assertEquals("u1", captured[0].fromPropositionId());
        assertEquals("c1", captured[0].targetClusterId());
        assertNull(captured[0].targetPropositionId());
        assertEquals(EdgeKind.RELATED_TO, captured[0].kind());

        assertFalse(allComponents(panel).stream().anyMatch(c -> c.hasClassName("popover")),
                "popover should close after Add edge");
    }

    @Test
    void popoverClosesOnCancel() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        var linkButton = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("link-btn"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(linkButton);
        assertTrue(allComponents(panel).stream().anyMatch(c -> c.hasClassName("popover")));

        var cancel = allComponents(panel).stream()
                .filter(c -> c instanceof Button && c.hasClassName("btn") && !c.hasClassName("primary"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow();
        click(cancel);

        assertFalse(allComponents(panel).stream().anyMatch(c -> c.hasClassName("popover")));
    }

    // ---- legend, no similarity numbers ----

    @Test
    void legendIsPresent() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        assertTrue(allComponents(panel).stream().anyMatch(c -> c.hasClassName("legend")));
        var text = allText(panel);
        assertTrue(text.contains("Auto edge"));
        assertTrue(text.contains("Manual edge"));
    }

    @Test
    void noSimilarityNumbersInClusteredMode() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        assertFalse(allComponents(panel).stream().anyMatch(c -> c.hasClassName("similarity-badge")));
        assertFalse(allComponents(panel).stream().anyMatch(c -> c.hasClassName("similarity-bar")));
        for (var c : allComponents(panel)) {
            if (c instanceof Span span) {
                var t = span.getText();
                if (t != null && BARE_PERCENT.matcher(t.trim()).matches()) {
                    throw new AssertionError("bare percentage text found in clustered mode: " + t);
                }
            }
        }
    }

    // ---- dimming on filter ----

    @Test
    void filterDimsNonMatchingMembersInsteadOfHidingThem() {
        var panel = newPanel();
        panel.setClustersProvider(this::twoClusterSnapshot);
        toggleClusters(panel);

        panel.setSearchQuery("Dolomites");

        var members = allComponents(panel).stream().filter(c -> c.hasClassName("member")).toList();
        assertFalse(members.isEmpty());
        // Every member stays visible (dimmed via CSS class), never hidden.
        assertTrue(members.stream().allMatch(Component::isVisible));
        var dimmed = members.stream().filter(c -> c.hasClassName("dim")).count();
        assertTrue(dimmed > 0, "expected at least one non-matching member to be dimmed");
    }
}
