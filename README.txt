PinDB GitHub Pages Website
==========================

This is the complete, self-contained preview of the PinDB website.

To preview it locally, extract the ZIP and open index.html in a browser.
For the closest match to GitHub Pages, serve the extracted folder with any
static web server, for example:

    python3 -m http.server 8000

Then open http://localhost:8000/ in a browser.

For publication, upload the contents of this folder to the root of the
gh-pages branch. Keep the .nojekyll file in the branch root. The website entry
page is index.html. JavaScript is only used for the current year and live GitHub
release download links.
