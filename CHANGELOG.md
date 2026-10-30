# Changelog

## 1.1.0 (unreleased)

### Added

* **llms-full.txt.** A second file per site, stored in `j:llmsFull` and served at `/llms-full.txt` and
  `/sites/<siteKey>/llms-full.txt`.
* **Generate both files from the site.** llms.txt lists the published pages a visitor without an account
  can read, by section, with their SEO description; llms-full.txt renders each of those pages
  anonymously and keeps its main content as Markdown. Generation never saves: the editor reviews and
  edits the text first. Language picker (site default first) and a per-site public URL for the links
  (`j:llmsBaseUrl`).
* GraphQL `llms { generate }` and `llms { save }`, with the permission checks on the server.
* `/sites/<siteKey>/llms.txt` serves that site's file whatever the host, for sites that share a host
  (`localhost`, Jahia Cloud).
* OSGi configuration `org.jahia.modules.llms` for the generation limits.
* German, French and English labels for the panel and the content type fields, with tooltips.

### Changed

* **The panel follows Jahia's screen layout**, with Moonstone components only: header with the title
  and Save and publish, one tab per file, cards at the full width of the panel, a status chip per file,
  Moonstone text area, banners and toasts. Gone: the fixed 666 px text area, the hard-coded colours and
  fonts, the "Loading ..." text and the legacy `@jahia/design-system-kit`, `@jahia/react-material` and
  `@apollo/react-hooks` (whose private Apollo context could blank jContent after a restart).
* **Save and publish publishes the site node alone.** 1.0.0 published only when the site was already
  published, and then through a publication that could include sub-nodes of the site.
* The panel checks that the module is enabled and that the user may publish, on a direct URL or a site
  switch too, and reads the site from jContent rather than from the page load.
* React 18, `@apollo/client` from the host, Node 22, Yarn 1, Jahia 8.2 parent, Java 17.
* At the domain root, a host claimed by no site or by the system site only is served by the default
  site.

### Removed

* The ESLint and Stylelint configurations of the 2021 toolchain, and the Sass loader.
