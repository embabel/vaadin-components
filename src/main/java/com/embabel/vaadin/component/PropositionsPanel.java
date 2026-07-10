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
import com.embabel.vaadin.component.MemoryClusters.AddEdgeRequest;
import com.embabel.vaadin.component.MemoryClusters.ClusterKind;
import com.embabel.vaadin.component.MemoryClusters.ClusterMemberView;
import com.embabel.vaadin.component.MemoryClusters.ClusteredMemories;
import com.embabel.vaadin.component.MemoryClusters.EdgeKind;
import com.embabel.vaadin.component.MemoryClusters.EdgeProvenance;
import com.embabel.vaadin.component.MemoryClusters.EntityLinkRequest;
import com.embabel.vaadin.component.MemoryClusters.LinkTarget;
import com.embabel.vaadin.component.MemoryClusters.LinkTargetKind;
import com.embabel.vaadin.component.MemoryClusters.MemoryClusterView;
import com.embabel.vaadin.component.MemoryClusters.RemoveEdgeRequest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
    private final TextField searchField;
    private final HorizontalLayout searchResultsBar;
    private final Span searchResultsBarLabel;
    private Consumer<String> onSearchSubmit;
    private Runnable searchResultsBarOnClear;
    private Consumer<String> onDelete;
    private Consumer<Proposition> onEdit;
    private LineageProvider lineageProvider;
    private Function<String, List<Proposition>> relatedPropositionsLoader;
    private Function<String, EntityPanel.RelatedRecords> relatedRecordsLoader;
    private BiConsumer<String, String> onUndoMember;
    private BiConsumer<String, String> onAfterUndo;
    private Consumer<String> onOpenRef;
    private Predicate<String> openable;
    private Consumer<String> onEntityPillClick;
    private String contextId;
    private boolean clustered = false;
    private Set<PropositionStatus> statusFilter = MemoryView.ACTIVE.statuses;
    private Supplier<List<SimilarityResult<Proposition>>> scoredResultsSupplier;
    private boolean scoredMode = false;
    private Supplier<ClusteredMemories> clustersProvider;
    private Consumer<AddEdgeRequest> onAddEdge;
    private Consumer<RemoveEdgeRequest> onRemoveEdge;
    private Consumer<String> onDissolveCluster;
    private Consumer<String> onSweepCluster;
    private Consumer<String> onMergeCluster;
    private Function<String, List<LinkTarget>> linkTargetSearch;
    private Consumer<EntityLinkRequest> onLinkEntity;

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

        // Add component-scoped CSS styling for scored mode cards and dedup badges
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
        refreshButton.getElement().setAttribute("aria-label", "Refresh memories");
        refreshButton.addClickListener(e -> refresh());

        // Search field: L1 instant client-side filter on every keystroke; Enter hands the raw
        // query to the host for semantic search (setOnSearchSubmit); Esc clears the field and
        // the instant filter. The "?" chip opens a popover documenting the search operators.
        searchField = new TextField();
        searchField.setPlaceholder("Search memories... filter, entity:\"...\", or ask a question");
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.addClassName("memory-search-field");
        // Dominant control of the header row: grows with the row, but stays readable at both
        // ends — never so narrow it clips typing, never so wide it swallows the other controls.
        searchField.setMinWidth("320px");
        searchField.setMaxWidth("480px");
        searchField.addValueChangeListener(e -> applyInstantFilter(e.getValue()));
        searchField.addKeyDownListener(Key.ENTER, e -> {
            if (onSearchSubmit != null) {
                onSearchSubmit.accept(searchField.getValue());
            }
        });
        searchField.addKeyDownListener(Key.ESCAPE, e -> {
            searchField.setValue("");
            applyInstantFilter("");
        });

        var infoChip = new Button("?");
        infoChip.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        infoChip.addClassName("search-info-chip");
        infoChip.getElement().setAttribute("title", "Search help: operators and tips");
        infoChip.getElement().setAttribute("aria-label", "Search help: operators and tips");

        var searchHelpPopover = new Popover();
        searchHelpPopover.setTarget(infoChip);
        searchHelpPopover.addClassName("search-help-popover");
        var helpContent = new VerticalLayout();
        helpContent.setPadding(false);
        helpContent.setSpacing(false);
        helpContent.addClassName("search-help-content");
        helpContent.add(
                searchOperatorRow("entity:<name>", "filter by entity (typeahead)"),
                searchOperatorRow("status:active|stale|all", "lifecycle filter"),
                searchOperatorRow("conf:>0.8 / conf:<0.5", "confidence band"),
                searchOperatorRow("merged:yes|no", "only merged / no merged cards"));
        var helpNote = new Span("Type to filter instantly. Press Enter for semantic search. Esc clears.");
        helpNote.addClassName("search-help-note");
        helpContent.add(helpNote);
        searchHelpPopover.add(helpContent);

        var searchWrap = new HorizontalLayout(searchField, infoChip, searchHelpPopover);
        searchWrap.setAlignItems(Alignment.CENTER);
        searchWrap.setSpacing(true);
        searchWrap.addClassName("search-wrap");
        searchWrap.setFlexGrow(1, searchField);

        headerLayout.add(titleSpan, propositionCountSpan, searchWrap, statusSelect, clusterToggle, refreshButton);
        // The search field is the dominant control of the header row now, not the title.
        headerLayout.setFlexGrow(1, searchWrap);

        // L2 semantic-results bar: slim, shown above the list when the host reports results
        // for a submitted query (setSearchResultsBar); hidden until then and on clear.
        searchResultsBarLabel = new Span();
        searchResultsBarLabel.addClassName("search-results-bar-label");
        var searchResultsBarClear = new Button("Clear");
        searchResultsBarClear.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        searchResultsBarClear.addClassName("search-results-bar-clear");
        searchResultsBar = new HorizontalLayout(searchResultsBarLabel, searchResultsBarClear);
        searchResultsBar.setAlignItems(Alignment.CENTER);
        searchResultsBar.setSpacing(true);
        searchResultsBar.addClassName("search-results-bar");
        // Full width with a growing label so the theme CSS can truncate long text instead of
        // letting it push the Clear button out of a narrow host's viewport.
        searchResultsBar.setWidthFull();
        searchResultsBar.setFlexGrow(1, searchResultsBarLabel);
        searchResultsBar.setVisible(false);
        searchResultsBarClear.addClickListener(e -> {
            searchResultsBar.setVisible(false);
            if (searchResultsBarOnClear != null) {
                searchResultsBarOnClear.run();
            }
        });

        propositionsContent = new VerticalLayout();
        propositionsContent.setPadding(false);
        propositionsContent.setSpacing(true);
        propositionsContent.setWidthFull();

        var contentScroller = new Scroller(propositionsContent);
        contentScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        contentScroller.setSizeFull();
        contentScroller.addClassName("panel-scroller");

        add(headerLayout, searchResultsBar, contentScroller);
        setFlexGrow(1, contentScroller);
    }

    private Div searchOperatorRow(String operator, String description) {
        var row = new Div();
        row.addClassName("search-op-row");
        var op = new Span(operator);
        op.addClassName("search-op-name");
        var desc = new Span(description);
        desc.addClassName("search-op-desc");
        row.add(op, desc);
        return row;
    }

    /**
     * L1 instant filter: hides cards whose memory text and entity names don't contain the
     * query (case-insensitive), and updates the visible count. Blank query shows everything.
     */
    private void applyInstantFilter(String query) {
        var q = query == null ? "" : query.trim().toLowerCase();
        var cards = allComponents(propositionsContent).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> (PropositionCard) c)
                .toList();
        int shown = 0;
        // Clustered mode groups cards under a shared .cluster-container (header + border); track
        // whether any member of each container matched so the whole container can be hidden when
        // none did, instead of leaving an empty bordered box behind (same failure mode the
        // .scored-card-wrapper handling below fixes for scored mode).
        var clusterHits = new java.util.LinkedHashMap<Component, Boolean>();
        for (var card : cards) {
            boolean hit = q.isEmpty() || matchesQuery(card.getProposition(), q);
            // In scored mode each card sits inside a .scored-card-wrapper that carries its own
            // border/background; hiding just the inner card leaves an empty bordered box behind
            // and pushes real matches out of view. Toggle the wrapper instead when present.
            var wrapper = card.getParent()
                    .filter(p -> p.hasClassName("scored-card-wrapper"))
                    .orElse(null);
            // A provider-driven rail wraps each card in a .member div; dim rather than hide so
            // the edge rail connectors stay visually coherent across the whole cluster.
            var member = card.getParent()
                    .filter(p -> p.hasClassName("member"))
                    .orElse(null);
            var clusterContainer = findAncestorWithClass(card, "cluster-container");
            if (wrapper != null) {
                wrapper.setVisible(hit);
            } else if (member != null) {
                if (member instanceof HasStyle stylable) {
                    stylable.setClassName("dim", !hit);
                }
                card.setVisible(true);
                if (clusterContainer != null) {
                    clusterHits.merge(clusterContainer, hit, Boolean::logicalOr);
                }
            } else if (clusterContainer != null) {
                card.setVisible(hit);
                clusterHits.merge(clusterContainer, hit, Boolean::logicalOr);
            } else {
                card.setVisible(hit);
            }
            if (hit) {
                shown++;
            }
        }
        clusterHits.forEach(Component::setVisible);
        propositionCountSpan.setText("(" + shown + (shown == 1 ? " memory)" : " memories)"));
    }

    private boolean matchesQuery(Proposition prop, String lowerCaseQuery) {
        if (prop.getText() != null && prop.getText().toLowerCase().contains(lowerCaseQuery)) {
            return true;
        }
        for (var mention : prop.getMentions()) {
            var name = resolvedEntityName(mention);
            if (name != null && name.toLowerCase().contains(lowerCaseQuery)) {
                return true;
            }
        }
        return false;
    }

    private String resolvedEntityName(com.embabel.dice.proposition.EntityMention mention) {
        if (mention.getResolvedId() != null && entityResolver != null) {
            var resolved = entityResolver.apply(mention.getResolvedId());
            if (resolved != null) {
                return resolved.getName();
            }
        }
        return mention.getSpan();
    }

    /**
     * Set the handler invoked with the raw search text when Enter is pressed in the search field.
     * The host runs semantic search / operator parsing and pushes results back through
     * {@link #showScoredPropositions(List)}, then calls {@link #setSearchResultsBar(String, Runnable)}.
     *
     * @param onSearchSubmit callback receiving the raw query text, or null to disable submit
     */
    public void setOnSearchSubmit(Consumer<String> onSearchSubmit) {
        this.onSearchSubmit = onSearchSubmit;
    }

    /**
     * Programmatically set the search field's text and run the same instant (L1) filter that
     * typing triggers on every keystroke. Doesn't submit for semantic search — that only
     * happens on Enter, same as when a user types.
     *
     * @param query the search text to set; null or empty clears the filter
     */
    public void setSearchQuery(String query) {
        var value = query == null ? "" : query;
        searchField.setValue(value);
        applyInstantFilter(value);
    }

    /**
     * Set the handler invoked with an entity pill's display name when a pill on a memory card
     * is clicked. Pills stay non-clickable (no cursor change) for cards rendered while this is
     * null.
     *
     * @param onEntityPillClick callback receiving the clicked entity's display name, or null to disable
     */
    public void setOnEntityPillClick(Consumer<String> onEntityPillClick) {
        this.onEntityPillClick = onEntityPillClick;
    }

    /**
     * Show or hide the slim "Semantic results for '&lt;label&gt;' — Clear" bar above the list.
     *
     * @param label the submitted query text to display, or null to hide the bar
     * @param onClear invoked when the bar's Clear link is clicked, after the bar is hidden
     */
    public void setSearchResultsBar(String label, Runnable onClear) {
        this.searchResultsBarOnClear = onClear;
        if (label == null) {
            searchResultsBar.setVisible(false);
            return;
        }
        searchResultsBarLabel.setText("Semantic results for \"" + label + "\"");
        searchResultsBar.setVisible(true);
    }

    /**
     * Show the results bar with the given text as-is — no "Semantic results for" wrapper.
     * Used for answer-style results (question answering) where the text is a sentence,
     * not an echoed query.
     *
     * @param text the literal bar text, or null to hide the bar
     * @param onClear invoked when the bar's Clear link is clicked, after the bar is hidden
     */
    public void setSearchResultsBarText(String text, Runnable onClear) {
        this.searchResultsBarOnClear = onClear;
        if (text == null) {
            searchResultsBar.setVisible(false);
            return;
        }
        searchResultsBarLabel.setText(text);
        searchResultsBar.setVisible(true);
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

            // Add dedup badge if this survivor collapsed other results (positioned absolutely via CSS)
            int collapsedCount = group.getCollapsedCount();
            if (collapsedCount > 0) {
                var dedupBadge = new Span("+" + collapsedCount + " similar");
                dedupBadge.addClassName("dedup-collapsed-badge");
                cardContainer.add(dedupBadge);
            }

            propositionsContent.add(cardContainer);
        }
    }

    /** Inject CSS styling for scored mode cards and dedup badges. */
    private void injectScoredModeStyles() {
        String css = """
            .scored-card-wrapper {
              position: relative;
              width: 100%;
              border: 1px solid var(--lumo-contrast-20pct);
              border-radius: var(--lumo-border-radius-m);
              background: var(--lumo-base-color);
              padding: var(--lumo-space-s);
              margin-bottom: var(--lumo-space-xs);
              display: flex;
              flex-direction: column;
            }

            .scored-card-wrapper .proposition-card {
              background: transparent;
              border: none;
              padding: 0;
              margin-bottom: 0;
              width: 100%;
            }

            .scored-card-wrapper .proposition-card:hover {
              border-color: transparent;
            }

            .scored-card-content {
              flex: 1;
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
        if (clustersProvider != null) {
            refreshProviderClustered();
            return;
        }
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

        // Render each cluster as a light, always-open container per the approved design: a
        // slim header ("Cluster: N similar memories" + shared entity chips) holding the member
        // cards uniformly — no anchor/similar distinction, no per-card score badges.
        for (var cluster : clusters) {
            var anchor = cluster.getAnchor();
            List<SimilarityResult<Proposition>> similar = cluster.getSimilar();
            int clusterSize = similar.size() + 1;

            var members = new java.util.ArrayList<Proposition>();
            members.add(anchor);
            similar.forEach(sim -> members.add(sim.getMatch()));

            var header = new HorizontalLayout();
            header.setAlignItems(Alignment.CENTER);
            header.setSpacing(true);
            header.addClassName("cluster-head");

            var clusterIcon = VaadinIcon.CLUSTER.create();
            clusterIcon.addClassName("cluster-head-icon");

            var headerLabel = new Span("Cluster: " + clusterSize + " similar memories");
            headerLabel.addClassName("cluster-head-label");

            header.add(clusterIcon, headerLabel);
            for (var name : sharedEntityNames(members)) {
                var chip = new Span(name);
                chip.addClassName("cluster-head-chip");
                header.add(chip);
            }

            var container = new VerticalLayout();
            container.setPadding(true);
            container.setSpacing(true);
            container.addClassName("cluster-container");
            container.add(header);
            for (var member : members) {
                container.add(createCard(member));
            }

            propositionsContent.add(container);
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

    /**
     * Provider-driven Clusters mode: renders exactly what the host hands over via
     * {@link #setClustersProvider(Supplier)} — an edge rail per cluster (node dots + connectors
     * carrying provenance and an edge tag), and a "Link…" affordance on every unclustered card.
     * No similarity numbers anywhere; provenance is shown as Auto/Manual only.
     */
    private void refreshProviderClustered() {
        var snapshot = clustersProvider.get();
        var clusters = snapshot == null ? List.<MemoryClusterView>of() : snapshot.clusters();
        var unclustered = snapshot == null ? List.<Proposition>of() : snapshot.unclustered();

        int totalCount = unclustered.size() + clusters.stream().mapToInt(c -> c.members().size()).sum();
        propositionCountSpan.setText("(" + totalCount + " memories, " + clusters.size() + " clusters)");

        if (totalCount == 0) {
            var emptyMessage = new Span("No memories yet. Start a conversation and analyze it to build memories.");
            emptyMessage.addClassName("panel-empty-message");
            propositionsContent.add(emptyMessage);
            return;
        }

        for (var cluster : clusters) {
            propositionsContent.add(buildClusterContainer(cluster));
        }

        if (!unclustered.isEmpty()) {
            propositionsContent.add(buildUnclusteredSection(unclustered, snapshot));
        }

        if (!clusters.isEmpty() || !unclustered.isEmpty()) {
            propositionsContent.add(buildLegend());
        }
    }

    private Div buildClusterContainer(MemoryClusterView cluster) {
        var container = new Div();
        container.addClassName("cluster-container");

        var header = new HorizontalLayout();
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(true);
        header.addClassName("cluster-head");

        var kindChip = new Span(cluster.kind() == ClusterKind.MANUAL ? "Manual" : "Auto");
        kindChip.addClassName("cluster-kind-chip");
        if (cluster.kind() == ClusterKind.MANUAL) {
            kindChip.addClassName("manual");
        }

        var title = new Span(cluster.title());
        title.addClassName("cluster-head-label");

        var grow = new Span();
        grow.addClassName("cluster-head-grow");

        var mergeLink = new Button("Merge cluster…");
        mergeLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        mergeLink.addClassName("cluster-merge-link");
        mergeLink.addClickListener(e -> {
            if (onMergeCluster != null) {
                onMergeCluster.accept(cluster.id());
            }
        });

        header.add(kindChip, title, grow, mergeLink);
        header.setFlexGrow(1, grow);

        var rail = new Div();
        rail.addClassName("rail");
        for (var member : cluster.members()) {
            rail.add(buildMember(member, cluster.id()));
        }

        var foot = new Div();
        foot.addClassName("cluster-foot");
        var sweepLink = new Button("Sweep this cluster");
        sweepLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        sweepLink.addClassName("cluster-sweep-link");
        sweepLink.addClickListener(e -> {
            if (onSweepCluster != null) {
                onSweepCluster.accept(cluster.id());
            }
        });
        var dissolveLink = new Button("Dissolve cluster");
        dissolveLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        dissolveLink.addClassName("cluster-dissolve-link");
        dissolveLink.addClickListener(e -> {
            var dialog = new ConfirmDialog();
            dialog.setHeader("Dissolve cluster");
            dialog.setText("Remove all edges in \"" + cluster.title() + "\"? This can't be undone.");
            dialog.setCancelable(true);
            dialog.setConfirmText("Dissolve");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(ev -> {
                if (onDissolveCluster != null) {
                    onDissolveCluster.accept(cluster.id());
                }
            });
            dialog.open();
        });
        foot.add(sweepLink, dissolveLink);

        container.add(header, rail, foot);
        return container;
    }

    private static String edgeTagTooltip(String tag) {
        return switch (tag.toLowerCase()) {
            case "similar" -> "Duplicate candidate — a sweep can merge these";
            case "related" -> "Grouped for context only — never merged";
            default -> tag;
        };
    }

    private Div buildMember(ClusterMemberView member, String clusterId) {
        var wrapper = new Div();
        wrapper.addClassName("member");
        if (member.provenance() == EdgeProvenance.MANUAL) {
            wrapper.addClassName("manual-edge");
        }

        if (member.edgeTag() != null && !member.edgeTag().isBlank()) {
            var tag = new Span(member.edgeTag());
            tag.addClassName("edge-tag");
            var tooltip = edgeTagTooltip(member.edgeTag());
            tag.getElement().setAttribute("title", tooltip);
            tag.getElement().setAttribute("aria-label", tooltip);
            wrapper.add(tag);
        }

        var card = createCard(member.proposition());
        wrapper.add(card);

        var actions = new Div();
        actions.addClassName("edge-actions");
        var unlink = new Button(VaadinIcon.CLOSE_SMALL.create());
        unlink.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        unlink.addClassName("icon-btn");
        unlink.addClassName("unlink");
        unlink.getElement().setAttribute("aria-label", "Remove from cluster");
        unlink.getElement().setAttribute("title", "Remove from cluster");
        unlink.addClickListener(e -> {
            if (onRemoveEdge != null) {
                onRemoveEdge.accept(new RemoveEdgeRequest(member.proposition().getId(), clusterId));
            }
        });
        actions.add(unlink);
        wrapper.add(actions);

        return wrapper;
    }

    private Div buildUnclusteredSection(List<Proposition> unclustered, ClusteredMemories snapshot) {
        var section = new Div();
        section.addClassName("unclustered-section");

        var label = new Span("Unclustered — " + unclustered.size()
                + (unclustered.size() == 1 ? " memory" : " memories"));
        label.addClassName("section-label");
        section.add(label);

        var list = new Div();
        list.addClassName("unclustered");
        for (var prop : unclustered) {
            var cardWrapper = new Div();
            cardWrapper.addClassName("unclustered-member");
            var card = createCard(prop);
            cardWrapper.add(card);
            addLinkAffordance(cardWrapper, card, prop, snapshot);
            list.add(cardWrapper);
        }
        section.add(list);
        return section;
    }

    /**
     * Finds the card's meta row (same technique {@link #wireUndoMergeLink} uses to reach into a
     * PropositionCard) and adds a "Link…" pill that opens the link popover for this memory.
     */
    private void addLinkAffordance(Div cardWrapper, PropositionCard card, Proposition prop, ClusteredMemories snapshot) {
        var metaRow = allComponents(card).stream()
                .filter(c -> c.hasClassName("proposition-meta"))
                .findFirst()
                .orElse(null);
        if (!(metaRow instanceof HasComponents metaContainer)) {
            return;
        }
        var linkButton = new Button("Link…", VaadinIcon.CONNECT.create());
        linkButton.addClassName("link-btn");
        linkButton.getElement().setAttribute("aria-label", "Link this memory to another memory or cluster");
        var popoverHolder = new Div();
        popoverHolder.addClassName("popover-holder");
        cardWrapper.add(popoverHolder);
        linkButton.addClickListener(e -> {
            popoverHolder.removeAll();
            popoverHolder.add(buildLinkPopover(popoverHolder, prop, snapshot));
        });
        metaContainer.add(linkButton);
    }

    private Div buildLinkPopover(Div holder, Proposition source, ClusteredMemories snapshot) {
        var popover = new Div();
        popover.addClassName("popover");

        var label = new Span("Link this memory to…");
        label.addClassName("p-label");
        popover.add(label);

        var searchWrap = new Div();
        searchWrap.addClassName("p-search");
        var searchField = new TextField();
        searchField.setPlaceholder("Search clusters, memories, entities…");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.addClassName("p-search-input");
        searchWrap.add(searchField);
        popover.add(searchWrap);

        var rowsContainer = new Div();
        rowsContainer.addClassName("p-targets");
        popover.add(rowsContainer);

        var relationLabel = new Span("Relation");
        relationLabel.addClassName("p-label");
        relationLabel.addClassName("relation-label");
        popover.add(relationLabel);

        var relRow = new Div();
        relRow.addClassName("rel-row");
        var dupOf = new Button("Duplicate of");
        dupOf.addClassName("rel");
        dupOf.addClassName("sel");
        var relatedTo = new Button("Related to");
        relatedTo.addClassName("rel");
        var relation = new EdgeKind[]{EdgeKind.DUPLICATE_OF};
        dupOf.addClickListener(ev -> {
            relation[0] = EdgeKind.DUPLICATE_OF;
            dupOf.addClassName("sel");
            relatedTo.removeClassName("sel");
        });
        relatedTo.addClickListener(ev -> {
            relation[0] = EdgeKind.RELATED_TO;
            relatedTo.addClassName("sel");
            dupOf.removeClassName("sel");
        });
        relRow.add(dupOf, relatedTo);
        popover.add(relRow);

        var selectedTarget = new LinkTarget[1];

        var actions = new Div();
        actions.addClassName("p-actions");
        var cancel = new Button("Cancel");
        cancel.addClassName("btn");
        cancel.addClickListener(ev -> holder.removeAll());
        var addEdge = new Button("Add edge");
        addEdge.addClassName("btn");
        addEdge.addClassName("primary");
        addEdge.addClickListener(ev -> {
            var target = selectedTarget[0];
            if (target == null) {
                return;
            }
            if (target.kind() == LinkTargetKind.ENTITY) {
                if (onLinkEntity != null) {
                    onLinkEntity.accept(new EntityLinkRequest(source.getId(), target.id()));
                }
            } else if (onAddEdge != null) {
                onAddEdge.accept(new AddEdgeRequest(source.getId(),
                        target.kind() == LinkTargetKind.CLUSTER ? target.id() : null,
                        target.kind() == LinkTargetKind.MEMORY ? target.id() : null,
                        relation[0]));
            }
            holder.removeAll();
        });
        actions.add(cancel, addEdge);
        popover.add(actions);

        Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            var query = searchField.getValue();
            var targets = linkTargets(query, source, snapshot);
            rowsContainer.removeAll();
            for (var target : targets) {
                var row = buildTargetRow(target);
                row.addClickListener(ev -> {
                    selectedTarget[0] = target;
                    rowsContainer.getChildren().forEach(r -> r.removeClassName("sel"));
                    row.addClassName("sel");
                    boolean isEntity = target.kind() == LinkTargetKind.ENTITY;
                    relationLabel.setVisible(!isEntity);
                    relRow.setVisible(!isEntity);
                });
                rowsContainer.add(row);
            }
        };
        searchField.addValueChangeListener(e -> renderRef[0].run());
        renderRef[0].run();

        return popover;
    }

    /**
     * The Link… popover's target list for a query: the host's search function when one is set
     * (empty query gets the host's own default ordering), or the panel's built-in fallback —
     * clusters then unclustered memories from the provider snapshot — so existing hosts that
     * never call {@link #setLinkTargetSearch} keep working unchanged.
     */
    private List<LinkTarget> linkTargets(String query, Proposition source, ClusteredMemories snapshot) {
        if (linkTargetSearch != null) {
            return linkTargetSearch.apply(query == null ? "" : query);
        }
        var out = new java.util.ArrayList<LinkTarget>();
        for (var cluster : snapshot.clusters()) {
            out.add(new LinkTarget(LinkTargetKind.CLUSTER, cluster.id(),
                    cluster.title() + " (" + cluster.members().size() + " memories)"));
        }
        for (var candidate : snapshot.unclustered()) {
            if (candidate.getId().equals(source.getId())) {
                continue;
            }
            out.add(new LinkTarget(LinkTargetKind.MEMORY, candidate.getId(), candidate.getText()));
        }
        return out;
    }

    private static String kindLabel(LinkTargetKind kind) {
        return switch (kind) {
            case CLUSTER -> "Cluster";
            case MEMORY -> "Memory";
            case ENTITY -> "Entity";
        };
    }

    private static Button buildTargetRow(LinkTarget target) {
        var kindText = kindLabel(target.kind());
        var kindChip = new Span(kindText);
        kindChip.addClassName("t-kind");
        if (target.kind() == LinkTargetKind.ENTITY) {
            kindChip.addClassName("ent");
        }
        var labelSpan = new Span(target.label());
        labelSpan.addClassName("t-txt");
        var row = new Button();
        row.getElement().appendChild(kindChip.getElement(), labelSpan.getElement());
        row.addClassName("target");
        row.addClassName("target-" + kindText.toLowerCase());
        row.getElement().setAttribute("data-kind", kindText);
        row.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return row;
    }

    private Div buildLegend() {
        var legend = new Div();
        legend.addClassName("legend");
        var auto = new Span("Auto edge (similarity sweep)");
        auto.addClassName("legend-item");
        auto.addClassName("auto");
        var manual = new Span("Manual edge (user-added)");
        manual.addClassName("legend-item");
        manual.addClassName("manual");
        var similar = new Span("similar = duplicate candidate, a sweep can merge it");
        similar.addClassName("legend-item");
        similar.addClassName("tag-definition");
        var related = new Span("related = grouped for context only, never merged");
        related.addClassName("legend-item");
        related.addClassName("tag-definition");
        legend.add(auto, manual, similar, related);
        return legend;
    }

    private PropositionCard createCard(Proposition prop) {
        var card = new PropositionCard(prop, entityResolver, collapseExplanationProvider, onEntityPillClick);
        card.setLineageProvider(lineageProvider);
        card.setRelatedPropositionsLoader(relatedPropositionsLoader);
        card.setRelatedRecordsLoader(relatedRecordsLoader);
        if (onUndoMember != null) {
            card.setOnUndoMember(onUndoMember);
        }
        if (onAfterUndo != null) {
            card.setOnAfterUndo(onAfterUndo);
        }
        if (onOpenRef != null) {
            card.setOnOpenRef(onOpenRef);
        }
        if (openable != null) {
            card.setOpenable(openable);
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
        wireUndoMergeLink(card, prop);
        return card;
    }

    /**
     * Adds a visible "Undo merge" link right after the "Merged N duplicates" badge on a merged
     * card, per the approved design (always visible, not hover-only). PropositionCard itself
     * owns that badge and isn't touched here — we find it in the rendered tree and add a sibling
     * into its parent layout, then wire it to fire the same (survivorId, retiredId) callbacks
     * the existing lineage-dialog undo path uses, once per retired member.
     */
    private void wireUndoMergeLink(PropositionCard card, Proposition prop) {
        if (collapseExplanationProvider == null) {
            return;
        }
        collapseExplanationProvider.explain(prop.getId())
                .filter(explanation -> !explanation.retired().isEmpty())
                .ifPresent(explanation -> {
                    var badge = findCollapseBadge(card);
                    if (badge == null) {
                        return;
                    }
                    var parent = badge.getParent().orElse(null);
                    if (!(parent instanceof HasComponents container)) {
                        return;
                    }
                    var undoLink = new Button("Undo merge", VaadinIcon.ARROW_BACKWARD.create());
                    undoLink.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                    undoLink.addClassName("undo-merge-link");
                    undoLink.getElement().setAttribute("title",
                            "Restore the " + explanation.retired().size() + " collapsed duplicate memories");
                    undoLink.addClickListener(e -> {
                        var survivorId = prop.getId();
                        for (var member : explanation.retired()) {
                            if (onUndoMember != null) {
                                onUndoMember.accept(survivorId, member.propositionId());
                            }
                        }
                        refresh();
                        if (onAfterUndo != null) {
                            for (var member : explanation.retired()) {
                                onAfterUndo.accept(survivorId, member.propositionId());
                            }
                        }
                    });
                    container.add(undoLink);
                });
    }

    private static Button findCollapseBadge(Component root) {
        return allComponents(root).stream()
                .filter(c -> c instanceof Button && c.hasClassName("collapse-explanation-badge"))
                .map(c -> (Button) c)
                .findFirst()
                .orElse(null);
    }

    /** Entity names shared by two or more of the given propositions, in first-seen order, capped at 4. */
    private List<String> sharedEntityNames(List<Proposition> members) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var member : members) {
            var seenOnThisMember = new HashSet<String>();
            for (var mention : member.getMentions()) {
                var name = resolvedEntityName(mention);
                if (name != null && seenOnThisMember.add(name)) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .limit(4)
                .toList();
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

    /**
     * Set the handler to invoke after an undo completes in the lineage section.
     * Fired after the proposition has been re-rendered with the new state.
     *
     * @param onAfterUndo callback receiving (survivorId, retiredMemberId) after undo lands,
     *                    or null to disable the callback
     */
    public void setOnAfterUndo(BiConsumer<String, String> onAfterUndo) {
        this.onAfterUndo = onAfterUndo;
    }

    /**
     * Set the handler to invoke when an Open button is clicked on a grounding/provenance ref in a lineage section.
     *
     * @param onOpenRef callback receiving the ref string when an Open button is clicked,
     *                  or null to disable the Open affordance
     */
    public void setOnOpenRef(Consumer<String> onOpenRef) {
        this.onOpenRef = onOpenRef;
    }

    /**
     * Set the predicate to filter which refs show an Open button in lineage sections.
     *
     * @param openable predicate testing whether a ref is openable, or null to allow all refs
     */
    public void setOpenable(Predicate<String> openable) {
        this.openable = openable;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
        this.scoredMode = false;
        // Leaving scored mode: bring back the header controls showScoredPropositions hid.
        // (A host whose normal state IS scored — the side panel — re-hides them on its next
        // refresh when the supplier re-enters scored mode.)
        statusSelect.setVisible(true);
        clusterToggle.setVisible(true);
    }

    public void setScoredResultsSupplier(Supplier<List<SimilarityResult<Proposition>>> supplier) {
        this.scoredResultsSupplier = supplier;
    }

    /**
     * Hand over a pre-computed clustering for Clusters mode. When set, clustered mode renders
     * exactly what the supplier returns (edge rail, provenance, "Link…" affordances) instead of
     * the panel's internal similarity-based clustering. Pass null to restore internal behavior.
     *
     * @param clustersProvider supplies the current clusters + unclustered memories, or null
     */
    public void setClustersProvider(Supplier<ClusteredMemories> clustersProvider) {
        this.clustersProvider = clustersProvider;
    }

    /**
     * Set the handler invoked when a user completes the "Link…" popover, asking the host to
     * create a manual edge from one memory to a chosen cluster or memory.
     *
     * @param onAddEdge callback receiving the completed request, or null to disable the affordance's effect
     */
    public void setOnAddEdge(Consumer<AddEdgeRequest> onAddEdge) {
        this.onAddEdge = onAddEdge;
    }

    /**
     * Set the search function driving the Link… popover's live target list: called with the
     * search box's text on every value change (including empty, for the host's default
     * ordering — clusters, then unclustered memories, then top entities). When unset, the panel
     * falls back to the static clusters + unclustered memories from the provider snapshot.
     *
     * @param linkTargetSearch function from query text to the targets to show, or null to fall back
     */
    public void setLinkTargetSearch(Function<String, List<LinkTarget>> linkTargetSearch) {
        this.linkTargetSearch = linkTargetSearch;
    }

    /**
     * Set the handler invoked when a user picks an Entity target in the Link… popover and clicks
     * Add edge — always an association (memory→entity mention), never a cluster edge, so the
     * relation choice is hidden for entity targets.
     *
     * @param onLinkEntity callback receiving the completed request, or null to disable the affordance's effect
     */
    public void setOnLinkEntity(Consumer<EntityLinkRequest> onLinkEntity) {
        this.onLinkEntity = onLinkEntity;
    }

    /**
     * Set the handler invoked when a user removes a single member's edge from a cluster via the
     * per-member unlink icon.
     *
     * @param onRemoveEdge callback receiving the removed member and its cluster, or null to disable
     */
    public void setOnRemoveEdge(Consumer<RemoveEdgeRequest> onRemoveEdge) {
        this.onRemoveEdge = onRemoveEdge;
    }

    /**
     * Set the handler invoked, after user confirmation, when "Dissolve cluster" is clicked.
     *
     * @param onDissolveCluster callback receiving the cluster id, or null to disable the action
     */
    public void setOnDissolveCluster(Consumer<String> onDissolveCluster) {
        this.onDissolveCluster = onDissolveCluster;
    }

    /**
     * Set the handler invoked when "Sweep this cluster" is clicked.
     *
     * @param onSweepCluster callback receiving the cluster id, or null to disable the action
     */
    public void setOnSweepCluster(Consumer<String> onSweepCluster) {
        this.onSweepCluster = onSweepCluster;
    }

    /**
     * Set the handler invoked when "Merge cluster…" is clicked.
     *
     * @param onMergeCluster callback receiving the cluster id, or null to disable the action
     */
    public void setOnMergeCluster(Consumer<String> onMergeCluster) {
        this.onMergeCluster = onMergeCluster;
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

    /**
     * Open the inline editor for a memory card by its proposition ID.
     * Walks the rendered cards to find one matching the given ID and opens its editor.
     *
     * @param propositionId the ID of the proposition to edit
     * @return true if a card with the given ID was found and editor opened, false otherwise
     */
    public boolean openEditor(String propositionId) {
        var card = findCardByPropositionId(propositionId);
        if (card.isPresent()) {
            card.get().openEditor();
            return true;
        }
        return false;
    }

    private java.util.Optional<PropositionCard> findCardByPropositionId(String propositionId) {
        // Walk all children of propositionsContent (flat, clustered, or scored mode) to find matching card
        return allComponents(propositionsContent).stream()
                .filter(c -> c instanceof PropositionCard)
                .map(c -> (PropositionCard) c)
                .filter(card -> card.getProposition().getId().equals(propositionId))
                .findFirst();
    }

    private static java.util.List<Component> allComponents(Component root) {
        var out = new java.util.ArrayList<Component>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, java.util.List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }

    /** Walks up the component tree from {@code start} looking for the nearest ancestor with {@code className}. */
    private static Component findAncestorWithClass(Component start, String className) {
        var current = start.getParent();
        while (current.isPresent()) {
            var c = current.get();
            if (c.hasClassName(className)) {
                return c;
            }
            current = c.getParent();
        }
        return null;
    }
}
