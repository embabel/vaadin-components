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

import java.util.List;

/**
 * Why a memory was collapsed into another one: the survivor it lives on as, the memories folded
 * into it, and the per-signal scoring that led to the merge. This is a plain, service-neutral view
 * of a host application's collector/dedup trace — it doesn't know or care how that trace is stored.
 *
 * @param componentId  id of the duplicate group this collapse belongs to
 * @param survivorId   id of the proposition that was kept
 * @param survivorText text of the surviving proposition
 * @param action       what the collector did (e.g. "MERGE"), as a plain label
 * @param retired      the memories that were folded into the survivor
 * @param edges        the scored candidate pairs behind the decision, each with its per-signal breakdown
 */
public record CollapseExplanation(
        String componentId,
        String survivorId,
        String survivorText,
        String action,
        List<RetiredMember> retired,
        List<ScoredEdge> edges) {

    /**
     * One memory that got folded into the survivor.
     *
     * @param propositionId       id of the retired memory
     * @param text                text of the retired memory, if it could still be resolved; null otherwise
     * @param priorStatus         the memory's status right before it was retired
     * @param foldedGrounding     grounding references folded onto the survivor from this memory
     * @param foldedProvenanceRefs provenance references folded onto the survivor from this memory
     * @param foldedSourceIds     source ids folded onto the survivor from this memory
     */
    public record RetiredMember(
            String propositionId,
            String text,
            String priorStatus,
            List<String> foldedGrounding,
            List<String> foldedProvenanceRefs,
            List<String> foldedSourceIds) {
    }

    /**
     * One scored candidate pair considered by the collector, with its per-signal breakdown.
     *
     * @param anchorId       id of the anchor proposition in the pair
     * @param memberId       id of the other proposition in the pair
     * @param aggregateScore the blended score across all signals
     * @param vetoed         true if a signal vetoed the merge outright
     * @param signals        the individual signal contributions behind the aggregate score
     */
    public record ScoredEdge(
            String anchorId,
            String memberId,
            double aggregateScore,
            boolean vetoed,
            List<SignalScore> signals) {
    }

    /**
     * One signal's contribution to an edge's score.
     *
     * @param signal      name of the signal (e.g. "vector", "lexical", "entity-overlap")
     * @param score       the signal's raw score
     * @param weight      the weight this signal carried in the blend
     * @param veto        true if this signal vetoed the merge
     * @param explanation a short human-readable reason for the score, if the signal provided one
     * @param evidenceRef a pointer to supporting evidence, if the signal provided one
     */
    public record SignalScore(
            String signal,
            double score,
            double weight,
            boolean veto,
            String explanation,
            String evidenceRef) {
    }
}
