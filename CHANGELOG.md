# Changelog

## 2.0.0 (unreleased)

A major version: the module asks for more of the platform and does more by default than 1.0.0.

### Upgrading from 1.0.0

* **Jahia 8.2 or later** is required.
* **Nothing to migrate.** The files stay where they are (`jmix:llms`, `j:llms` on the site node) and are
  served at the same addresses. Modules that read or write them (geo-readiness, content-governance)
  keep working.
* **Served by default after the upgrade**, without any action: each published page as Markdown at its
  URL followed by `.md`, and two discovery links in the `<head>` of the pages of the sites where the
  module is enabled. `servePageMarkdown` and `discoveryLinks` switch them off.
* **Save and publish** publishes the site node alone.
* The module adds the permission `siteAdminLlmsSettings` to the role `site-administrator`.
* A host claimed by no site is served the files of the default site.

### Added

**The two files**

* **llms-full.txt.** A second file per site, stored in `j:llmsFull` and served at `/llms-full.txt` and
  `/sites/<siteKey>/llms-full.txt`.
* **Generate both files from the site.** llms.txt lists the published pages a visitor without an account
  can read, by section, with their SEO description; llms-full.txt renders each of those pages
  anonymously and keeps its main content as Markdown. Generation never saves: the editor reviews and
  edits the text first. Language picker (site default first) and a per-site public URL for the links
  (`j:llmsBaseUrl`).
* `/sites/<siteKey>/llms.txt` serves that site's file whatever the host, for sites that share a host
  (`localhost`, Jahia Cloud).

**What the llms.txt proposal (v2) asks**

* **Each page has a Markdown version at its own URL followed by `.md`** (`page.html.md`), and at the
  shorter `page.md`. It is the page as a visitor without an account gets it, whoever asks: no Markdown
  version of a page that is not published or not open to visitors. `linkToMarkdown` makes a generated
  llms.txt link to these versions.
* **The Markdown version is a view of the module** (template type `md`, for `jnt:page` and
  `jmix:mainResource`): Jahia renders the page with its own template, the module converts its main
  content, and Jahia keeps the result in its cache until the page or a content below it is published.
  It needs the module enabled on the site.
* **A Markdown version names its page**: it starts with a YAML block that gives the `title` of the
  page, its `url` (the page as HTML, the one to quote), its `description` and the day it was last
  published (`last_updated`), for a reader that has the file without its HTTP headers.
* **A vanity URL has a Markdown version too**: the vanity URL followed by `.md`, on the hosts where
  Jahia answers that vanity URL. `linkToMarkdown` links to it, so the contents known by a vanity URL (news, articles) are linked
  as Markdown like the pages.
* **Pages name their Markdown version and the llms.txt of their site** in their `<head>`
  (`rel="alternate" type="text/markdown"`, `rel="describedby"`), and a Markdown version does so in a
  `Link` header.
* **A section of the site can have its own llms.txt and llms-full.txt**, as the proposal defines a file
  in a sub-path. In the panel, "Files of" switches between the whole site and a section; the files are
  generated from the page of the section down and served under its path (`/home/docs/llms.txt`). The
  pages below a menu group can be chosen too, named after their group. A page a visitor without an
  account cannot read is refused as a section, and the panel says why.
* **Chaining.** A generated llms.txt ends with the links to the llms.txt of the sections that have one:
  the proposal has no paging, and this is how the files of a large site chain.

**What is listed**

* **Contents shown as a page of their own** (`jmix:mainResource`: news, articles) are listed after the
  pages, the most recent first (`maxMainResources`, 100 by default), get a Markdown version and name it
  in their head.
* **Fair page selection.** When a site has more pages than one file lists, every page of a level is
  listed before any page of the next, instead of filling the limit with the first sections. `maxDepth`
  now defaults to 10: `maxPages` is the limit.
* **List only marked pages.** `includedNodeTypes` lists only the pages and contents that carry a mixin,
  such as the new page option `jmix:llmsListed` (List in llms.txt). The content editor shows that
  option only to who holds the new permission `llmsListPages` on the site.
* The node types that leave a page out are configurable (`excludedNodeTypes`, as many as needed), with
  the "hidden in the menu" marks of the jahia.com and Academy templates among the defaults, and a
  second list leaves out a page with all its sub-pages (`excludedTreeNodeTypes`).

**Configuration**

* OSGi configuration `org.jahia.modules.llms` for the values of every site and the limits that protect
  the server.
* **A configuration per site**: `org.jahia.modules.llms.site-<name>.cfg` changes, for one site, what is
  listed (depth, numbers, excluded and listed types, links to Markdown, images). A key left out keeps
  the value of the module configuration.
* **Settings tab** in the panel, for who administers the site (permission `siteAdminLlmsSettings`, added
  to the role `site-administrator`): the values of the site, each explained, with the value it inherits,
  and checked. A value the module would have to correct is refused, and nothing is saved while one is.
  Saving writes the configuration of that site.

**The panel**

* **A generation of llms-full.txt shows the pages go by.** One block says what runs, with a progress bar
  and each page as it is done; it stays under the result afterwards. The generation runs in the
  background (`llms { startGeneration }`, read with `llms { generationProgress }`) instead of one request
  that says nothing for a minute.
* **The panel says which limit cut a generated file** (pages, size or time) and how many pages were left
  out. Pages not written because llms-full.txt was full are marked so, no longer shown as failures or
  left unsaid.
* **The panel says which pages are left out**, from the rules in force for its site: hidden from the
  menu, not translated, marked No index, of a configured type, and how many entries a file may list.
* **Preview** of a file: its Markdown rendered and sanitized, in place of the editor.
* A generated llms-full.txt says it is the pages put end to end, and that proofreading and cleaning it
  up before publishing is strongly advised.
* German, French and English labels for the panel, the settings, the permissions and the content type
  fields.

**API**

* GraphQL `llms { generate, startGeneration, generationProgress, status, sections, rules, settings }`
  and `llms { save, saveSettings }`, with the permission checks on the server.

### Security

* Saving the settings of a site never writes or removes the configuration of another site, even when
  a file is named after the first and holds the key of the second. The values of the server
  (`renderBaseUrl`, threads, timeouts, sizes) cannot be set for a site.
* The preview renders Markdown through a sanitizer: no script, style, form, image, id or tab stop.

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

* The thread that generates llms-full.txt works as the guest user: Jahia logged a "Null thread user"
  error for each page of a generation.
* A text of a page that is only a tag left open (`<ul`) is no longer written in llms-full.txt.

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
* React 18, `@apollo/client` from the host, Node 22, Yarn 1, Jahia 8.2 parent, Java 17. New front-end
  dependencies for the preview: `marked` and `dompurify`.
* At the domain root, a host claimed by no site or by the system site only is served by the default
  site.

### Removed

* The ESLint and Stylelint configurations of the 2021 toolchain, and the Sass loader.
