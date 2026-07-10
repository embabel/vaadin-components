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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Reusable memory section with file upload ("Remember"), analyze, clear all,
 * and propositions display. Parameterized by context so it can be used for
 * user-specific, global, or bot-specific memories.
 */
public class MemorySection extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(MemorySection.class);

    private final PropositionsPanel propositionsPanel;
    private final Button sweepButton;
    private com.vaadin.flow.shared.Registration sweepListenerRegistration;

    public record RememberRequest(InputStream inputStream, String filename) {}

    /**
     * Convenience constructor for callers that don't explain collapses.
     */
    public MemorySection(
            PropositionRepository propositionRepository,
            Function<String, NamedEntity> entityResolver,
            Supplier<String> contextIdSupplier,
            Runnable onAnalyze,
            Consumer<RememberRequest> onRemember,
            Consumer<String> onClearContext) {
        this(propositionRepository, entityResolver, null, contextIdSupplier, onAnalyze, onRemember, onClearContext);
    }

    /**
     * Memory section with file upload (Learn button), optional Analyze trigger, Clear All action,
     * and a propositions panel showing the knowledge base for this context. Optionally explains why
     * propositions were collapsed into one another.
     *
     * @param propositionRepository stores and queries propositions
     * @param entityResolver resolves entity mention IDs to NamedEntity; null to show mentions unresolved
     * @param collapseExplanationProvider looks up why propositions were collapsed, if at all; null skips collapse badges
     * @param contextIdSupplier supplies the context ID for this section; can change across the lifetime of the component
     * @param onAnalyze invoked when the Analyze button is clicked; null hides the button
     * @param onRemember invoked when a file upload succeeds; null hides the Learn button
     * @param onClearContext invoked when Clear All is confirmed with the current context ID
     */
    public MemorySection(
            PropositionRepository propositionRepository,
            Function<String, NamedEntity> entityResolver,
            CollapseExplanationProvider collapseExplanationProvider,
            Supplier<String> contextIdSupplier,
            Runnable onAnalyze,
            Consumer<RememberRequest> onRemember,
            Consumer<String> onClearContext) {

        // Create propositions panel early (referenced by button listeners)
        propositionsPanel = new PropositionsPanel(propositionRepository, entityResolver, collapseExplanationProvider);
        propositionsPanel.setContextId(contextIdSupplier.get());
        propositionsPanel.setOnDelete(id -> {
            var deleted = propositionRepository.delete(id);
            logger.info("Delete proposition {}: {}", id, deleted ? "success" : "not found");
        });
        propositionsPanel.setOnEdit(propositionRepository::save);

        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // Button row
        var buttonRow = new HorizontalLayout();
        buttonRow.setWidthFull();
        buttonRow.setSpacing(true);
        buttonRow.addClassName("memory-button-row");

        // Upload status row — shown below buttons during upload/extraction
        var statusRow = new HorizontalLayout();
        statusRow.setWidthFull();
        statusRow.setAlignItems(Alignment.CENTER);
        statusRow.setSpacing(true);
        statusRow.setPadding(false);
        statusRow.addClassName("learn-status-row");
        statusRow.setVisible(false);

        var statusLabel = new Span();
        statusLabel.addClassName("learn-status-label");

        var statusBar = new ProgressBar();
        statusBar.addClassName("learn-progress-bar");

        statusRow.add(statusLabel, statusBar);
        statusRow.setFlexGrow(1, statusBar);

        // "Learn" file upload — progress shown in status row below
        if (onRemember != null) {
            var buffer = new MemoryBuffer();
            var upload = new Upload(buffer);
            upload.setDropAllowed(false);
            var learnButton = new Button("Learn", VaadinIcon.BOOK.create());
            upload.setUploadButton(learnButton);
            upload.setAcceptedFileTypes(
                    ".pdf", ".txt", ".md", ".html", ".htm",
                    ".doc", ".docx", ".odt", ".rtf",
                    "application/pdf",
                    "text/plain",
                    "text/markdown",
                    "text/html",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
            upload.setMaxFileSize(10 * 1024 * 1024); // 10MB
            upload.setMaxFiles(1);
            upload.addClassName("learn-upload");

            // Immediately clear the file list so it never shows inline
            upload.getElement().addEventListener("upload-start", e ->
                    upload.getElement().executeJs("this.files = []"));
            upload.getElement().addEventListener("upload-success", e ->
                    upload.getElement().executeJs("this.files = []"));

            upload.addStartedListener(event -> {
                statusLabel.setText("Uploading: " + event.getFileName());
                statusBar.setIndeterminate(false);
                statusBar.setValue(0);
                statusRow.setVisible(true);
            });

            upload.addProgressListener(event -> {
                if (event.getContentLength() > 0) {
                    statusBar.setIndeterminate(false);
                    statusBar.setValue((double) event.getReadBytes() / event.getContentLength());
                } else {
                    statusBar.setIndeterminate(true);
                }
            });

            upload.addSucceededListener(event -> {
                var filename = event.getFileName();
                statusLabel.setText("Extracting memories from: " + filename);
                statusBar.setIndeterminate(true);
                try {
                    onRemember.accept(new RememberRequest(buffer.getInputStream(), filename));
                    // Hide status after extraction has had time to start
                    getUI().ifPresent(ui -> {
                        propositionsPanel.scheduleRefresh(ui, 5000);
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000);
                                ui.access(() -> statusRow.setVisible(false));
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                    });
                } catch (Exception e) {
                    logger.error("Failed to remember file: {}", filename, e);
                    statusLabel.setText("Error: " + e.getMessage());
                    statusBar.setVisible(false);
                }
            });

            upload.addFailedListener(event -> {
                logger.error("Upload failed: {}", event.getReason().getMessage());
                statusLabel.setText("Upload failed");
                statusRow.setVisible(false);
                Notification.show("Upload failed: " + event.getReason().getMessage(),
                        5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            });

            buttonRow.add(upload);
        }

        // "Analyze" button (optional)
        if (onAnalyze != null) {
            var analyzeButton = new Button("Analyze", VaadinIcon.COG.create());
            analyzeButton.addClickListener(e -> {
                onAnalyze.run();
                getUI().ifPresent(ui -> propositionsPanel.scheduleRefresh(ui, 5000));
            });
            buttonRow.add(analyzeButton);
        }

        // "Sweep" button — sits in the actions row next to Analyze, matching its style. Hidden
        // until a host wires setOnSweep; there's no old top-left placement for this to replace,
        // it's new here.
        sweepButton = new Button("Sweep", VaadinIcon.REFRESH.create());
        sweepButton.addClassName("memory-sweep");
        sweepButton.setVisible(false);
        sweepButton.getElement().setAttribute("title", "Run a dedup sweep");
        sweepButton.getElement().setAttribute("aria-label", "Run a dedup sweep");
        buttonRow.add(sweepButton);

        // "Clear All" button
        var clearAllButton = new Button("Clear All", VaadinIcon.TRASH.create());
        clearAllButton.addClassName("memory-clear-all");
        clearAllButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        clearAllButton.addClickListener(e -> {
            var dialog = new ConfirmDialog();
            dialog.setHeader("Clear All Memories");
            dialog.setText("Are you sure you want to delete all memories for this context? This cannot be undone.");
            dialog.setCancelable(true);
            dialog.setConfirmText("Clear All");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(event -> {
                onClearContext.accept(contextIdSupplier.get());
                propositionsPanel.refresh();
            });
            dialog.open();
        });
        buttonRow.add(clearAllButton);

        add(buttonRow, statusRow, propositionsPanel);
        setFlexGrow(1, propositionsPanel);
    }

    public void refresh() {
        propositionsPanel.refresh();
    }

    public void setContextId(String contextId) {
        propositionsPanel.setContextId(contextId);
    }

    /**
     * Looks up lineage for a proposition, or null to hide the lineage affordance.
     */
    public void setLineageProvider(LineageProvider lineageProvider) {
        propositionsPanel.setLineageProvider(lineageProvider);
    }

    /**
     * Looks up propositions mentioning an entity, or null to omit the related-memories section.
     */
    public void setRelatedPropositionsLoader(Function<String, List<Proposition>> relatedPropositionsLoader) {
        propositionsPanel.setRelatedPropositionsLoader(relatedPropositionsLoader);
    }

    /**
     * Loader for the entity-360 related-records shown when an entity is opened from a memory card.
     */
    public void setRelatedRecordsLoader(Function<String, EntityPanel.RelatedRecords> relatedRecordsLoader) {
        propositionsPanel.setRelatedRecordsLoader(relatedRecordsLoader);
    }

    /**
     * Set the handler to invoke when an "Undo this merge" button is clicked in a lineage section.
     */
    public void setOnUndoMember(BiConsumer<String, String> onUndoMember) {
        propositionsPanel.setOnUndoMember(onUndoMember);
    }

    /**
     * Set the handler to invoke after an undo completes in the lineage section.
     */
    public void setOnAfterUndo(BiConsumer<String, String> onAfterUndo) {
        propositionsPanel.setOnAfterUndo(onAfterUndo);
    }

    /**
     * Set the handler to invoke when an Open button is clicked on a grounding/provenance ref in a lineage section.
     */
    public void setOnOpenRef(Consumer<String> onOpenRef) {
        propositionsPanel.setOnOpenRef(onOpenRef);
    }

    /**
     * Set the predicate to filter which refs show an Open button in lineage sections.
     */
    public void setOpenable(Predicate<String> openable) {
        propositionsPanel.setOpenable(openable);
    }

    /**
     * Programmatically open the inline editor for a memory card by its proposition ID.
     *
     * @param propositionId the ID of the proposition to edit
     * @return true if a card with the given ID was found and editor opened, false otherwise
     */
    public boolean openEditor(String propositionId) {
        return propositionsPanel.openEditor(propositionId);
    }

    /**
     * Set the handler invoked with the raw search text when Enter is pressed in the header
     * search field. This vaadin component stays dumb: it fires the raw query, and the host
     * runs semantic search / question-answering / operator parsing and pushes results back
     * through {@link #showScoredPropositions(List)}, then {@link #setSearchResultsBar(String, Runnable)}.
     *
     * @param onSearchSubmit callback receiving the raw query text, or null to disable submit
     */
    public void setOnSearchSubmit(Consumer<String> onSearchSubmit) {
        propositionsPanel.setOnSearchSubmit(onSearchSubmit);
    }

    /**
     * Programmatically set the header search field's text and run the same instant filter that
     * typing triggers on every keystroke.
     *
     * @param query the search text to set; null or empty clears the filter
     */
    public void setSearchQuery(String query) {
        propositionsPanel.setSearchQuery(query);
    }

    /**
     * Set the handler invoked with an entity pill's display name when a pill on a memory card
     * is clicked. Pills stay non-clickable (no cursor change) while this is null.
     *
     * @param onEntityPillClick callback receiving the clicked entity's display name, or null to disable
     */
    public void setOnEntityPillClick(Consumer<String> onEntityPillClick) {
        propositionsPanel.setOnEntityPillClick(onEntityPillClick);
    }

    /**
     * Set the handler invoked when the "Sweep" button in the actions row (next to Analyze) is
     * clicked, and show that button. Pass null to hide it again.
     *
     * @param onSweep invoked when Sweep is clicked, or null to hide the button
     */
    public void setOnSweep(Runnable onSweep) {
        sweepButton.setVisible(onSweep != null);
        if (sweepListenerRegistration != null) {
            sweepListenerRegistration.remove();
            sweepListenerRegistration = null;
        }
        if (onSweep != null) {
            sweepListenerRegistration = sweepButton.addClickListener(e -> onSweep.run());
        }
    }

    /**
     * Show or hide the slim "Semantic results for '&lt;label&gt;' — Clear" bar above the list.
     *
     * @param label the submitted query text to display, or null to hide the bar
     * @param onClear invoked when the bar's Clear link is clicked, after the bar is hidden
     */
    public void setSearchResultsBar(String label, Runnable onClear) {
        propositionsPanel.setSearchResultsBar(label, onClear);
    }

    /**
     * Display search results with relevance scores — the L2 semantic-search results path.
     * Delegates to the panel; see {@link PropositionsPanel#showScoredPropositions(List)}.
     */
    public void showScoredPropositions(List<com.embabel.common.core.types.SimilarityResult<Proposition>> results) {
        propositionsPanel.showScoredPropositions(results);
    }

    /**
     * Hand over a pre-computed clustering for Clusters mode. See
     * {@link PropositionsPanel#setClustersProvider(Supplier)}.
     */
    public void setClustersProvider(Supplier<MemoryClusters.ClusteredMemories> clustersProvider) {
        propositionsPanel.setClustersProvider(clustersProvider);
    }

    /**
     * Set the handler invoked when a user completes the "Link…" popover in Clusters mode.
     */
    public void setOnAddEdge(Consumer<MemoryClusters.AddEdgeRequest> onAddEdge) {
        propositionsPanel.setOnAddEdge(onAddEdge);
    }

    /**
     * Set the handler invoked when a user removes a member's edge from a cluster.
     */
    public void setOnRemoveEdge(Consumer<MemoryClusters.RemoveEdgeRequest> onRemoveEdge) {
        propositionsPanel.setOnRemoveEdge(onRemoveEdge);
    }

    /**
     * Set the search function driving the Link… popover's live target list. See
     * {@link PropositionsPanel#setLinkTargetSearch(Function)}.
     */
    public void setLinkTargetSearch(Function<String, List<MemoryClusters.LinkTarget>> linkTargetSearch) {
        propositionsPanel.setLinkTargetSearch(linkTargetSearch);
    }

    /**
     * Set the handler invoked when a user links a memory to an Entity target. See
     * {@link PropositionsPanel#setOnLinkEntity(Consumer)}.
     */
    public void setOnLinkEntity(Consumer<MemoryClusters.EntityLinkRequest> onLinkEntity) {
        propositionsPanel.setOnLinkEntity(onLinkEntity);
    }

    /**
     * Set the handler invoked, after user confirmation, when "Dissolve cluster" is clicked.
     */
    public void setOnDissolveCluster(Consumer<String> onDissolveCluster) {
        propositionsPanel.setOnDissolveCluster(onDissolveCluster);
    }

    /**
     * Set the handler invoked when "Sweep this cluster" is clicked.
     */
    public void setOnSweepCluster(Consumer<String> onSweepCluster) {
        propositionsPanel.setOnSweepCluster(onSweepCluster);
    }

    /**
     * Set the handler invoked when "Merge cluster…" is clicked.
     */
    public void setOnMergeCluster(Consumer<String> onMergeCluster) {
        propositionsPanel.setOnMergeCluster(onMergeCluster);
    }
}
