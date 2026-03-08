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

import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Composite panel combining file upload, URL ingestion, and document listing
 * into a single component. Use this instead of managing three separate sections.
 */
public class DocumentsPanel extends VerticalLayout {

    private final DocumentListSection documentsSection;

    /**
     * @param documentInfoProvider provides document metadata for listing
     * @param contextSupplier      optional context supplier (null for no-context)
     * @param onIngestStream       called with (InputStream, filename) when a file is uploaded
     * @param onIngestUrl          called with URL string when a URL is submitted
     * @param onDocumentsChanged   callback after any document change (upload, ingest, delete)
     */
    public DocumentsPanel(DocumentInfoProvider documentInfoProvider,
                          Supplier<String> contextSupplier,
                          BiConsumer<InputStream, String> onIngestStream,
                          Consumer<String> onIngestUrl,
                          Runnable onDocumentsChanged) {
        setPadding(false);
        setSpacing(true);

        documentsSection = new DocumentListSection(documentInfoProvider, contextSupplier, onDocumentsChanged);

        Runnable onSuccess = () -> {
            documentsSection.refresh();
            onDocumentsChanged.run();
        };

        var uploadSection = new FileUploadSection(onIngestStream, onSuccess);
        var urlSection = new UrlIngestSection(onIngestUrl, onSuccess);

        add(uploadSection, urlSection, documentsSection);
    }

    public void refresh() {
        documentsSection.refresh();
    }
}
