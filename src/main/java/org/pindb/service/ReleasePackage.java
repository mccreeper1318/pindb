package org.pindb.service;

import org.pindb.platform.LinuxPackageType;
import org.pindb.platform.SystemArchitecture;

import java.net.URI;
import java.util.Objects;

public record ReleasePackage(
        LinuxPackageType type,
        SystemArchitecture architecture,
        String fileName,
        URI downloadUri,
        URI checksumUri
) {
    public ReleasePackage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(architecture, "architecture");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(downloadUri, "downloadUri");
    }
}
