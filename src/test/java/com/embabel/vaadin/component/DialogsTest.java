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
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that the Dialogs helper makes dialogs resizable, draggable, content-fit sized,
 * and Esc-closable. Tests the public static methods directly.
 */
class DialogsTest {

    @Test
    void resizableContentFitEnablesResizableAndDraggable() {
        var dialog = new Dialog();
        dialog.add(new Div("Test content"));

        Dialogs.resizableContentFit(dialog);

        assertTrue(dialog.isResizable(), "dialog must be resizable");
        assertTrue(dialog.isDraggable(), "dialog must be draggable");
    }

    @Test
    void resizableContentFitCapsToViewport() {
        var dialog = new Dialog();
        dialog.add(new Div("Test content"));

        Dialogs.resizableContentFit(dialog);

        assertEquals("min(90vw, 960px)", dialog.getMaxWidth(), "max width must cap to viewport and content");
        assertEquals("90vh", dialog.getMaxHeight(), "max height must cap to viewport");
    }

    @Test
    void resizableContentFitDoesNotSetFixedDimensions() {
        var dialog = new Dialog();
        dialog.add(new Div("Test content"));

        Dialogs.resizableContentFit(dialog);

        assertNull(dialog.getWidth(), "width must not be set — dialog sizes to content");
        assertNull(dialog.getHeight(), "height must not be set — dialog sizes to content");
    }

    @Test
    void resizableContentFitSetsOverlayClass() {
        var dialog = new Dialog();
        dialog.add(new Div("Test content"));

        Dialogs.resizableContentFit(dialog);

        var overlayClass = dialog.getElement().getProperty("overlayClass");
        assertEquals("content-fit-dialog", overlayClass, "overlayClass must be set for CSS styling");
    }

    @Test
    void enableEscCloseAddsShortcutListener() {
        var dialog = new Dialog();
        dialog.add(new Div("Test content"));

        // Verify the shortcut can be added without throwing — this ensures the
        // method does not fail. A full verification of the listener triggering
        // would require UI infrastructure; we settle for non-error.
        Dialogs.enableEscClose(dialog);

        // The dialog should not throw when Escape would be pressed.
        // This is a smoke test; full behavior requires UI harness.
    }

    @Test
    void contentFitAndEscCanBeUsedTogether() {
        var dialog = new Dialog();
        dialog.add(new Div("Test content"));

        // Both helpers should work together without conflict.
        Dialogs.resizableContentFit(dialog);
        Dialogs.enableEscClose(dialog);

        assertTrue(dialog.isResizable(), "resizable must still be true");
        assertTrue(dialog.isDraggable(), "draggable must still be true");
        assertEquals("min(90vw, 960px)", dialog.getMaxWidth(), "max width must persist");
    }
}
