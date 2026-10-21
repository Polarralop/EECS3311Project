package ca.yorku.storyteller.persistence;

import ca.yorku.storyteller.model.StorySession;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Stores each session as one JSON file, {@code <id>.json}, in the save directory.
 *
 * <p>Jackson reads and writes the model's fields directly, so the model classes need no
 * annotations; the observer list is transient and is never written.
 *
 * <p>Features: F11.
 */
public class JsonSessionRepository implements SessionRepository {

    private static final String EXTENSION = ".json";
    /** Ids become file names, so they are limited to safe characters. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    private final Path directory;
    private final ObjectMapper mapper;

    public JsonSessionRepository(Path directory) {
        this.directory = directory;
        this.mapper = new ObjectMapper()
                .setVisibility(PropertyAccessor.ALL, Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                .setVisibility(PropertyAccessor.CREATOR, Visibility.ANY)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public void save(StorySession session) {
        Path target = fileFor(session.getId());
        try {
            Files.createDirectories(directory);
            // Write to a temporary file first so a failed save never leaves a half-written story.
            Path temp = Files.createTempFile(directory, session.getId(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), session);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the story to " + target + ": " + e.getMessage(), e);
        }
    }

    @Override
    public StorySession load(String id) {
        Path file = fileFor(id);
        try {
            StorySession session = mapper.readValue(Files.readAllBytes(file), StorySession.class);
            if (session == null || session.getId() == null || !session.getId().equals(id)) {
                throw new UncheckedIOException(new IOException("The save file for " + id + " is damaged."));
            }
            return session;
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("There is no saved story called " + id + ".");
        } catch (JacksonException e) {
            throw new UncheckedIOException("The save file for " + id + " is damaged and cannot be loaded.", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the save file for " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listSessions() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> saves = new ArrayList<>(files
                    .filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .filter(p -> SAFE_ID.matcher(idOf(p)).matches())
                    .toList());
            saves.sort(Comparator.comparing(JsonSessionRepository::modified).reversed());
            return saves.stream().map(JsonSessionRepository::idOf).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list the saved stories: " + e.getMessage(), e);
        }
    }

    private Path fileFor(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("\"" + id + "\" is not a valid story id.");
        }
        return directory.resolve(id + EXTENSION);
    }

    private static String idOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - EXTENSION.length());
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
