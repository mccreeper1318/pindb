(function () {
  "use strict";

  const repo = "mccreeper1318/pindb";
  const releasesUrl = `https://github.com/${repo}/releases`;
  const api = `https://api.github.com/repos/${repo}/releases`;

  function setText(id, value) {
    const node = document.getElementById(id);
    if (node) node.textContent = value;
  }

  function choosePackage(release, extension) {
    if (!release) return null;
    const packages = release.assets.filter(asset => asset.name.toLowerCase().endsWith(extension));
    return packages.find(asset => /(?:x86_64|amd64|x64)/i.test(asset.name)) || packages[0] || null;
  }

  function chooseChecksum(release, packageAsset) {
    if (!release || !packageAsset) return null;
    const exactName = `${packageAsset.name}.sha256`.toLowerCase();
    return release.assets.find(asset => asset.name.toLowerCase() === exactName) ||
      release.assets.find(asset => {
        const name = asset.name.toLowerCase();
        return name === "checksums.sha256" || name === "checksums-linux.sha256";
      }) || null;
  }

  function setPackageLink(id, release, asset, fallback, packageLabel) {
    const link = document.getElementById(id);
    if (!link) return;
    link.href = asset ? asset.browser_download_url : fallback;
    link.textContent = asset ? `Download ${asset.name}` : `View release for ${packageLabel}`;
    link.title = release ? `${packageLabel} for ${release.tag_name}` : packageLabel;
  }

  function setChecksumLink(id, release, packageAsset, fallback, packageLabel) {
    const link = document.getElementById(id);
    if (!link) return;
    const checksum = chooseChecksum(release, packageAsset);
    link.href = checksum ? checksum.browser_download_url : fallback;
    link.textContent = checksum ? `Checksum: ${checksum.name}` : `View ${packageLabel} checksum`;
  }

  function fillRelease(prefix, release, fallback) {
    if (!release) {
      setText(`${prefix}-version`, "not published");
      return;
    }

    const deb = choosePackage(release, ".deb");
    const rpm = choosePackage(release, ".rpm");
    setText(`${prefix}-version`, release.tag_name);
    setPackageLink(`${prefix}-deb`, release, deb, fallback, "Debian package");
    setChecksumLink(`${prefix}-deb-checksum`, release, deb, fallback, "Debian package");
    setPackageLink(`${prefix}-rpm`, release, rpm, fallback, "Fedora RPM");
    setChecksumLink(`${prefix}-rpm-checksum`, release, rpm, fallback, "Fedora RPM");
  }

  async function loadReleases() {
    try {
      const response = await fetch(api, {
        headers: {
          "Accept": "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28"
        }
      });
      if (!response.ok) throw new Error(`GitHub returned ${response.status}`);

      const releases = await response.json();
      const published = releases.filter(release => !release.draft);
      const stable = published.find(release => !release.prerelease);
      const beta = published.find(release => release.prerelease);

      fillRelease("stable", stable, stable?.html_url || `${releasesUrl}/latest`);
      fillRelease("beta", beta, beta?.html_url || releasesUrl);

      const newest = published[0];
      if (newest) setText("latest-version", newest.tag_name);
      setText("release-status", "GitHub release information loaded. Download buttons now point to available package assets.");
    } catch (error) {
      setText("release-status", "Live release lookup is unavailable. The buttons still open the GitHub Releases page.");
    }
  }

  function localCounter() {
    const key = "pindb-retro-preview-visits";
    let visits = Number(localStorage.getItem(key) || "0") + 1;
    localStorage.setItem(key, String(visits));
    setText("visitor-counter", String(visits).padStart(6, "0"));
  }

  document.addEventListener("DOMContentLoaded", function () {
    localCounter();
    if (document.querySelector("[data-release-page]") || document.getElementById("latest-version")) {
      loadReleases();
    }
    const year = document.getElementById("year");
    if (year) year.textContent = new Date().getFullYear();
  });
}());
