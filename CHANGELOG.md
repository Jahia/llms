# Changelog

## 2.0.0 (2026-11-24)

A major version. 1.0.0 was an editor for one file, llms.txt, and a servlet that served it. 2.0.0
generates the files from the site, adds llms-full.txt, serves each page as Markdown, and asks for more
of the platform.

### Upgrading from 1.0.0

* **Jahia 8.2 or later** is required. 1.0.0 ran on Jahia 8.1.
* **Nothing to migrate.** The files stay where they are (`jmix:llms`, `j:llms` on the site node) and
  are served at the same addresses. Modules that read or write them (geo-readiness,
  content-governance) keep working.
* **New by default after the upgrade**, without any action, on the sites where the module is enabled:
  each published page is served as Markdown at its URL followed by `.md`, and its `<head>` carries two
  discovery links. `servePageMarkdown` and `discoveryLinks` switch them off.
* **Fewer addresses answer.** 1.0.0 answered any address that ended in `/llms.txt` with the file of the
  site of the host. Only the addresses listed in the README answer now: a media file or a resource of
  another module named `llms.txt` keeps its own URL.
* **A file that was never written is no longer served.** A site whose llms.txt is still the
  autocreated `# Title` answers 404, where 1.0.0 served that placeholder.
* **A host claimed by no site**, or by the system site only, is served the files of the default site,
  where 1.0.0 answered 404.
* **Caching.** The files are served with `Cache-Control: no-cache` and an `ETag`, where 1.0.0 sent
  `no-store`: a client revalidates and gets a 304 when nothing changed. A published change is still
  served at once.
* **Save and publish** publishes the site node alone. 1.0.0 published only when the site was already
  published, and then through a publication that could include sub-nodes of the site.
* The module adds the permission `siteAdminLlmsSettings` to the role `site-administrator`, and the
  permission `llmsListPages`, which no role holds until it is granted.

### Added

**The two files**

* **llms-full.txt.** A second file per site, stored in `j:llmsFull` and served at `/llms-full.txt` and
  `/sites/<siteKey>/llms-full.txt`.
* **Generate both files from the site.** llms.txt lists the published pages a visitor without an
  account can read, by section, with their SEO description; llms-full.txt renders each of those pages
  for such a visitor and keeps its main content as Markdown. Generation never saves: the editor reviews
  and edits the text first. Language picker (site default first) and a per-site public URL for the
  links (`j:llmsBaseUrl`).
* `/sites/<siteKey>/llms.txt` serves that site's file whatever the host, for sites that share a host
  (`localhost`, Jahia Cloud).

**What llms-full.txt keeps of a page**

* **The content, wherever the template puts it.** The first usual place that holds nearly all the text
  is taken (`<main>`, a single `<article>`, `#content`); as soon as a real share of the text sits
  outside it, the whole body is read, without its chrome. The chrome that older templates only name
  with a class (`header-v8`, `blog-topbar`, `navbar`, `footer-v1`, `copyright`) is dropped too.
* **What a visitor can open**: the panels of accordions and of tabs, under the name of their tab,
  carousel slides, the title of an accordion, the content of a form wrapped around a page, an article's
  own header and asides, embedded videos as a link, lazy-loaded images. Dialogs stay out.
* **Not the menus beside it.** Templates of documentation put the menu of a section, the table of
  contents of the page and the switches of version inside `<main>`. A menu is left out when it is in
  an `<aside>`, when the page has an `<article>` and the menu is outside it, or when it is a small part
  of the text. A page that is a menu, as a site map, keeps it.
* **Images are left out** unless `keepImages` is set: a text reader gains nothing from an address it
  cannot open, and most images of a page are logos and decorations.
* A page with nothing to read adds no block, instead of a bare title.

**What the llms.txt proposal (v2) asks**

* **Each page has a Markdown version at its own URL followed by `.md`** (`page.html.md`), and at the
  shorter `page.md`. It is the page as a visitor without an account gets it, whoever asks: no Markdown
  version of a page that is not published or not open to visitors. `linkToMarkdown` makes a generated
  llms.txt link to these versions; it is off by default, since geo-readiness and content-governance
  compare the links of llms.txt with the addresses of the pages.
* **The Markdown version is a view of the module** (template type `md`, for `jnt:page` and
  `jmix:mainResource`): Jahia renders the page with its own template, the module converts its main
  content, and Jahia keeps the result in its cache until the page or a content below it is published.
  A content the page only refers to follows within ten minutes. The links of the page are the ones a
  visitor gets: its short address on the host of the site, its vanity URL. It needs the module enabled on the
  site.
* **A Markdown version names its page**: it starts with a YAML block that gives the `title` of the
  page, its `url` (the page as HTML, the one to quote), its `description` and the day it was last
  published (`last_updated`), for a reader that has the file without its HTTP headers.
* **A vanity URL has a Markdown version too**: the vanity URL followed by `.md`, on the hosts where
  Jahia answers that vanity URL. `linkToMarkdown` links to it, so the contents known by a vanity URL
  (news, articles) are linked as Markdown like the pages.
* **Pages name their Markdown version and the llms.txt of their site** in their `<head>`
  (`rel="alternate" type="text/markdown"`, `rel="describedby"`), and a Markdown version does so in a
  `Link` header, with the page it is another form of (`rel="canonical"`).
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
* **Fair page selection.** When a site has more pages than one file lists (`maxPages`, 200 by default),
  every page of a level is listed before any page of the next, instead of filling the limit with the
  first sections. `maxDepth` defaults to 10.
* **Pages a visitor is not meant to find are left out**: hidden from the menu, not translated in the
  language of the file, or marked **No index** with the option the sitemap module adds to a page
  (`jseomix:noIndex`). The node types that leave a page out are configurable (`excludedNodeTypes`, as
  many as needed), with the "hidden in the menu" marks of the jahia.com and Academy templates among the
  defaults, and a second list leaves out a page with all its sub-pages (`excludedTreeNodeTypes`).
* **List only marked pages.** `includedNodeTypes` lists only the pages and contents that carry a mixin,
  such as the new page option `jmix:llmsListed` (List in llms.txt). The content editor shows that
  option only to who holds the new permission `llmsListPages` on the site.

**Configuration**

* OSGi configuration `org.jahia.modules.llms` for the values of every site and the limits that protect
  the server.
* **A configuration per site**: `org.jahia.modules.llms.site-<name>.cfg` changes, for one site, what is
  listed (depth, numbers, excluded and listed types, links to Markdown, images). A key left out keeps
  the value of the module configuration.
* **Settings tab** in the panel, for who administers the site (permission `siteAdminLlmsSettings`):
  the values of the site, each explained, with the value it inherits, and checked. A value the module
  would have to correct is refused, and nothing is saved while one is. Saving writes the configuration
  of that site.

**The panel**

* **Generate sits with what it needs.** The language, the public URL and the Generate button are one
  block, at the top of each tab; the button names the file it generates and keeps the focus while it
  runs, so the proposal appears right below it.
* **A generation of llms-full.txt shows the pages go by.** One block says what runs, with a progress bar
  and each page as it is done; it stays under the result afterwards. The generation runs in the
  background (`llms { startGeneration }`, read with `llms { generationProgress }`) instead of one request
  that says nothing for a minute.
* **The panel says which limit cut a generated file** (pages, size or time) and how many pages were left
  out. A page that did not fit in a full llms-full.txt is marked as left out, not as a failure.
* **The panel says which pages are left out**, from the rules in force for its site, and how many
  entries a file may list.
* **Each file says where it is served**: its address, as a link, once it is published, and "not served
  yet" in words until then.
* **Preview** of a file: its Markdown rendered and sanitized, in place of the editor.
* A generated llms-full.txt says it is the pages put end to end, and that proofreading and cleaning it
  up before publishing is strongly advised.
* German, French and English labels for the panel, the settings, the permissions and the content type
  fields.

**API**

* GraphQL `llms { generate, startGeneration, generationProgress, status, sections, rules, settings }`
  and `llms { save, saveSettings }`, with the permission checks on the server.

### Changed

* **The panel follows Jahia's screen layout**, with Moonstone components only: header with the title
  and Save and publish, one tab per file, cards at the full width of the panel, a status chip per file,
  Moonstone text area, banners and toasts. Gone: the fixed 666 px text area, the hard-coded colours and
  fonts, the "Loading ..." text and the legacy `@jahia/design-system-kit`, `@jahia/react-material` and
  `@apollo/react-hooks` (whose private Apollo context could blank jContent after a restart).
* **Accessibility of the panel**: every field is named, an invalid URL is tied to its field, tabs are
  related to their panel, status changes are announced, the two regions that scroll can be reached with
  the keyboard, the focus is never lost after an action.
* The panel checks that the module is enabled and that the user may publish, on a direct URL or a site
  switch too, and reads the site from jContent rather than from the page load.
* React 18, `@apollo/client` from the host, Node 22, Yarn 1, Jahia 8.2 parent, Java 17. New
  dependencies: `jsoup`, embedded in the bundle, to read the pages; `marked` and `dompurify` in the
  panel, for the preview.

### Fixed

* A media file or a module resource named `llms.txt` is no longer captured by the module's URLs.
* A host that is no site's no longer makes the servlet fail and write an error in the log on each
  request.

### Security

What the new features were built to hold, since a public URL now makes Jahia render its own pages.

* **A visitor never chooses what is rendered.** A Markdown version is rendered for the page of the
  address asked for and no other: nothing is read from a parameter a visitor added, an address that
  reads two ways (an encoded separator, a dot segment) is refused, and the servlet answers only a
  request the rewrite rules forwarded.
* **A Markdown version is the same for everyone.** It is rendered for a visitor without an account, in
  live, whoever asks: nothing a signed-in user alone may read is written in it.
* **The Markdown of a page leaves by one door.** The view behind a Markdown version has an address of
  its own in Jahia (`about.md/`, `about.md.ajax`). It answers 404 there: served by Jahia's own render
  servlet it would leave as HTML, with the text of the page no longer escaped. Only the addresses of
  the README answer, as `text/markdown`, with `nosniff`.
* **What a visitor adds to an address reaches nothing that is kept.** A page is rendered without the
  parameters of the request, so a pager or a search of the page cannot be made to write in what Jahia
  keeps for every visitor.
* **The Markdown Jahia keeps in its cache holds no host.** The host of each request is written when
  the file is served, so a request cannot make its own `Host` header reach the next visitor.
* **Generation is bounded.** A generation of llms-full.txt has an overall deadline (`maxTotalSeconds`),
  a size limit (`maxFullKb`) and a limit per page; two run at most on one Jahia, and the walk stops
  reading the page tree once the page limit is reached. The limits are kept in range.
* A page that redirects is followed only to another URL of the same site.
* Generated links keep only `http`, `https`, `mailto` and `tel` targets (images: `http` and `https`),
  and a link target cannot close its own parenthesis.
* Converting a page uses no pattern whose cost grows with the square of a run of white space or of
  `<`, and a path of any length is read without recursion.
* Saving the settings of a site never writes or removes the configuration of another site, even when
  a file is named after the first and holds the key of the second. The values of the server
  (`renderBaseUrl`, threads, timeouts, sizes) cannot be set for a site.
* The preview renders Markdown through a sanitizer: no script, style, form, image, id or tab stop.
* Only the languages active in live are accepted for generation.

### Performance

* A conditional request for an unchanged file answers 304 without reading or hashing it again while
  the dates of the site node have not moved (checked again every minute at the latest), and the site
  of a host comes from Jahia's cache: an anonymous request runs no repository query.
* The panel asks the server whether each file is served and current (`llms { status }`) rather than
  downloading the published text, and counts lines, links and size when the text changes, not on
  every render.
* The page tree is read once per level, and the conversion patterns are compiled once.

### Removed

* The ESLint and Stylelint configurations of the 2021 toolchain, and the Sass loader.
