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

import java.time.Instant;
import java.util.List;

/**
 * Abstraction for document information retrieval across Embabel applications.
 * Implementations provide access to indexed document metadata, counts, and deletion.
 */
public interface DocumentInfoProvider {

    record DocumentInfo(String uri, String title, String context, int chunkCount, Instant ingestedAt) {}

    List<DocumentInfo> getDocuments();

    default List<DocumentInfo> getDocuments(String context) {
        return getDocuments();
    }

    boolean deleteDocument(String uri);

    int getDocumentCount();

    default int getDocumentCount(String context) {
        return getDocumentCount();
    }

    int getChunkCount();

    default int getChunkCount(String context) {
        return getChunkCount();
    }
}
