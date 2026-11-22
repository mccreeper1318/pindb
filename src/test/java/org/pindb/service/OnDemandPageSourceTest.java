package org.pindb.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class OnDemandPageSourceTest {
    @Test
    void doesNotRenderMultipageDocumentUntilPageIsRequested() {
        AtomicInteger renders = new AtomicInteger();
        List<Integer> pages = java.util.stream.IntStream.range(0, 100).boxed().toList();

        OnDemandPageSource<Integer, String> source = new OnDemandPageSource<>(pages, page -> {
            renders.incrementAndGet();
            return "page-" + page;
        });

        assertEquals(100, source.size());
        assertEquals(0, renders.get());
        assertEquals("page-57", source.render(57));
        assertEquals(1, renders.get());
    }

    @Test
    void doesNotCacheOrRetainRenderedPageResults() {
        AtomicInteger renders = new AtomicInteger();
        OnDemandPageSource<Integer, Object> source = new OnDemandPageSource<>(
                List.of(0, 1, 2),
                ignored -> {
                    renders.incrementAndGet();
                    return new Object();
                });

        Object firstRender = source.render(1);
        Object secondRender = source.render(1);

        assertNotSame(firstRender, secondRender);
        assertEquals(2, renders.get());
    }
}
