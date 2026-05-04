package com.brainbyte.easy_maintenance.commons.dto;

import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Unified pagination response supporting both cursor-based and OFFSET pagination.
 *
 * <p>In cursor mode: {@code totalElements=-1}, {@code totalPages=-1}, {@code number=-1}.
 * In OFFSET mode: all fields are populated; {@code nextCursor} is set to the ID of the
 * last item in the page so callers can switch to cursor mode for subsequent requests.
 */
public record CursorPageResponse<T>(
        List<T> content,
        Long nextCursor,
        Long prevCursor,
        boolean hasMore,
        int size,
        long totalElements,
        int totalPages,
        int number
) {
    /**
     * Wraps a Spring Data {@link Page} result (OFFSET mode).
     * {@code nextCursor} is populated from the last item's ID so the frontend can
     * switch to cursor mode on the next request.
     */
    public static <T> CursorPageResponse<T> ofOffset(Page<T> page, Function<T, Long> idExtractor) {
        Long nextCursor = page.hasNext() && !page.getContent().isEmpty()
                ? idExtractor.apply(page.getContent().get(page.getContent().size() - 1))
                : null;
        return new CursorPageResponse<>(
                page.getContent(),
                nextCursor,
                null,
                page.hasNext(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber()
        );
    }

    /**
     * Wraps a cursor-mode result set.
     * {@code totalElements}, {@code totalPages}, and {@code number} are set to {@code -1}
     * as sentinel values (count queries are skipped in cursor mode for performance).
     */
    public static <T> CursorPageResponse<T> ofCursor(List<T> content, Long nextCursor,
                                                      Long prevCursor, boolean hasMore, int size) {
        return new CursorPageResponse<>(
                content,
                nextCursor,
                prevCursor,
                hasMore,
                size,
                -1L,
                -1,
                -1
        );
    }
}
