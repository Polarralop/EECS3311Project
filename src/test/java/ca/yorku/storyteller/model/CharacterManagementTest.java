package ca.yorku.storyteller.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for character management (F03) in {@link StorySession}, {@link Character} and {@link Relationship}. */
class CharacterManagementTest {

    private StorySession session;

    @BeforeEach
    void setUp() {
        session = new StorySession("test");
        session.addCharacter(new Character("Mira", "A smith's apprentice.", List.of("stubborn", " ")));
        session.addCharacter(new Character("Tomas", "Her older brother.", List.of()));
    }

    @Test
    void addAndFindCharacterIgnoringCase() {
        assertEquals("Mira", session.findCharacter("mira").orElseThrow().getName());
        assertEquals(List.of("stubborn"), session.findCharacter("MIRA").orElseThrow().getTraits());
        assertTrue(session.findCharacter("Nobody").isEmpty());
    }

    @Test
    void emptyNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Character(" ", "x", List.of()));
    }

    @Test
    void duplicateNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> session.addCharacter(new Character("MIRA", "Another Mira.", List.of())));
        assertEquals(2, session.getCharacters().size());
    }

    @Test
    void relationshipToExistingCharacterIsStoredOneWay() {
        session.addRelationship("Mira", new Relationship("Tomas", "sister", "protective"));
        assertEquals("sister", session.findCharacter("Mira").orElseThrow().relationshipTo("tomas").orElseThrow().getType());
        assertTrue(session.findCharacter("Tomas").orElseThrow().getRelationships().isEmpty());
    }

    @Test
    void relationshipToMissingCharacterIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> session.addRelationship("Mira", new Relationship("Ghost", "friend", "")));
        assertThrows(IllegalArgumentException.class,
                () -> session.addRelationship("Ghost", new Relationship("Mira", "friend", "")));
    }

    @Test
    void relationshipToSelfIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> session.addRelationship("Mira", new Relationship("mira", "rival", "")));
    }

    @Test
    void relationshipNeedsTargetAndType() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship("", "friend", ""));
        assertThrows(IllegalArgumentException.class, () -> new Relationship("Mira", " ", ""));
    }

    @Test
    void newCharacterWithInvalidRelationshipIsRejected() {
        Character c = new Character("Ines", "", List.of());
        c.addRelationship(new Relationship("Ghost", "friend", ""));
        assertThrows(IllegalArgumentException.class, () -> session.addCharacter(c));
        assertTrue(session.findCharacter("Ines").isEmpty());
    }

    @Test
    void addingSecondRelationshipToSameTargetReplacesIt() {
        session.addRelationship("Mira", new Relationship("Tomas", "sister", ""));
        session.addRelationship("Mira", new Relationship("Tomas", "rival", ""));
        Character mira = session.findCharacter("Mira").orElseThrow();
        assertEquals(1, mira.getRelationships().size());
        assertEquals("rival", mira.getRelationships().get(0).getType());
    }

    @Test
    void updateEditsCharacterAndRenameFollowsRelationships() {
        session.addRelationship("Mira", new Relationship("Tomas", "sister", ""));
        Character edited = session.findCharacter("Tomas").orElseThrow().copy();
        edited.setName("Tomas the Elder");
        edited.setDescription("Now a guard captain.");
        session.updateCharacter("Tomas", edited);

        assertTrue(session.findCharacter("Tomas").isEmpty());
        assertEquals("Now a guard captain.", session.findCharacter("Tomas the Elder").orElseThrow().getDescription());
        assertEquals("Tomas the Elder",
                session.findCharacter("Mira").orElseThrow().getRelationships().get(0).getTargetName());
    }

    @Test
    void updateCannotTakeAnotherCharactersName() {
        Character edited = session.findCharacter("Tomas").orElseThrow().copy();
        edited.setName("mira");
        assertThrows(IllegalArgumentException.class, () -> session.updateCharacter("Tomas", edited));
        assertTrue(session.findCharacter("Tomas").isPresent());
    }

    @Test
    void updateWithInvalidRelationshipLeavesOriginal() {
        Character edited = session.findCharacter("Tomas").orElseThrow().copy();
        edited.addRelationship(new Relationship("Ghost", "friend", ""));
        assertThrows(IllegalArgumentException.class, () -> session.updateCharacter("Tomas", edited));
        assertTrue(session.findCharacter("Tomas").orElseThrow().getRelationships().isEmpty());
    }

    @Test
    void updateUnknownCharacterFails() {
        assertThrows(IllegalArgumentException.class,
                () -> session.updateCharacter("Ghost", new Character("Ghost", "", List.of())));
    }

    @Test
    void removeCharacterAlsoRemovesRelationshipsPointingAtIt() {
        session.addRelationship("Mira", new Relationship("Tomas", "sister", ""));
        session.removeCharacter("tomas");
        assertTrue(session.findCharacter("Tomas").isEmpty());
        assertTrue(session.findCharacter("Mira").orElseThrow().getRelationships().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> session.removeCharacter("Tomas"));
    }

    @Test
    void describeIncludesTraitsAndRelationships() {
        session.addRelationship("Mira", new Relationship("Tomas", "sister", "protective"));
        String text = session.findCharacter("Mira").orElseThrow().describe();
        assertTrue(text.startsWith("Mira: A smith's apprentice."));
        assertTrue(text.contains("Traits: stubborn"));
        assertTrue(text.contains("sister of Tomas (protective)"));
    }

    @Test
    void copyIsIndependent() {
        Character copy = session.findCharacter("Mira").orElseThrow().copy();
        copy.setDescription("changed");
        assertFalse(session.findCharacter("Mira").orElseThrow().getDescription().equals("changed"));
    }
}
