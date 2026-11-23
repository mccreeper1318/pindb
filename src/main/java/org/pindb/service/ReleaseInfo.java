package org.pindb.service;

import java.time.Instant;

public record ReleaseInfo(
        String tag,
        Version version,
        String name,
        String markdownNotes,
        String postUpdateMarkdownNotes,
        ReleasePackage packageAsset,
        boolean prerelease,
        Instant publishedAt
) {
}
