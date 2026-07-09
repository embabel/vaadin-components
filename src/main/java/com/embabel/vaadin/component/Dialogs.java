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

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.dialog.Dialog;

/**
 * Shared static helpers for modal dialogs — making them resizable, draggable,
 * and sizing to content with viewport caps and Esc-close support.
 */
public final class Dialogs {

    private Dialogs() {
        // Utility class, no instances.
    }

    /**
     * Make a modal dialog resizable and draggable, opening at content-fit size
     * capped to the viewport. The dialog sizes to its content; use this helper
     * to cap max dimensions and apply visual styling (via overlayClass).
     * <p>
     * The dialog is NOT given fixed width/height — it sizes based on its
     * content and layout. The maxWidth and maxHeight set caps so oversized
     * content still fits the viewport.
     *
     * @param d the dialog to configure
     */
    public static void resizableContentFit(Dialog d) {
        d.setResizable(true);
        d.setDraggable(true);
        // Content-fit: do NOT set width/height — let dialog size to content.
        // Only set max-width/max-height to cap oversized content.
        d.setMaxWidth("min(90vw, 960px)");
        d.setMaxHeight("90vh");
        // Apply overlay class for padding and styling via CSS.
        d.getElement().setProperty("overlayClass", "content-fit-dialog");
    }

    /**
     * Enable Escape key to close the dialog, even when focus is outside the overlay.
     * <p>
     * The built-in {@link Dialog#setCloseOnEsc(boolean)} only fires when focus is
     * inside the overlay. This helper uses a UI-scoped {@link Shortcuts} listener
     * to catch Esc regardless of focus location, matching the user's expectation
     * that Esc always closes the modal.
     *
     * @param d the dialog to configure
     */
    public static void enableEscClose(Dialog d) {
        Shortcuts.addShortcutListener(d, d::close, Key.ESCAPE);
    }
}
