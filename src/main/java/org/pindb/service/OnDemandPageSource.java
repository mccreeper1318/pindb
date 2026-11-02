package org.pindb.service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class OnDemandPageSource<T, R> {
    private final List<T> pages;
    private final Function<T, R> renderer;

    OnDemandPageSource(List<T> pages, Function<T, R> renderer) {
        this.pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    int size() {
        return pages.size();
    }

    R render(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            throw new IndexOutOfBoundsException("Page index " + pageIndex + " is out of range.");
        }
        return renderer.apply(pages.get(pageIndex));
    }
}
