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
* **Contents shown as a page of their own** (`jmix:mainResource`: news, articles) are listed after the
  pages, the most recent first (`maxMainResources`, 100 by default), get a Markdown version and name it
  in their head.
* **List only marked pages**: `includedNodeTypes` lists only the pages and contents that carry a mixin,
  such as the new page option `jmix:llmsListed` (List in llms.txt).
* **A configuration per site**: `org.jahia.modules.llms.site-<name>.cfg` changes, for one site, what is
  listed (depth, numbers, excluded and listed types, links to Markdown, images).

* **Each page has a Markdown version at its own URL followed by `.md`** (`page.html.md`), as the
  llms.txt proposal (v2) asks. It is the page as a visitor without an account gets it: no Markdown
  version of a page that is not published or not open to visitors. `linkToMarkdown` makes a generated
  llms.txt link to these versions.

* **Pages name their Markdown version and the llms.txt of their site** in their `<head>`
  (`rel="alternate" type="text/markdown"`, `rel="describedby"`), and a Markdown version does so in a
  `Link` header. This is how the proposal lets an agent find these files from a page.

* **A section of the site can have its own llms.txt and llms-full.txt**, as the proposal (v2) defines a
  file in a sub-path. In the panel, "Files of" switches between the whole site and a section; the files
  are generated from the page of the section down and served under its path
  (`/home/docs/llms.txt`). The link of a page to its llms.txt names the nearest one.

### Security

* The public files are served with an `ETag` and `304 Not Modified` (`Cache-Control: no-cache`, so a
  published change is still served at once), and the site of a host comes from Jahia's cache: an
  anonymous request no longer runs a repository query and re-sends the whole file each time.
* Generation limits are kept in range (no size overflow), a generation of llms-full.txt has an overall
  deadline (`maxTotalSeconds`), two run at most on one Jahia, and the walk stops reading the page tree
  once the page limit is reached.
* Converting a page no longer uses patterns whose cost grows with the square of a run of white space
  or of `<`.
* Generated links keep only `http`, `https`, `mailto` and `tel` targets (images: `http` and `https`),
  and a link target can no longer close its own parenthesis.
* A page that redirects is followed only to another URL of the same site.
* Only the languages active in live are accepted for generation.
* Saving llms-full.txt alone no longer publishes the autocreated `# Title` as llms.txt, and the
  placeholder is never served.

### Fixed

* **llms-full.txt no longer drops parts of a page.** The converter kept only `<main>` and removed every
  hidden element, form and button. It now takes the first usual place that holds a fair share of the
  text (`<main>`, a single `<article>`, `#content`, else the body without its chrome), keeps the panels
  a visitor can open (accordions, tabs under the name of their tab, carousel slides), the title of an
  accordion (a button in a heading), the content of a form wrapped around a page, an article's own
  header and asides, embedded videos as a link, and lazy-loaded images. Dialogs stay out.
* **Content beside `<main>` is kept, and a template without landmarks is read cleanly.** `<main>` is
  taken alone only when it holds nearly all the text; otherwise the whole body is read. The chrome that
  older templates only name with a class (`header-v8`, `blog-topbar`, `navbar`, `footer-v1`,
  `copyright`) is dropped, so a page of such a template gives its content and not its login bar and
  footer.
* A media file or a module resource named `llms.txt` is no longer captured by the module's URLs.
* **Images are left out of llms-full.txt** unless `keepImages` is set: a text reader gains nothing from
  an address it cannot open, and most images of a page are logos and decorations.
* A page with nothing to read is left out of llms-full.txt instead of being written as a bare title.
* **A page marked No index is left out of both files.** The check looked for a node type that does not
  exist (`jmix:noindex`), so the mark never had any effect. It now reads the **No index** option the
  sitemap module adds to a page (`jseomix:noIndex`).
* The node types that leave a page out are configurable (`excludedNodeTypes`, as many as needed), with
  the "hidden in the menu" marks of the jahia.com and Academy templates among the defaults, and a
  second list leaves out a page with all its sub-pages (`excludedTreeNodeTypes`).

### Panel

* **Generate sits with what it needs.** The language, the public URL and the Generate button are one
  block, at the top of each tab; the button names the file it generates and keeps the focus while it
  runs, so the proposal appears right below it.
* **Each file says where it is served**: its address, as a link, once it is published, and "not served
  yet" in words until then.
* Blocks are Moonstone `Paper`s, fields line up with their titles and carry a real label, running text
  keeps a readable line length.
* Accessibility: every field is named, an invalid URL is tied to its field, tabs are related to their
  panel, status changes are announced, the focus is never lost after an action.

* **A generation of llms-full.txt shows the pages go by.** A progress bar with the count of pages, and
  a log of each page as it is rendered, with what became of it (rendered, empty and left out, failed and
  left out). The log stays after the generation, until the next action. The generation runs in the
  background (`llms { startGeneration }`, read with `llms { generationProgress }`) instead of one request
  that says nothing for a minute.

* **The panel says which pages are left out**, from the rules in force on this Jahia: hidden from the
  menu, not translated, marked No index, of a configured type, and how many pages and levels are listed.

### Performance

* The panel asks the server whether each file is served and current (`llms { status }`) instead of
  downloading the published text a second time, and counts lines, links and size when the text
  changes, not on every render.
* A conditional request for an unchanged file answers 304 without reading or hashing it again while
  the dates of the site node have not moved (checked again every minute at the latest).
* The page tree is read once per level, and the conversion patterns are compiled once.

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
