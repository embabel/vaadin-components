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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Panel showing the knowledge base of extracted propositions.
 */
public class PropositionsPanel extends VerticalLayout {

    /**
     * The memory views offered in the Memory-tab header. ALL carries an empty status set because
     * dice treats that as "no status filter" — the way to surface STALE for audit.
     */
    private enum MemoryView {
        ACTIVE("Active", Set.of(PropositionStatus.ACTIVE)),
        ALL("All", Set.of()),
        STALE("Stale", Set.of(PropositionStatus.STALE));

        private final String label;
        private final Set<PropositionStatus> statuses;

        MemoryView(String label, Set<PropositionStatus> statuses) {
            this.label = label;
            this.statuses = statuses;
        }
    }

    private final PropositionRepository propositionRepository;
    private final Function<String, NamedEntity> entityResolver;
    private final CollapseExplanationProvider collapseExplanationProvider;
    private final VerticalLayout propositionsContent;
    private final Span propositionCountSpan;
    private final Select<MemoryView> statusSelect;
    private final Button clusterToggle;
    private Consumer<String> onDelete;
    private Consumer<Proposition> onEdit;
    private LineageProvider lineageProvider;
    private Function<String, List<Proposition>> relatedPropositionsLoader;
    private Function<String, EntityPanel.RelatedRecords> relatedRecordsLoader;
    private BiConsumer<String, String> onUndoMember;
    private String contextId;
    private boolean clustered = false;
    private Set<PropositionStatus> statusFilter = MemoryView.ACTIVE.statuses;
    private Supplier<List<SimilarityResult<Proposition>>> scoredResultsSupplier;
    private boolean scoredMode = false;

    /**
     * Convenience constructor for callers that don't explain collapses.
     */
    public PropositionsPanel(PropositionRepository propositionRepository,
                             Function<String, NamedEntity> entityResolver) {
        this(propositionRepository, entityResolver, null);
    }

    /**
     * Panel showing the knowledge base of propositions in a context. Offers flat or clustered view,
     * status filter (active/all/stale), and inline edit/delete. Optionally explains why propositions
     * were collapsed into one another.
     *
     * @param propositionRepository stores and queries propositions
     * @param entityResolver resolves entity mention IDs to NamedEntity; null to show mentions unresolved
     * @param collapseExplanationProvider looks up why propositions were collapsed, if at all; null skips collapse badges
     */
    public PropositionsPanel(PropositionRepository propositionRepository,
                             Function<String, NamedEntity> entityResolver,
                             CollapseExplanationProvider collapseExplanationProvider) {
        this.propositionRepository = propositionRepository;
        this.entityResolver = entityResolver;
        this.collapseExplanationProvider = collapseExplanationProvider;

        setPadding(false);
        setSpacing(true);
        setSizeFull();

        // Add component-scoped CSS styling for scored mode cards with relevance bars and dedup badges
        injectScoredModeStyles();

        var headerLayout = new HorizontalLayout();
        headerLayout.setAlignItems(Alignment.CENTER);
        headerLayout.setSpacing(true);
        headerLayout.setWidthFull();

        var titleSpan = new Span("Memories");
        titleSpan.addClassName("panel-title");

        propositionCountSpan = new Span("(0 memories)");
        propositionCountSpan.addClassName("panel-count");

        statusSelect = new Select<>();
        statusSelect.setItems(MemoryView.values());
        statusSelect.setItemLabelGenerator(view -> view.label);
        statusSelect.setValue(MemoryView.ACTIVE);
        statusSelect.addThemeVariants(SelectVariant.LUMO_SMALL);
        statusSelect.addClassName("status-filter");
        statusSelect.getElement().setAttribute("title", "Choose which memories to show");
        statusSelect.addValueChangeListener(e -> {
            statusFilter = e.getValue().statuses;
            refresh();
        });

        clusterToggle = new Button("Clusters", VaadinIcon.CLUSTER.create());
        clusterToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        clusterToggle.addClassName("cluster-toggle");
        clusterToggle.getElement().setAttribute("title", "Toggle cluster view");
        clusterToggle.addClickListener(e -> {
            clustered = !clustered;
            clusterToggle.setText(clustered ? "List" : "Clusters");
            clusterToggle.setIcon(clustered ? VaadinIcon.LIST.create() : VaadinIcon.CLUSTER.create());
            refresh();
        });

        var refreshButton = new Button(VaadinIcon.REFRESH.create());
        refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        refreshButton.getElement().setAttribute("title", "Refresh memories");
        refreshButton.addClickListener(e -> refresh());

        headerLayout.add(titleSpan, propositionCountSpan, statusSelect, clusterToggle, refreshButton);
        headerLayout.setFlexGrow(1, titleSpan);

        propositionsContent = new VerticalLayout();
        propositionsContent.setPadding(false);
        propositionsContent.setSpacing(true);
        propositionsContent.setWidthFull();

        var contentScroller = new Scroller(propositionsContent);
        contentScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        contentScroller.setSizeFull();
        contentScroller.addClassName("panel-scroller");

        add(headerLayout, contentScroller);
        setFlexGrow(1, contentScroller);
    }

    public void refresh() {
        if (scoredMode) {
            if (scoredResultsSupplier != null) {
                showScoredPropositions(scoredResultsSupplier.get());
            }
            return;
        }
        propositionsContent.removeAll();
        if (contextId == null) {
            // No context means nothing to show. Deliberately not a findAll() across every
            // context — that would leak other users' memories into this per-user panel.
            propositionCountSpan.setText("(0 memories)");
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }
        if (clustered) {
            refreshClustered();
        } else {
            refreshFlat();
        }
    }

    /**
     * Display search results with relevance scores. Used by the memory drawer
     * to show propositions relevant to the current conversation.
     */
    public void showScoredPropositions(List<SimilarityResult<Proposition>> results) {
        scoredMode = true;
        propositionsContent.removeAll();
        propositionCountSpan.setText("(" + results.size() + " relevant)");
        // Scored results are driven by the caller, not by a context query, so the status
        // filter and cluster toggle don't apply. Hide both — changing the filter would
        // trigger a context-scoped refresh and wipe these results.
        statusSelect.setVisible(false);
        clusterToggle.setVisible(false);

        if (results.isEmpty()) {
            var emptyMessage = new Span("No relevant memories for this conversation.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        // Dedup: normalize text, group by normalized form, keep highest-scored result per group.
        var dedupMap = new java.util.LinkedHashMap<String, DedupGroup>();
        for (var result : results) {
            var normalizedText = normalizeText(result.getMatch().getText());
            dedupMap.computeIfAbsent(normalizedText, k -> new DedupGroup())
                    .considerResult(result);
        }

        for (var group : dedupMap.values()) {
            var result = group.getSurvivor();
            var cardContainer = new Div();
            cardContainer.addClassName("scored-card-wrapper");

            var card = createCard(result.getMatch());
            card.addClassName("scored-card-content");
            cardContainer.add(card);

            // Add relevance bar + score in meta-row
            double score = result.getScore();
            var metaRow = new Div();
            metaRow.addClassName("scored-meta-row");

            var relBar = new Div();
            relBar.addClassName("relevance-bar");
            var barFill = new Span();
            int scorePct = (int) Math.round(score * 100);
            barFill.getElement().getStyle().set("width", scorePct + "%");
            relBar.add(barFill);

            var scoreDisplay = new Span(String.format("%.2f", score));
            scoreDisplay.addClassName("relevance-score");

            metaRow.add(relBar, scoreDisplay);
            cardContainer.add(metaRow);

            // Add dedup badge if this survivor collapsed other results (positioned absolutely via CSS)
            int collapsedCount = group.getCollapsedCount();
            if (collapsedCount > 0) {
                var dedupBadge = new Span("+" + collapsedCount + " similar");
                dedupBadge.addClassName("dedup-collapsed-badge");
                cardContainer.add(dedupBadge);
            }

            // Add hidden similarity-badge to preserve any backward compatibility
            var scoreBadge = new Span(scorePct + "%");
            scoreBadge.addClassName("similarity-badge");
            cardContainer.add(scoreBadge);

            propositionsContent.add(cardContainer);
        }
    }

    /** Inject CSS styling for scored mode cards with relevance bars and dedup badges. */
    private void injectScoredModeStyles() {
        String css = """
            .scored-card-wrapper {
              position: relative;
              border: 1px solid var(--lumo-contrast-20pct);
              border-radius: var(--lumo-border-radius-m);
              background: var(--lumo-base-color);
              padding: 10px 11px;
              margin-bottom: 8px;
            }

            .scored-card-wrapper .proposition-card {
              background: transparent;
              border: none;
              padding: 0;
              margin-bottom: 0;
            }

            .scored-card-wrapper .proposition-card:hover {
              border-color: transparent;
            }

            .scored-card-content {
              flex: 1;
            }

            .scored-meta-row {
              display: flex;
              align-items: center;
              gap: 8px;
              margin-top: 7px;
            }

            .relevance-bar {
              flex: 1;
              height: 5px;
              border-radius: 3px;
              background: var(--lumo-contrast-10pct);
              overflow: hidden;
            }

            .relevance-bar > span {
              display: block;
              height: 100%;
              background: var(--lumo-primary-color);
              border-radius: 3px;
            }

            .relevance-score {
              font-size: 10.5px;
              color: var(--lumo-secondary-text-color);
              font-variant-numeric: tabular-nums;
              width: 30px;
              text-align: right;
            }

            .dedup-collapsed-badge {
              position: absolute;
              top: 8px;
              right: 9px;
              font-size: 9.5px;
              font-weight: 700;
              padding: 1px 6px;
              border-radius: 999px;
              background: var(--lumo-warning-color);
              color: white;
              opacity: 0.9;
            }

            .similarity-badge {
              display: none;
            }
            """;

        Element styleElement = new Element("style");
        styleElement.setText(css);
        getElement().appendVirtualChild(styleElement);
    }

    /** Normalize proposition text for dedup comparison: trim, lowercase, collapse whitespace, strip trailing period. */
    private static String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("\\.$", "");
    }

    /** Tracks the best result and collapsed count for a normalized text group. */
    private static class DedupGroup {
        private SimilarityResult<Proposition> survivor;
        private int collapsedCount = 0;

        void considerResult(SimilarityResult<Proposition> result) {
            if (survivor == null || result.getScore() > survivor.getScore()) {
                if (survivor != null) {
                    collapsedCount++;
                }
                survivor = result;
            } else {
                collapsedCount++;
            }
        }

        SimilarityResult<Proposition> getSurvivor() {
            return survivor;
        }

        int getCollapsedCount() {
            return collapsedCount;
        }
    }

    /** Context-scoped query carrying the current status filter; the store applies it (Cypher on Neo4j). */
    private PropositionQuery memoryQuery() {
        return PropositionQuery.againstContext(contextId).withStatuses(statusFilter);
    }

    private void refreshFlat() {
        var propositions = propositionRepository.query(memoryQuery());
        propositionCountSpan.setText("(" + propositions.size() + " memories)");

        if (propositions.isEmpty()) {
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        propositions.stream()
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .forEach(prop -> propositionsContent.add(createCard(prop)));
    }

    private void refreshClustered() {
        // One query scopes both the clustering and the unclustered list.
        var query = memoryQuery();
        List<Cluster<Proposition>> clusters = propositionRepository.findClusters(0.7, 10, query);

        // Collect all propositions that appear in a cluster
        var clusteredIds = new HashSet<String>();
        for (var cluster : clusters) {
            clusteredIds.add(cluster.getAnchor().getId());
            for (var sim : cluster.getSimilar()) {
                clusteredIds.add(sim.getMatch().getId());
            }
        }

        // The in-scope propositions, to find the ones no cluster claimed.
        var allPropositions = propositionRepository.query(query);

        int totalCount = allPropositions.size();
        propositionCountSpan.setText("(" + totalCount + " memories, " + clusters.size() + " clusters)");

        if (allPropositions.isEmpty()) {
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        // Render each cluster as a collapsible Details component
        for (int i = 0; i < clusters.size(); i++) {
            var cluster = clusters.get(i);
            var anchor = cluster.getAnchor();
            List<SimilarityResult<Proposition>> similar = cluster.getSimilar();
            int clusterSize = similar.size() + 1;

            // Summary: cluster icon + anchor text + count badge
            var summaryLayout = new HorizontalLayout();
            summaryLayout.setAlignItems(Alignment.CENTER);
            summaryLayout.setSpacing(true);
            summaryLayout.addClassName("cluster-summary");

            var clusterIcon = new Span();
            clusterIcon.addClassName("cluster-icon");

            var anchorText = new Span(truncate(anchor.getText(), 70));
            anchorText.addClassName("cluster-anchor-text");

            var countBadge = new Span(clusterSize + " memories");
            countBadge.addClassName("cluster-count");

            summaryLayout.add(clusterIcon, anchorText, countBadge);

            // Content: anchor card (labeled) + similar cards with scored badges
            var content = new VerticalLayout();
            content.setPadding(false);
            content.setSpacing(false);
            content.addClassName("cluster-content");

            // Anchor card with label
            var anchorWrapper = new VerticalLayout();
            anchorWrapper.setPadding(false);
            anchorWrapper.setSpacing(false);
            anchorWrapper.addClassName("cluster-anchor-wrapper");
            var anchorLabel = new Span("Anchor");
            anchorLabel.addClassName("cluster-item-label");
            anchorLabel.addClassName("anchor-label");
            anchorWrapper.add(anchorLabel, createCard(anchor));
            content.add(anchorWrapper);

            // Similar cards with similarity score to the right
            for (var sim : similar) {
                var simWrapper = new HorizontalLayout();
                simWrapper.setWidthFull();
                simWrapper.setAlignItems(Alignment.CENTER);
                simWrapper.setPadding(false);
                simWrapper.setSpacing(false);
                simWrapper.addClassName("cluster-similar-wrapper");

                var card = createCard(sim.getMatch());

                var scorePct = (int) Math.round(sim.getScore() * 100);
                var scoreIndicator = new VerticalLayout();
                scoreIndicator.setPadding(false);
                scoreIndicator.setSpacing(false);
                scoreIndicator.setAlignItems(Alignment.CENTER);
                scoreIndicator.addClassName("similarity-indicator");

                var scoreBadge = new Span(scorePct + "%");
                scoreBadge.addClassName("similarity-badge");
                if (scorePct >= 90) {
                    scoreBadge.addClassName("score-high");
                } else if (scorePct >= 80) {
                    scoreBadge.addClassName("score-medium");
                } else {
                    scoreBadge.addClassName("score-low");
                }

                var scoreBar = new Span();
                scoreBar.addClassName("similarity-bar");
                scoreBar.getElement().getStyle().set("--score-width", scorePct + "%");

                scoreIndicator.add(scoreBadge, scoreBar);
                simWrapper.add(card, scoreIndicator);
                simWrapper.setFlexGrow(1, card);
                content.add(simWrapper);
            }

            var details = new Details(summaryLayout, content);
            details.addClassName("cluster-details");
            details.addClassName("cluster-index-" + (i % 4));
            propositionsContent.add(details);
        }

        // Unclustered propositions
        var unclustered = allPropositions.stream()
                .filter(p -> !clusteredIds.contains(p.getId()))
                .sorted(Comparator.comparing(Proposition::getCreated).reversed())
                .toList();

        if (!unclustered.isEmpty()) {
            var sectionLayout = new VerticalLayout();
            sectionLayout.setPadding(false);
            sectionLayout.setSpacing(true);
            sectionLayout.addClassName("unclustered-section");

            var sectionHeader = new HorizontalLayout();
            sectionHeader.setAlignItems(Alignment.CENTER);
            sectionHeader.setSpacing(true);
            sectionHeader.addClassName("unclustered-header");
            var sectionLabel = new Span("Unclustered");
            sectionLabel.addClassName("unclustered-label");
            var sectionCount = new Span(unclustered.size() + "");
            sectionCount.addClassName("unclustered-count");
            sectionHeader.add(sectionLabel, sectionCount);

            sectionLayout.add(sectionHeader);
            for (var prop : unclustered) {
                sectionLayout.add(createCard(prop));
            }
            propositionsContent.add(sectionLayout);
        }
    }

    private PropositionCard createCard(Proposition prop) {
        var card = new PropositionCard(prop, entityResolver, collapseExplanationProvider);
        card.setLineageProvider(lineageProvider);
        card.setRelatedPropositionsLoader(relatedPropositionsLoader);
        card.setRelatedRecordsLoader(relatedRecordsLoader);
        if (onUndoMember != null) {
            card.setOnUndoMember(onUndoMember);
        }
        if (onDelete != null) {
            card.setOnDelete(p -> {
                onDelete.accept(p.getId());
                refresh();
            });
        }
        if (onEdit != null) {
            card.setOnEdit(onEdit);
        }
        return card;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    public void setOnDelete(Consumer<String> handler) {
        this.onDelete = handler;
    }

    public void setOnEdit(Consumer<Proposition> handler) {
        this.onEdit = handler;
    }

    /**
     * Give cards a way to trace where a memory came from. When set, each proposition card
     * offers a "Lineage" affordance that opens the grounding/provenance/collapse trail.
     *
     * @param lineageProvider looks up lineage for a proposition id, or null to hide the affordance
     */
    public void setLineageProvider(LineageProvider lineageProvider) {
        this.lineageProvider = lineageProvider;
    }

    /**
     * Give entity dialogs a way to show memories mentioning the entity. When set,
     * entity panels display a collapsed "Mentioned in N memories" section with those propositions.
     *
     * @param relatedPropositionsLoader looks up propositions mentioning an entity id,
     *                                   or null to omit the related-memories section
     */
    public void setRelatedPropositionsLoader(Function<String, List<Proposition>> relatedPropositionsLoader) {
        this.relatedPropositionsLoader = relatedPropositionsLoader;
    }

    /**
     * Give entity dialogs additional related-records sections (contact facts, people, orgs,
     * emails, meetings, edge chips). When set, entity panels load and display these sections.
     *
     * @param relatedRecordsLoader looks up RelatedRecords by entity id,
     *                             or null to omit related records
     */
    public void setRelatedRecordsLoader(Function<String, EntityPanel.RelatedRecords> relatedRecordsLoader) {
        this.relatedRecordsLoader = relatedRecordsLoader;
    }

    /**
     * Set the handler to invoke when an "Undo this merge" button is clicked in a lineage section.
     *
     * @param onUndoMember callback receiving (survivorId, retiredMemberId) when undo is clicked,
     *                     or null to disable undo functionality
     */
    public void setOnUndoMember(BiConsumer<String, String> onUndoMember) {
        this.onUndoMember = onUndoMember;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
        this.scoredMode = false;
    }

    public void setScoredResultsSupplier(Supplier<List<SimilarityResult<Proposition>>> supplier) {
        this.scoredResultsSupplier = supplier;
    }

    public void scheduleRefresh(com.vaadin.flow.component.UI ui, long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                ui.access(this::refresh);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
