package com.back.common.api;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 응답. 무한 스크롤형 목록은 커서, 표 형태 화면은 페이지 번호를 쓴다 (D-12).
 */
public final class PageDto {

    private PageDto() {
    }

    /**
     * 커서 목록. {@code nextCursor}가 null이면 마지막 페이지다.
     */
    public record CursorRes<T>(List<T> items, Long nextCursor) {

        /**
         * {@code size + 1}건을 조회한 결과에서 한 페이지를 자른다. 남는 1건이 있으면 다음 페이지가 있다.
         */
        public static <T> CursorRes<T> of(List<T> fetched, int size, Function<T, Long> cursorOf) {
            if (fetched.size() <= size) {
                return new CursorRes<>(fetched, null);
            }
            List<T> page = fetched.subList(0, size);
            return new CursorRes<>(List.copyOf(page), cursorOf.apply(page.getLast()));
        }
    }

    /**
     * 페이지 번호 목록. {@code page}는 1부터 센다.
     */
    public record PageRes<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageRes<T> of(List<T> content, int page, int size, long totalElements) {
            int totalPages = (int) ((totalElements + size - 1) / size);
            return new PageRes<>(content, page, size, totalElements, totalPages);
        }
    }
}
