package net.vanbaelinghem.skylanders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads the real dumps under {@code src/test/resources/fixtures} together with the expected truth
 * stored beside them.
 *
 * <p>CLAUDE.md, "Tests" — parsing tests never run on invented bytes. A test built on synthetic
 * input validates the implementation against itself and proves nothing about the format.
 */
public final class Fixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Fixtures() {}

    public static byte[] dump(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name + ".sky")) {
            if (in == null) {
                throw new IllegalStateException("fixture absente : " + name + ".sky");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode truth(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name + ".json")) {
            if (in == null) {
                throw new IllegalStateException("verite attendue absente : " + name + ".json");
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
