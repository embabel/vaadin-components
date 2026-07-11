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
import com.embabel.agent.rag.service.Cluster;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the Memory tab (rendered by {@link PropositionsPanel}) filters at the DB layer and lets
 * the user choose which statuses to view — the two things flagged on #294.
 *
 * <p><b>DB-level, not in app memory.</b> The fake repository's {@code query} and {@code findClusters}
 * <i>honour the status set on the {@link PropositionQuery} they are handed</i>: they only return rows
 * whose status the query asked for. So if a STALE row is excluded, it is excluded because the panel
 * never asked for it — there is no {@code stream().filter(status)} in the panel to make up for a
 * repository that returns everything. We then capture the query with an {@link ArgumentCaptor} and
 * assert the status set rode along in it.
 *
 * <p><b>Status-filter affordance.</b> The default view is Active (so #294 holds: STALE collapsed
 * duplicates stay hidden). The tests drive the real header {@link Select} control to switch to All
 * (audit everything) and Stale (review exactly what was collapsed), proving the UI is wired.
 */
class PropositionsPanelActiveFilterTest {

    private static final String CTX = "ctx-1";
    private static final String SUBJECT_TEXT = "Jim lives in Brisbane";

    private final Function<String, NamedEntity> entityResolver = id -> null;

    private final Proposition active = prop("active-1", PropositionStatus.ACTIVE, Instant.now());
    private final Proposition stale = prop("stale-1", PropositionStatus.STALE, Instant.now().minusSeconds(60));
    private final List<Proposition> pool = List.of(active, stale);

    private Proposition prop(String id, PropositionStatus status, Instant created) {
        return Proposition.create(
                id,
                CTX,
                SUBJECT_TEXT,
                List.of(),          // no mentions — keep the card simple
                0.9,                // confidence
                0.0,                // decay
                0.5,                // importance
                null,               // reasoning
                List.of(),          // grounding
                created,            // created
                created,            // revised
                status
        );
    }

    /**
     * Build a repository whose query surface HONOURS the query's status set. An empty/absent set
     * means "any status" (the All view); otherwise only the requested statuses come back. This is
     * what makes the assertions meaningful: the panel must put the right statuses on the query or
     * the wrong rows would be returned.
     */
    private PropositionRepository honouringRepo() {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any(PropositionQuery.class)))
                .thenAnswer(inv -> matching(inv.getArgument(0)));
        when(repo.findClusters(anyDouble(), anyInt(), any(PropositionQuery.class)))
                .thenAnswer(inv -> matching(inv.getArgument(2)).stream()
                        .map(p -> new Cluster<>(p, List.<SimilarityResult<Proposition>>of()))
                        .toList());
        return repo;
    }

    private List<Proposition> matching(PropositionQuery q) {
        Set<PropositionStatus> statuses = q.getStatuses();
        return pool.stream()
                .filter(p -> statuses == null || statuses.isEmpty() || statuses.contains(p.getStatus()))
                .toList();
    }

    // --- FLAT MODE ------------------------------------------------------------------------------

    @Test
    void flatModeDefaultActiveExcludesStaleViaDbQuery() {
        var repo = honouringRepo();
        var panel = new PropositionsPanel(repo, entityResolver);
        panel.setContextId(CTX);

        panel.refresh(); // flat mode, default Active view

        var cardIds = renderedCardIds(panel);
        assertEquals(List.of("active-1"), cardIds, "flat list must render only the ACTIVE card");
        assertFalse(cardIds.contains("stale-1"), "STALE card must be absent under the default Active view");
        assertEquals("(1 memories)", countBadgeText(panel), "count badge must reflect the ACTIVE-only size");

        // Prove the filtering rode in on the query handed to the DB, not in app memory.
        var captor = ArgumentCaptor.forClass(PropositionQuery.class);
        verify(repo).query(captor.capture());
        var statuses = captor.getValue().getStatuses();
        assertEquals(Set.of(PropositionStatus.ACTIVE), statuses,
                "flat query must carry withStatuses({ACTIVE}) — filtering is in the query, not the panel");
    }

    @Test
    void flatModeAllSurfacesStaleAndStaleViewShowsOnlyStale() {
        var repo = honouringRepo();
        var panel = new PropositionsPanel(repo, entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        // Drive the REAL header Select to "All".
        selectView(panel, "All");
        var all = renderedCardIds(panel);
        assertTrue(all.contains("active-1"), "All view must surface the ACTIVE memory");
        assertTrue(all.contains("stale-1"), "All view must surface the STALE memory too");
        assertEquals("(2 memories)", countBadgeText(panel), "All view count must include STALE");

        // Drive the REAL header Select to "Stale".
        selectView(panel, "Stale");
        var staleOnly = renderedCardIds(panel);
        assertEquals(List.of("stale-1"), staleOnly, "Stale view must show only the STALE memory");
    }

    // --- CLUSTERED MODE -------------------------------------------------------------------------

    @Test
    void clusteredModeDefaultActiveExcludesStaleAndScopesClusterQuery() {
        var repo = honouringRepo();
        var panel = new PropositionsPanel(repo, entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        // Drive the real cluster toggle (refresh() -> refreshClustered()).
        var toggle = findToggle(panel);
        ComponentUtil.fireEvent(toggle, new ClickEvent<>(toggle));

        var cardIds = renderedCardIds(panel);
        assertTrue(cardIds.contains("active-1"), "clustered view must render the ACTIVE memory");
        assertFalse(cardIds.contains("stale-1"), "clustered view must exclude the STALE memory");
        assertEquals("(1 memories, 1 clusters)", countBadgeText(panel),
                "clustered count badge must reflect ACTIVE-only totals (STALE excluded)");

        // Prove the status scope flowed into the cluster query (DB-level, not in app memory).
        var captor = ArgumentCaptor.forClass(PropositionQuery.class);
        verify(repo).findClusters(anyDouble(), anyInt(), captor.capture());
        var statuses = captor.getValue().getStatuses();
        assertEquals(Set.of(PropositionStatus.ACTIVE), statuses,
                "findClusters query must carry withStatuses({ACTIVE})");
    }

    @Test
    void clusteredModeAllSurfacesStale() {
        var repo = honouringRepo();
        var panel = new PropositionsPanel(repo, entityResolver);
        panel.setContextId(CTX);
        panel.refresh();

        var toggle = findToggle(panel);
        ComponentUtil.fireEvent(toggle, new ClickEvent<>(toggle)); // -> clustered

        // Switch the real Select to All; clustered view re-runs and STALE now participates.
        selectView(panel, "All");
        var cardIds = renderedCardIds(panel);
        assertTrue(cardIds.contains("active-1"), "All clustered view must include the ACTIVE memory");
        assertTrue(cardIds.contains("stale-1"), "All clustered view must include the STALE memory");

        // The cluster query for the All view must NOT restrict to ACTIVE (empty set = any status).
        var captor = ArgumentCaptor.forClass(PropositionQuery.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).findClusters(anyDouble(), anyInt(), captor.capture());
        var lastStatuses = captor.getValue().getStatuses();
        assertTrue(lastStatuses == null || lastStatuses.isEmpty(),
                "All view cluster query must not pin a status (empty/absent set = any status)");
    }

    // --- driving the real Select control --------------------------------------------------------

    private static void selectView(Component root, String label) {
        Select<Object> select = findStatusSelect(root);
        var item = select.getListDataView().getItems()
                .filter(i -> label.equals(select.getItemLabelGenerator().apply(i)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no status-filter option labelled '" + label + "'"));
        select.setValue(item); // fires the value-change listener -> panel.refresh()
    }

    @SuppressWarnings("unchecked")
    private static Select<Object> findStatusSelect(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Select && ((Select<?>) c).getClassNames().contains("status-filter"))
                .map(c -> (Select<Object>) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("status-filter Select not found"));
    }

    // --- component-tree helpers (no Karibu needed — plain Vaadin component walk) -----------------

    private static List<String> renderedCardIds(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> ((PropositionCard) c).getProposition().getId())
                .toList();
    }

    private static String countBadgeText(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Span && ((Span) c).getClassNames().contains("panel-count"))
                .map(c -> ((Span) c).getText())
                .findFirst()
                .orElseThrow(() -> new AssertionError("count badge span not found"));
    }

    private static Button findToggle(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Button && ((Button) c).getClassNames().contains("cluster-toggle"))
                .map(c -> (Button) c)
                .findFirst()
                .orElseThrow(() -> new AssertionError("cluster toggle button not found"));
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
