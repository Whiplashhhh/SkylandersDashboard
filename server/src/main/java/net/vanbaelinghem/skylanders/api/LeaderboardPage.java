package net.vanbaelinghem.skylanders.api;

import java.util.List;

/**
 * @param sortableFields the whitelist the UI builds its headers from, so the client can never ask
 *                       for a column that does not exist
 */
public record LeaderboardPage(
        List<LeaderboardRow> rows,
        int total,
        int page,
        int size,
        int pageCount,
        String sort,
        String direction,
        List<String> sortableFields) {}
