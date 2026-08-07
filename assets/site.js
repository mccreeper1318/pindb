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

  function makeUnavailable(link, text) {
    link.removeAttribute("href");
    link.textContent = text;
    link.classList.add("unavailable");
    link.setAttribute("aria-disabled", "true");
    link.removeAttribute("title");
  }

  function setPackageLink(id, release, asset, packageLabel, channelLabel) {
    const link = document.getElementById(id);
    if (!link) return;

    if (!asset) {
      makeUnavailable(link, `No ${channelLabel} ${packageLabel} published yet`);
      return;
    }

    link.href = asset.browser_download_url;
    link.textContent = `Download ${asset.name}`;
    link.title = `${packageLabel} for ${release.tag_name}`;
    link.classList.remove("unavailable");
    link.removeAttribute("aria-disabled");
  }

  function setChecksumLink(id, release, packageAsset, packageLabel) {
    const link = document.getElementById(id);
    if (!link) return;

    const checksum = chooseChecksum(release, packageAsset);
    if (!checksum) {
      makeUnavailable(link, packageAsset ? `No ${packageLabel} checksum published` : "Checksum unavailable");
      return;
    }

    link.href = checksum.browser_download_url;
    link.textContent = `Checksum: ${checksum.name}`;
    link.classList.remove("unavailable");
    link.removeAttribute("aria-disabled");
  }

  function fillRelease(prefix, release, channelLabel) {
    if (!release) {
      setText(`${prefix}-version`, "not published");
      setPackageLink(`${prefix}-deb`, null, null, "Debian package", channelLabel);
      setPackageLink(`${prefix}-rpm`, null, null, "Fedora RPM", channelLabel);
      return;
    }

    const deb = choosePackage(release, ".deb");
    const rpm = choosePackage(release, ".rpm");

    setText(`${prefix}-version`, release.tag_name);
    setPackageLink(`${prefix}-deb`, release, deb, "Debian package", channelLabel);
    setChecksumLink(`${prefix}-deb-checksum`, release, deb, "Debian package");
    setPackageLink(`${prefix}-rpm`, release, rpm, "Fedora RPM", channelLabel);
    setChecksumLink(`${prefix}-rpm-checksum`, release, rpm, "Fedora RPM");
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

      fillRelease("stable", stable, "stable");
      fillRelease("beta", beta, "beta");

      const newest = published[0];
      if (newest) setText("latest-version", newest.tag_name);
      setText("release-status", "GitHub release information loaded. Available package buttons download the files directly.");
    } catch (error) {
      setText("release-status", `Live release lookup is unavailable. Visit ${releasesUrl} for all packages.`);
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
