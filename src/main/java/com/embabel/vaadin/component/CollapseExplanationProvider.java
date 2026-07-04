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
}
