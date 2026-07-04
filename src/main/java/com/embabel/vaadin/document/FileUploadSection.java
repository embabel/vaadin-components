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

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * File upload section for documents.
 * Accepts an {@link IngestHandler} of (InputStream, filename, fromOrg) for ingestion — the optional
 * "From" org/domain captures PROVENANCE the file itself can't carry (a downloaded PDF has no origin host).
 */
public class FileUploadSection extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadSection.class);

    /** Ingest callback carrying the uploaded stream, its filename, and the (possibly blank) source org/domain. */
    @FunctionalInterface
    public interface IngestHandler {
        void accept(InputStream stream, String filename, String fromOrg);
    }

    public FileUploadSection(IngestHandler onIngest, Runnable onSuccess) {
        setPadding(true);
        setSpacing(true);

        var instructions = new Span("Upload documents to add to the knowledge base");
        instructions.addClassName("section-instructions");

        // Optional provenance: which organization / domain this document is from (a downloaded report has no
        // origin URL, so the user tags it here → (:Document)-[:PUBLISHED_BY]->(:Organization)).
        var fromField = new TextField("From (organization or domain)");
        fromField.setPlaceholder("e.g. acme.com");
        fromField.setClearButtonVisible(true);
        fromField.setWidthFull();
        fromField.setId("upload-from-org");

        var buffer = new MemoryBuffer();
        var upload = new Upload(buffer);
        upload.setWidthFull();
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

        upload.addSucceededListener(event -> {
            var filename = event.getFileName();
            try {
                var inputStream = buffer.getInputStream();
                onIngest.accept(inputStream, filename, fromField.getValue());

                Notification.show("Uploaded: " + filename, 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                onSuccess.run();
            } catch (Exception e) {
                logger.error("Failed to ingest file: {}", filename, e);
                Notification.show("Error: " + e.getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFailedListener(event -> {
            logger.error("Upload failed: {}", event.getReason().getMessage());
            Notification.show("Upload failed: " + event.getReason().getMessage(), 5000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        add(instructions, fromField, upload);
    }
}
