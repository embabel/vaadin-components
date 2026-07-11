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

import com.embabel.agent.rag.model.NamedEntity;
import com.embabel.dice.proposition.PropositionRepository;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that the Analyze button is not permanently highlighted (LUMO_PRIMARY).
 * Proves the button renders with default styling at rest, matching the Learn button.
 */
class MemorySectionAnalyzeButtonTest {

    private NamedEntity createEntity() {
        var entity = mock(NamedEntity.class);
        when(entity.getId()).thenReturn("test-entity");
        when(entity.getName()).thenReturn("Test Entity");
        when(entity.getDescription()).thenReturn("Test Description");
        when(entity.labels()).thenReturn(Set.of("Test"));
        return entity;
    }

    private PropositionRepository mockRepo() {
        var repo = mock(PropositionRepository.class);
        when(repo.query(any())).thenReturn(List.of());
        return repo;
    }

    /**
     * Construct MemorySection with Analyze callback and verify that the Analyze button
     * does NOT carry the LUMO_PRIMARY theme variant at rest. This proves the button
     * is not permanently highlighted.
     */
    @Test
    void analyzeButtonNotHighlightedAtRest() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1", () -> {}, null, null);
            ui.add(memorySection);

            // Find the Analyze button
            var analyzeButton = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button)
                    .map(c -> (Button) c)
                    .filter(b -> "Analyze".equals(b.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected an Analyze button in MemorySection"));

            // Assert that LUMO_PRIMARY is NOT applied at rest
            assertFalse(
                    analyzeButton.getThemeNames().contains("primary"),
                    "Analyze button must not have LUMO_PRIMARY theme at rest");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Construct MemorySection with both Learn (onRemember) and Analyze callbacks.
     * Verify that at rest, both buttons carry the same variant set (consistency).
     */
    @Test
    void learnAndAnalyzeButtonsHaveSameVariantsAtRest() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1",
                    () -> {},  // onAnalyze
                    req -> {}, // onRemember
                    null);     // onClearContext
            ui.add(memorySection);

            // Find both buttons
            var buttons = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button)
                    .map(c -> (Button) c)
                    .toList();

            var learnButton = buttons.stream()
                    .filter(b -> "Learn".equals(b.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected a Learn button"));

            var analyzeButton = buttons.stream()
                    .filter(b -> "Analyze".equals(b.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("expected an Analyze button"));

            // Get the theme names for each button (sorted for comparison)
            var learnThemes = new ArrayList<>(learnButton.getThemeNames());
            var analyzeThemes = new ArrayList<>(analyzeButton.getThemeNames());
            learnThemes.sort(String::compareTo);
            analyzeThemes.sort(String::compareTo);

            assertTrue(
                    learnThemes.equals(analyzeThemes),
                    String.format("Learn and Analyze buttons must have same variants at rest. " +
                            "Learn: %s, Analyze: %s", learnThemes, analyzeThemes));
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Construct MemorySection with Analyze callback but no Learn button (onRemember is null).
     * Click the Analyze button and verify the callback is invoked. This tests that the button
     * wiring is still intact after removing the unconditional LUMO_PRIMARY.
     */
    @Test
    void analyzeButtonClickInvokesCallback() {
        var ui = withUi();
        try {
            var entity = createEntity();
            var repo = mockRepo();
            Function<String, NamedEntity> entityResolver = id -> entity;

            var callbackInvoked = new AtomicBoolean(false);
            var memorySection = new MemorySection(
                    repo, entityResolver, () -> "ctx-1",
                    () -> callbackInvoked.set(true),  // onAnalyze
                    null,  // onRemember (no Learn button)
                    null);
            ui.add(memorySection);

            // Find and click the Analyze button
            var analyzeButton = allComponents(memorySection).stream()
                    .filter(c -> c instanceof Button)
                    .map(c -> (Button) c)
                    .filter(b -> "Analyze".equals(b.getText()))
                    .findFirst()
                    .orElseThrow();

            analyzeButton.click();

            assertTrue(callbackInvoked.get(), "Analyze button click must invoke the callback");
        } finally {
            UI.setCurrent(null);
        }
    }

    /**
     * Set up a live {@link UI} so components can be added.
     */
    private static UI withUi() {
        var ui = new UI();
        var session = Mockito.mock(VaadinSession.class);
        Mockito.when(session.hasLock()).thenReturn(true);
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        return ui;
    }

    /**
     * Depth-first tree walk to collect all components under a root.
     */
    private static List<Component> allComponents(Component root) {
        var out = new ArrayList<Component>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        c.getChildren().forEach(child -> collect(child, out));
    }
}
