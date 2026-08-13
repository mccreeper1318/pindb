package org.pindb.service;

import java.time.Instant;
import java.util.Objects;

public record ReleaseNote(String tag, String title, String markdown, boolean prerelease,
                          Instant publishedAt, boolean bundled) {
    public ReleaseNote {
        tag = Objects.requireNonNullElse(tag, "").trim();
        title = Objects.requireNonNullElse(title, "").trim();
        markdown = Objects.requireNonNullElse(markdown, "");
        publishedAt = Objects.requireNonNullElse(publishedAt, Instant.EPOCH);
    }

    public String displayName() {
        return tag + (prerelease ? "  •  Pre-release" : "");
    }
}
