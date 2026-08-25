package net.vanbaelinghem.skylanders.api;

import java.util.Map;

/**
 * A caveat the UI must show, expressed as a code rather than a sentence.
 *
 * <p>The server used to return ready-made French sentences. That made the API monolingual and put
 * wording decisions on the wrong side of the wire: the interface knows which language it is being
 * read in, the server does not. It now names the situation and supplies the values; the client
 * turns that into a sentence.
 */
public record Notice(String code, Map<String, Object> params) {

    public static Notice of(String code) {
        return new Notice(code, Map.of());
    }

    public static Notice of(String code, Map<String, Object> params) {
        return new Notice(code, params);
    }
}
