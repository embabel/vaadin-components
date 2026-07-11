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
import java.util.Optional;

/**
 * Looks up where a memory came from: what grounded it, the provenance trail behind it, and (if it
 * was ever involved in a collapse) the merge that led to it. A host application implements this
 * against whatever trace store it keeps; this component doesn't know or care where that data
 * comes from.
 */
@FunctionalInterface
public interface LineageProvider {

    /**
     * @param propositionId id of the proposition to trace
     * @return the lineage for this proposition, if any is known; empty if nothing is on record
     */
    Optional<Lineage> lineageFor(String propositionId);

    /**
     * A proposition's full trail: what it's grounded on, its provenance entries, and the collapse
     * that produced it, if any.
     *
     * @param grounding  raw grounding references backing this proposition
     * @param provenance provenance entries describing where each piece of the proposition came from
     * @param collapse   the collapse that folded other memories into this one, if it was ever involved in one
     */
    record Lineage(List<String> grounding, List<ProvenanceRef> provenance, Optional<CollapseExplanation> collapse) {

        /**
         * One provenance entry: where a piece of the proposition came from, when, and any extra detail.
         *
         * @param source name or id of the source system or document
         * @param ref    a pointer into that source (timestamp, line, span, etc.)
         * @param detail short human-readable detail about this entry
         */
        public record ProvenanceRef(String source, String ref, String detail) {
        }
    }
}
