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
package com.embabel.vaadin.document;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Document cards surface the ingestion pipeline's work: a "structured" badge
 * when a converter (e.g. docling) produced the document's markdown, and a
 * figure-count badge when figures were extracted and indexed. A plain document
 * shows neither. The five-arg {@link DocumentInfoProvider.DocumentInfo}
 * constructor keeps old callers compiling with no badges.
 */
class DocumentListSectionBadgesTest {

    private DocumentListSection sectionFor(List<DocumentInfoProvider.DocumentInfo> docs) {
        var provider = new DocumentInfoProvider() {
            @Override public List<DocumentInfo> getDocuments() { return docs; }
            @Override public boolean deleteDocument(String uri) { return true; }
            @Override public int getDocumentCount() { return docs.size(); }
            @Override public int getChunkCount() { return 0; }
        };
        var section = new DocumentListSection(provider, null, () -> { });
        section.refresh();
        return section;
    }

    private Stream<Span> spans(Component root) {
        return root.getChildren().flatMap(c ->
                Stream.concat(c instanceof Span s ? Stream.of(s) : Stream.empty(), spans(c)));
    }

    private List<String> badgeTexts(Component root, String className) {
        return spans(root).filter(s -> s.hasClassName(className)).map(Span::getText).toList();
    }

    @Test
    void convertedDocumentShowsStructuredAndFigureBadges() {
        var section = sectionFor(List.of(new DocumentInfoProvider.DocumentInfo(
                "upload://q.pdf", "Quarterly Review", "ws", 5, Instant.now(), "docling", 2)));

        assertEquals(List.of("structured · docling"), badgeTexts(section, "converted-badge"));
        assertEquals(List.of("2 figures"), badgeTexts(section, "figures-badge"));
    }

    @Test
    void singleFigureBadgeIsSingular() {
        var section = sectionFor(List.of(new DocumentInfoProvider.DocumentInfo(
                "upload://q.pdf", "Q", "ws", 5, Instant.now(), "docling", 1)));

        assertEquals(List.of("1 figure"), badgeTexts(section, "figures-badge"));
    }

    @Test
    void plainDocumentShowsNeitherBadge() {
        var section = sectionFor(List.of(new DocumentInfoProvider.DocumentInfo(
                "file:///notes.md", "Notes", "ws", 3, Instant.now())));

        assertTrue(badgeTexts(section, "converted-badge").isEmpty());
        assertTrue(badgeTexts(section, "figures-badge").isEmpty());
        // The context badge is still there.
        assertEquals(List.of("ws"), badgeTexts(section, "context-badge").stream()
                .filter("ws"::equals).toList());
    }
}
