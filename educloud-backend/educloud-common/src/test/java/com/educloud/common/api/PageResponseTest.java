package com.educloud.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void calculatesCeilingPagesAndCopiesItems() {
        var source = new ArrayList<>(List.of("a", "b"));

        var page = PageResponse.of(source, 2, 2, 5);
        source.add("c");

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.totalPages()).isEqualTo(3);
        assertThatThrownBy(() -> page.items().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void handlesEmptyAndEvenlyDivisibleTotals() {
        assertThat(PageResponse.of(List.of(), 1, 20, 0).totalPages()).isZero();
        assertThat(PageResponse.of(List.of("a"), 1, 2, 4).totalPages()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidPaginationInputs() {
        assertThatThrownBy(() -> PageResponse.of(List.of(), 0, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> PageResponse.of(List.of(), 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
        assertThatThrownBy(() -> PageResponse.of(List.of(), 1, 10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");
        assertThatThrownBy(() -> PageResponse.of(null, 1, 10, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAnInconsistentExplicitTotalPageCount() {
        assertThatThrownBy(() -> new PageResponse<>(List.of("a"), 1, 10, 11, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalPages");
    }
}
