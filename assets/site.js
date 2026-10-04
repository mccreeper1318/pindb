(function () {
  "use strict";

  const repo = "mccreeper1318/pindb";
  const releasesUrl = `https://github.com/${repo}/releases`;
  const api = `https://api.github.com/repos/${repo}/releases?per_page=30`;

  function byId(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    const node = byId(id);
    if (node) node.textContent = value;
  }

  function findAsset(release, predicate) {
    if (!release || !Array.isArray(release.assets)) return null;
    return release.assets.find(asset => predicate(asset.name.toLowerCase())) || null;
  }

  function choosePackage(release, kind) {
    switch (kind) {
      case "windows-x64":
        return findAsset(release, name => name.endsWith("-windows-x64.exe"));
      case "macos-arm64":
        return findAsset(release, name => name.endsWith("-macos-arm64.pkg"));
      case "macos-x64":
        return findAsset(release, name => name.endsWith("-macos-x64.pkg"));
      case "deb-x64":
        return findAsset(release, name => name.endsWith(".deb") && /(?:amd64|x86_64)/.test(name));
      case "rpm-x64":
        return findAsset(release, name => name.endsWith(".rpm") && /(?:x86_64|amd64)/.test(name));
      default:
        return null;
    }
  }

  function platformAggregate(kind) {
    if (kind.startsWith("macos-")) return "checksums-macos.sha256";
    if (kind.startsWith("windows-")) return "checksums-windows.sha256";
    return "checksums-linux.sha256";
  }

  function chooseChecksum(release, packageAsset, kind) {
    if (!release || !packageAsset) return null;
    const exact = `${packageAsset.name}.sha256`.toLowerCase();
    return findAsset(release, name => name === exact) ||
      findAsset(release, name => name === platformAggregate(kind)) ||
      findAsset(release, name => name === "checksums.sha256");
  }

  function makeUnavailable(link, text) {
    if (!link) return;
    link.removeAttribute("href");
    link.textContent = text;
    link.classList.add("unavailable");
    link.setAttribute("aria-disabled", "true");
  }

  function setAssetLink(prefix, release, kind, label) {
    const packageLink = byId(`${prefix}-${kind}`);
    const checksumLink = byId(`${prefix}-${kind}-checksum`);
    if (!packageLink && !checksumLink) return;

    const asset = choosePackage(release, kind);
    if (!asset) {
      makeUnavailable(packageLink, `${label} not published for this release`);
      makeUnavailable(checksumLink, "Checksum unavailable");
      return;
    }

    if (packageLink) {
      packageLink.href = asset.browser_download_url;
      packageLink.textContent = `Download ${asset.name}`;
      packageLink.classList.remove("unavailable");
      packageLink.removeAttribute("aria-disabled");
    }

    const checksum = chooseChecksum(release, asset, kind);
    if (!checksum) {
      makeUnavailable(checksumLink, `No checksum published for ${asset.name}`);
    } else if (checksumLink) {
      checksumLink.href = checksum.browser_download_url;
      checksumLink.textContent = `Checksum: ${checksum.name}`;
      checksumLink.classList.remove("unavailable");
      checksumLink.removeAttribute("aria-disabled");
    }
  }

  function fillRelease(prefix, release) {
    if (!release) {
      setText(`${prefix}-version`, "not published");
    } else {
      setText(`${prefix}-version`, release.tag_name);
    }

    const platforms = [
      ["windows-x64", "Windows x64 installer"],
      ["macos-arm64", "Apple Silicon PKG"],
      ["macos-x64", "Intel Mac PKG"],
      ["deb-x64", "Debian x86-64 package"],
      ["rpm-x64", "Fedora x86-64 package"]
    ];

    platforms.forEach(([kind, label]) => setAssetLink(prefix, release, kind, label));
  }

  async function loadReleases() {
    const status = byId("release-status");
    try {
      const response = await fetch(api, {
        headers: {
          "Accept": "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28"
        }
      });
      if (!response.ok) throw new Error(`GitHub returned ${response.status}`);

      const releases = (await response.json()).filter(release => !release.draft);
      const stable = releases.find(release => !release.prerelease) || null;
      const preview = releases.find(release => release.prerelease) || null;

      fillRelease("stable", stable);
      fillRelease("preview", preview);

      if (status) {
        status.textContent = "Live release information loaded from GitHub. Buttons are enabled only when the matching native package and published release asset are present.";
      }
    } catch (error) {
      if (status) {
        status.textContent = `Live release lookup is unavailable. Visit ${releasesUrl} to view all published packages.`;
      }
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (document.querySelector("[data-release-page]")) loadReleases();
  });
}());
