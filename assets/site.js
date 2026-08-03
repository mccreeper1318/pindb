(function () {
  "use strict";

  const repo = "mccreeper1318/pindb";
  const api = `https://api.github.com/repos/${repo}/releases`;

  function setText(id, value) {
    const node = document.getElementById(id);
    if (node) node.textContent = value;
  }

  function setAssetLink(id, release, asset, fallback) {
    const link = document.getElementById(id);
    if (!link) return;
    if (asset) {
      link.href = asset.browser_download_url;
      link.textContent = `Download ${asset.name}`;
      link.title = `Download ${release.tag_name}`;
    } else {
      link.href = fallback;
    }
  }

  async function loadReleases() {
    try {
      const response = await fetch(api, { headers: { "Accept": "application/vnd.github+json" } });
      if (!response.ok) throw new Error(`GitHub returned ${response.status}`);
      const releases = await response.json();
      const published = releases.filter(r => !r.draft);
      const stable = published.find(r => !r.prerelease);
      const beta = published.find(r => r.prerelease);
      const chooseDeb = release => release && release.assets.find(a => a.name.toLowerCase().endsWith(".deb"));
      const chooseChecksum = (release, deb) => release && release.assets.find(a =>
        a.name === `${deb?.name}.sha256` || a.name.toLowerCase() === "checksums.sha256");

      if (stable) {
        setText("stable-version", stable.tag_name);
        setAssetLink("stable-deb", stable, chooseDeb(stable), `https://github.com/${repo}/releases/latest`);
        const checksum = chooseChecksum(stable, chooseDeb(stable));
        if (checksum) setAssetLink("stable-checksum", stable, checksum, `https://github.com/${repo}/releases/latest`);
      }
      if (beta) {
        setText("beta-version", beta.tag_name);
        setAssetLink("beta-deb", beta, chooseDeb(beta), `https://github.com/${repo}/releases`);
        const checksum = chooseChecksum(beta, chooseDeb(beta));
        if (checksum) setAssetLink("beta-checksum", beta, checksum, `https://github.com/${repo}/releases`);
      }
      const newest = published[0];
      if (newest) setText("latest-version", newest.tag_name);
      setText("release-status", "GitHub release information loaded successfully.");
    } catch (error) {
      setText("release-status", "Live release lookup unavailable. The Releases links still work.");
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
    if (document.querySelector("[data-release-page]") || document.getElementById("latest-version")) loadReleases();
    const year = document.getElementById("year");
    if (year) year.textContent = new Date().getFullYear();
  });
}());
