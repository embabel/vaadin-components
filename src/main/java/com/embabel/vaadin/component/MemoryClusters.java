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

import com.embabel.dice.proposition.Proposition;

import java.util.List;

/**
 * View model for the Clusters mode of the Memories page. A host wires
 * {@link PropositionsPanel#setClustersProvider(java.util.function.Supplier)} to hand over a
 * pre-computed grouping of memories (auto-clustered by similarity, or manually assembled by the
 * user) instead of letting the panel run its own internal similarity clustering.
 */
public final class MemoryClusters {

    private MemoryClusters() {
    }

    /** Where a cluster or an edge came from — the similarity sweep, or a user's manual action. */
    public enum ClusterKind {
        AUTO, MANUAL
    }

    /** Same distinction, applied to a single membership edge rather than the whole cluster. */
    public enum EdgeProvenance {
        AUTO, MANUAL
    }

    /** The relation a user picks when manually linking two memories (or a memory to a cluster). */
    public enum EdgeKind {
        DUPLICATE_OF, RELATED_TO
    }

    /**
     * A group of memories the UI renders as one cluster container.
     *
     * @param id     stable cluster id used by the mutation callbacks
     * @param title  short label shown in the cluster header
     * @param kind   whether this cluster came from the similarity sweep or was built by hand
     * @param members the memories in the cluster, in display order
     */
    public record MemoryClusterView(String id, String title, ClusterKind kind, List<ClusterMemberView> members) {
    }

    /**
     * One memory's membership in a cluster, carrying the provenance and label of the edge that
     * put it there (used to draw the rail connector and its tag).
     *
     * @param proposition  the memory itself
     * @param provenance   whether this member's edge is from the similarity sweep or user-added
     * @param edgeTag      short label on the connector ("similar", "related"), or null for none
     */
    public record ClusterMemberView(Proposition proposition, EdgeProvenance provenance, String edgeTag) {
    }

    /**
     * Everything the Clusters view renders for a context: the clusters, plus the memories that
     * don't belong to any of them.
     *
     * @param clusters    clusters in display order
     * @param unclustered memories with no cluster membership, in display order
     */
    public record ClusteredMemories(List<MemoryClusterView> clusters, List<Proposition> unclustered) {
    }

    /**
     * Fired when a user completes the "Link…" popover flow, asking the host to create a manual
     * edge from one memory to either an existing cluster or another single memory.
     *
     * @param fromPropositionId  the memory the Link… action was opened from
     * @param targetClusterId    the chosen cluster's id, or null if a memory was chosen instead
     * @param targetPropositionId the chosen memory's id, or null if a cluster was chosen instead
     * @param kind                "Duplicate of" or "Related to"
     */
    public record AddEdgeRequest(String fromPropositionId, String targetClusterId, String targetPropositionId,
                                  EdgeKind kind) {
    }

    /**
     * Fired when a user removes one member's edge from a cluster via the per-member unlink icon.
     *
     * @param memberPropositionId the member being removed
     * @param clusterId           the cluster it's being removed from
     */
    public record RemoveEdgeRequest(String memberPropositionId, String clusterId) {
    }
}
