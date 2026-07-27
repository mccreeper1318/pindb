package org.pindb.service;

import java.net.URI;
import java.time.Instant;

public record ReleaseInfo(
        String tag,
        Version version,
        String name,
        String markdownNotes,
        URI debAsset,
        URI checksumAsset,
        boolean prerelease,
        Instant publishedAt
) {
}
