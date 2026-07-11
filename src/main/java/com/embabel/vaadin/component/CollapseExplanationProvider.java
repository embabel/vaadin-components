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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up why a memory was collapsed, so the Memory tab can explain a merge instead of just
 * showing the survivor. A host application implements this against whatever collector/dedup trace
 * it keeps; this component doesn't know or care where that data comes from.
 */
@FunctionalInterface
public interface CollapseExplanationProvider {

    /**
     * @param propositionId id of the proposition shown on the card
     * @return the collapse explanation for this proposition, if a decision folded other memories
     * into it; empty if this memory was never involved in a collapse
     */
    Optional<CollapseExplanation> explain(String propositionId);

    /**
     * Looks up collapse explanations for a whole batch of propositions in one go — the panel calls
     * this once per render instead of calling {@link #explain(String)} once per card, so a host with
     * a real batch query (a single Cypher lookup, say) can avoid one round trip per memory shown.
     * The default just loops over {@link #explain(String)}, so existing providers keep working
     * unchanged until they choose to override this.
     *
     * @param propositionIds ids of the propositions being rendered
     * @return a map from id to explanation, containing only ids that actually had a collapse
     */
    default Map<String, CollapseExplanation> explainAll(Collection<String> propositionIds) {
        var result = new LinkedHashMap<String, CollapseExplanation>();
        for (var id : propositionIds) {
            explain(id).ifPresent(explanation -> result.put(id, explanation));
        }
        return result;
    }
}
