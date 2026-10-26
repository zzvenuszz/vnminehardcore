package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NpcDefinitionLoaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsBundledNpcArchetypesAndDialogue() throws Exception {
        var resource = getClass().getClassLoader().getResource("npcs/npc-definitions.yml");
        assertTrue(resource != null);

        var definitions = NpcDefinitionLoader.load(Path.of(resource.toURI()).toFile(),
            Logger.getAnonymousLogger()).orElseThrow();

        assertEquals(7, definitions.size());
        assertEquals("tutorial", definitions.get("tutorial-guide").type());
        assertEquals("Người dẫn đường", definitions.get("tutorial-guide").displayName());
        assertEquals(2, definitions.get("tutorial-guide").dialogue().size());
        assertEquals("open-quest-journal", definitions.get("tutorial-guide").actions().getFirst().type());
        assertEquals("trainer", definitions.get("class-trainer").type());
        assertEquals("open-skill-tree", definitions.get("class-trainer").actions().getFirst().type());
        assertEquals("story", definitions.get("story-keeper").type());
    }

    @Test
    public void rejectsUnsupportedSchemaVersion() throws IOException {
        File file = writeConfig("""
            schema-version: 2
            npc-definitions: {}
            """);

        assertTrue(NpcDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void rejectsUnknownNpcType() throws IOException {
        File file = writeConfig(definition("teleporter"));

        assertTrue(NpcDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void rejectsMalformedIdsAndEmptyDialogueLines() throws IOException {
        File invalidId = writeConfig("""
            schema-version: 1
            npc-definitions:
              Invalid_ID:
                type: tutorial
                display-name: Guide
                dialogue: ["Hello"]
            """);
        File invalidDialogue = writeConfig("""
            schema-version: 1
            npc-definitions:
              guide:
                type: tutorial
                display-name: Guide
                dialogue: [" "]
            """);

        assertTrue(NpcDefinitionLoader.load(invalidId, Logger.getAnonymousLogger()).isEmpty());
        assertTrue(NpcDefinitionLoader.load(invalidDialogue, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void loadsSkillBookActionsAndRejectsUnknownActionTypes() throws IOException {
        File valid = writeConfig("""
            schema-version: 1
            npc-definitions:
              trainer:
                type: trainer
                display-name: Trainer
                dialogue: [Hello]
                actions:
                  - type: give-skill-book
                    label: "Learn Blade Flare"
                    book-id: blade-flare-manual
            """);
        File invalid = writeConfig("""
            schema-version: 1
            npc-definitions:
              trainer:
                type: trainer
                display-name: Trainer
                dialogue: [Hello]
                actions:
                  - type: run-command
                    label: "Run command"
            """);

        var action = NpcDefinitionLoader.load(valid, Logger.getAnonymousLogger()).orElseThrow()
            .get("trainer").actions().getFirst();
        assertEquals("give-skill-book", action.type());
        assertEquals("blade-flare-manual", action.bookId());
        assertTrue(NpcDefinitionLoader.load(invalid, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void rejectsDuplicateYamlKeysAndOversizedDialogue() throws IOException {
        File duplicateKeys = writeConfig("""
            schema-version: 1
            npc-definitions:
              guide:
                type: tutorial
                display-name: Guide
                display-name: Replaced
                dialogue: ["Hello"]
            """);
        File oversizedDialogue = writeConfig("""
            schema-version: 1
            npc-definitions:
              guide:
                type: tutorial
                display-name: Guide
                dialogue: ["%s"]
            """.formatted("x".repeat(257)));

        assertTrue(NpcDefinitionLoader.load(duplicateKeys, Logger.getAnonymousLogger()).isEmpty());
        assertTrue(NpcDefinitionLoader.load(oversizedDialogue, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void enforcesMaximumDefinitionCount() throws IOException {
        StringBuilder hundredDefinitions = new StringBuilder("schema-version: 1\nnpc-definitions:\n");
        StringBuilder oneHundredOneDefinitions = new StringBuilder("schema-version: 1\nnpc-definitions:\n");
        for (int index = 0; index < 101; index++) {
            String entry = """
                  guide-%d:
                    type: tutorial
                    display-name: Guide
                    dialogue: ["Hello"]
                """.formatted(index);
            oneHundredOneDefinitions.append(entry);
            if (index < 100) hundredDefinitions.append(entry);
        }

        var valid = NpcDefinitionLoader.load(writeConfig(hundredDefinitions.toString()),
            Logger.getAnonymousLogger()).orElseThrow();
        assertEquals(100, valid.size());
        assertTrue(NpcDefinitionLoader.load(writeConfig(oneHundredOneDefinitions.toString()),
            Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void acceptsMaximumDisplayNameAndDialogueBounds() throws IOException {
        String dialogueLines = "      - \"" + "x".repeat(256) + "\"\n"
            + String.join("\n", Collections.nCopies(19, "      - \"x\""));
        File valid = writeConfig("""
            schema-version: 1
            npc-definitions:
              guide:
                type: tutorial
                display-name: "%s"
                dialogue:
            %s
            """.formatted("x".repeat(64), dialogueLines));
        File tooLongName = writeConfig(definition("tutorial").replace("display-name: Guide",
            "display-name: " + "x".repeat(65)));

        var definition = NpcDefinitionLoader.load(valid, Logger.getAnonymousLogger()).orElseThrow()
            .get("guide");
        assertEquals(64, definition.displayName().length());
        assertEquals(20, definition.dialogue().size());
        assertEquals(256, definition.dialogue().getFirst().length());
        assertTrue(NpcDefinitionLoader.load(tooLongName, Logger.getAnonymousLogger()).isEmpty());
    }

    private File writeConfig(String content) throws IOException {
        File file = temporaryFolder.newFile("npcs-" + System.nanoTime() + ".yml");
        Files.writeString(file.toPath(), content);
        return file;
    }

    private String definition(String type) {
        return """
            schema-version: 1
            npc-definitions:
              guide:
                type: %s
                display-name: Guide
                dialogue: ["Hello"]
            """.formatted(type);
    }
}
