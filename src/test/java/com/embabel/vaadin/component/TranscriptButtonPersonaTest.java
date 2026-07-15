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

import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import com.embabel.chat.support.InMemoryConversation;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transcript must label assistant messages with the assistant's IDENTITY name
 * (e.g. "Em", "Marvin"), not a hardcoded "Assistant" — embabel/me#460. The chat
 * bubbles already show the persona; the transcript (dialog + copied text) must match.
 */
class TranscriptButtonPersonaTest {

    private static Conversation conversation() {
        return new InMemoryConversation(List.of(
                new UserMessage("hello there"),
                new AssistantMessage("hi, how can I help?")
        ));
    }

    private static List<String> htmlLabels(TranscriptButton button, Conversation conversation) {
        var container = new Div();
        button.buildHtmlTranscript(conversation, container);
        return container.getChildren()
                .map(entry -> entry.getChildren().findFirst().orElseThrow())
                .map(span -> ((Span) span).getText())
                .toList();
    }

    @Test
    void plainTranscriptLabelsAssistantMessagesWithTheIdentityName() {
        var button = new TranscriptButton(TranscriptButtonPersonaTest::conversation, "Em");
        var text = button.buildPlainTranscript(conversation());
        assertTrue(text.contains("You: hello there"), "user label unchanged: " + text);
        assertTrue(text.contains("Em: hi, how can I help?"), "assistant labelled by identity name: " + text);
        assertFalse(text.contains("Assistant:"), "no hardcoded Assistant label: " + text);
    }

    @Test
    void htmlTranscriptLabelsAssistantMessagesWithTheIdentityName() {
        var button = new TranscriptButton(TranscriptButtonPersonaTest::conversation, "Em");
        assertEquals(List.of("You", "Em"), htmlLabels(button, conversation()));
    }

    @Test
    void singleArgConstructorKeepsTheAssistantFallbackLabel() {
        var button = new TranscriptButton(TranscriptButtonPersonaTest::conversation);
        assertTrue(button.buildPlainTranscript(conversation()).contains("Assistant: hi"));
        assertEquals(List.of("You", "Assistant"), htmlLabels(button, conversation()));
    }

    @Test
    void blankOrNullNameFallsBackToAssistant() {
        var blank = new TranscriptButton(TranscriptButtonPersonaTest::conversation, "  ");
        assertTrue(blank.buildPlainTranscript(conversation()).contains("Assistant: hi"));
        var nul = new TranscriptButton(TranscriptButtonPersonaTest::conversation, null);
        assertTrue(nul.buildPlainTranscript(conversation()).contains("Assistant: hi"));
    }
}
