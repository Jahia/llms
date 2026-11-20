![GitHub tag (latest by version)](https://img.shields.io/github/v/tag/Jahia/llms?sort=semver)
![License](https://img.shields.io/github/license/jahia/llms)

# Jahia `llms.txt` Manager

Write, generate and publish the [`llms.txt`](https://llmstxt.org/) and `llms-full.txt` of a Jahia site
from jContent. No file to edit on a server, no deployment.

- **llms.txt** is an index of the site for AI assistants: the site's name, a one-line summary, then its
  pages by section, each with a link and its description.
- **llms-full.txt** holds the text of those pages, converted to Markdown, so an assistant can read the
  site in one file.
- **Each page** is also served as Markdown, at its own URL followed by `.md`.

Everything describes the site as a visitor without an account gets it: published content only, read
with the rights of the guest user.

## Contents

- [Install](#install)
- [The panel](#the-panel): generate, review, publish, sections, preview, settings
- [What generation lists](#what-generation-lists)
- [What is served](#what-is-served)
- [Configuration](#configuration): the module, one site, the option of a page
- [Permissions](#permissions)
- [GraphQL API](#graphql-api)
- [Contract with other modules](#contract-with-other-modules)
- [Limits and things not tested](#limits-and-things-not-tested)
- [Build and tests](#build-and-tests)

## Install

1. Install the module from the Jahia Store or the module manager. It needs Jahia 8.2 or later and the
   modules `jcontent` and `graphql-dxm-provider`.
2. Enable it on the site: **Administration > Sites > (site) > Modules**.
3. Open **jContent > Additional > SEO > llms.txt**. You need the **publish** permission on the site.

## The panel

One tab per file, and a **Settings** tab for who administers the site.

### Generate, review, publish

1. Pick the **Language**: the default language of the site is preselected. A site has one llms.txt and
   one llms-full.txt, in the language they were generated in.
2. Check the **Public site URL**: the scheme, host and optional port the links start with
   (`https://www.example.com`). It is preset from the server name of the site and saved with the files.
3. Select **Generate**. Nothing is saved: the proposal replaces the text of the editor, and
   **Discard changes** brings the saved file back.
4. Review and edit the text, then select **Save and publish**.

Saving writes the files on the site node and publishes **that node alone**, never its pages or content.
An empty file is removed, and its URL then answers 404.

llms-full.txt renders every listed page, so it runs in the background. The panel shows a progress bar
and each page as it is done: rendered, empty and left out, failed and left out, or left out because the
file was full. The list stays after the generation.

A generated llms-full.txt is the pages put end to end. The panel says so: proofread it, rewrite what
reads badly and remove what does not help before publishing. A curated file serves assistants far better
than a raw one.

### When a file is cut

The panel names the limit that cut a generated file:

| Limit | What happened | What to do |
|---|---|---|
| Pages | The site has more pages than one file lists: the pages nearest to the home page were kept | Give the large sections files of their own |
| Size | llms-full.txt reached `maxFullKb`: the last pages were left out, and counted | Generate by section, or raise `maxFullKb` |
| Time | `maxTotalSeconds` ran out: the pages not rendered in time were left out, and named | Generate by section, or raise the limit |

### Preview

The card of a file has a **Preview** button: the Markdown rendered as a person reads it, in place of the
editor. The text is edited in the editor only. The HTML is sanitized (no script, style, form or image),
and a very large file is previewed up to its first 300 KB.

### Files of a section

The llms.txt proposal lets a file sit in a sub-path: it then covers the pages under that path. A section
is a page of the site with the pages below it.

- **Files of** switches between the whole site and a section. The list offers the pages that already
  have files, and the pages with sub-pages below the home page, looking through menu groups: a page
  below a group is named `Group / Page`.
- The files of a section are generated from its page down, with the same rules, and served under the
  path of that page: the page `/home/docs.html` has `/home/docs/llms.txt` and `/home/docs/llms-full.txt`.
- A page name with a character an address would have to encode (a space, an accent) cannot be the top
  of a section.

**Chaining.** The proposal has no paging. A generated llms.txt ends with a `## llms.txt` list: one link
per section below it that has a published llms.txt of its own. For a site too large for one file:

1. Choose a large section in **Files of**, generate its llms.txt, save and publish. Repeat for the others.
2. Go back to the whole site and generate its llms.txt again: the links are at the end.

The file of the site only knows the section files that are published when it is generated. The pages of
a section that has its own file are still listed in the file of the site.

### Settings

The **Settings** tab holds what this site lists differently from the others: the eight values of the
[configuration of one site](#one-site). Each field says what it does, the range it accepts and the value
it inherits when empty, which also shows in the empty field.

- A value the module would have to correct is refused: a number out of range, a name that is not a node
  type of this Jahia (the error names it). Nothing is saved while a value is refused.
- Saving writes `org.jahia.modules.llms.site-<siteKey>.cfg` through Jahia's configuration service. A
  site that sets nothing any more has no file.
- A change applies to the next generation: a file already saved stays as it is.
- The limits that protect the server are not in the tab: they are the same for every site.

## What generation lists

**Read as a visitor.** The pages are read as the guest user in the live workspace, and rendered by an
anonymous request to this Jahia. A page that is not published, or that guests cannot read, is in no
file. The rights of the editor who generates do not count.

**Pages left out**, their sub-pages still listed:

- pages hidden from the navigation menu;
- pages without a published translation in the chosen language;
- pages that carry a type of `excludedNodeTypes`: by default **No index** (`jseomix:noIndex`, the option
  of the sitemap module) and the "hidden in the menu" marks of two template sets.

A type of `excludedTreeNodeTypes` leaves a page out with everything below it. When `includedNodeTypes`
is set, only the pages that carry one of its types are listed. The panel states the rules in force for
its site.

**How many.** At most `maxPages` pages (200), down to `maxDepth` levels below the home page (10). Only
pages count as a level: a menu group is looked through. When the site has more pages than the limit,
every page of a level is listed before any page of the next, so the limit cuts the deepest level
reached and not the last sections of the site.

**Contents shown as a page.** Contents that have a page of their own (`jmix:mainResource`: a news item,
an article) are listed after the pages, the most recently modified first, at most `maxMainResources`
(100), under the title of the page or folder that holds them. A file therefore lists 300 entries at most
with the defaults.

**Layout of llms.txt.** The home page and the first-level pages without sub-pages come first, then one
section per first-level page with sub-pages, then the pages beside the home page, then the contents
shown as a page, then the links to the section files. Titles come from the page title in the language,
descriptions from its SEO description (`jcr:description`).

```markdown
# Example Corp

> Products and services of Example Corp.

## Home

- [Home](https://www.example.com/home.html): Welcome to Example Corp
- [Contact](https://www.example.com/contact.html)

## Products

- [Products](https://www.example.com/products.html): Everything we sell
- [Widget](https://www.example.com/products/widget.html): The widget, in three sizes

## llms.txt

- [Documentation](https://www.example.com/sites/example/home/docs/llms.txt)
```

**Layout of llms-full.txt.** The name and summary of the site, then one block per page:

```markdown
---

# Widget

Source: https://www.example.com/products/widget.html

The widget comes in three sizes...
```

The converter keeps the main content of a page and drops its chrome: header, footer, navigation,
breadcrumbs, dialogs. It keeps the panels a visitor can open (accordions, tabs, slides), tables, quotes
and code. Images are left out unless `keepImages` is set. Links between listed pages use their public
URL. A page with nothing to read adds no block.

## What is served

| Address | What |
|---|---|
| `/llms.txt`, `/llms-full.txt` | The files of the site, on the site's own domain |
| `/sites/<siteKey>/llms.txt`, `/sites/<siteKey>/llms-full.txt` | The same, whatever the host |
| `<path of a page>/llms.txt`, `<path of a page>/llms-full.txt` | The files of a section |
| `<address of a page>.md` | That page as Markdown |

**The files.** Served as `text/plain` with an `ETag`; a request with `If-None-Match` for an unchanged
file gets `304 Not Modified`. `Cache-Control` is `public, no-cache` for a visitor without an account
(`private, no-cache` for a signed-in user): a proxy may keep the file but revalidates it each time, so a
published change is served at once. A file is served whether or not the module is enabled on the site.

**A page as Markdown.**

```
https://www.example.com/home/about.html      the page
https://www.example.com/home/about.html.md   the same page as Markdown
```

- It is the page as a visitor without an account gets it, whoever asks. A page that is not published or
  not open to visitors has no Markdown version (404).
- Served as `text/markdown`, with a `Link: <page>; rel="canonical"` header.
- Works for pages and for contents shown as a page, at their address (`about.html.md`) and at their
  vanity URL followed by `.md` (`/news/launch.md`). A vanity URL is answered wherever Jahia answers it:
  on the host of its site, and on a host several sites share. On a shared host, a vanity URL that two
  sites use is not answered, since nothing says which one is meant.
- Each request renders the page: eight are converted at the same time at most, beyond which the answer
  is `503` with `Retry-After`.

**Discovery links.** Each published page of a site where the module is enabled carries two links in its
`<head>`, so that an agent holding a page finds the rest:

```html
<link rel="alternate" type="text/markdown" href="/home/about.html.md"/>
<link rel="describedby" href="/sites/mySite/llms.txt"/>
```

The second names the nearest llms.txt going up: the one of the section, else the one of the site. The
links are cached with the page: a site that gets its first llms.txt shows the second link on a page once
the cache of that page is renewed.

## Configuration

### The module

The OSGi configuration `org.jahia.modules.llms` gives the values of every site. The module ships no
file: add `digital-factory-data/karaf/etc/org.jahia.modules.llms.cfg` to change one.

What is listed. A site can change these for itself:

| Key | Default | Meaning |
|---|---|---|
| `maxPages` | `200` | Most pages one file lists (1 to 2000) |
| `maxDepth` | `10` | Levels of pages below the home page that are listed (1 to 10) |
| `maxMainResources` | `100` | Most contents shown as a page one file lists (0 to 2000); `0` lists none |
| `includedNodeTypes` | empty | When set, only the pages and contents that carry one of these node types (mixins) are listed |
| `excludedNodeTypes` | `jseomix:noIndex, jcmix:hidePage, jacademix:hidePage` | Node types of the pages left out; their sub-pages are still listed. Setting the key replaces the defaults: repeat the ones to keep |
| `excludedTreeNodeTypes` | empty | Node types of the pages left out with all their sub-pages |
| `linkToMarkdown` | `false` | In a generated llms.txt, link each page to its Markdown version rather than to the page |
| `keepImages` | `false` | Write the images of a page that have a text alternative in llms-full.txt |

Lists of types are separated by commas. A type no installed module defines is ignored.

`linkToMarkdown` is what the proposal recommends. It is off by default because geo-readiness and
content-governance compare the links of llms.txt with the addresses of the pages.

What protects the server. The same for every site:

| Key | Default | Meaning |
|---|---|---|
| `fetchThreads` | `4` | Pages rendered at the same time for llms-full.txt (1 to 8) |
| `fetchTimeoutSeconds` | `20` | Time allowed to render one page (1 to 120) |
| `maxTotalSeconds` | `300` | Time allowed for one generation of llms-full.txt (10 to 3600) |
| `maxPageKb` | `2048` | Largest rendered page read |
| `maxFullKb` | `4096` | Largest llms-full.txt produced |
| `renderBaseUrl` | blank | Scheme, host and port this Jahia answers on from inside its own container. Blank: `http://127.0.0.1` on the port of the editor's request |

What is served. The same for every site:

| Key | Default | Meaning |
|---|---|---|
| `servePageMarkdown` | `true` | Serve each published page as Markdown at its URL followed by `.md` |
| `discoveryLinks` | `true` | Write the two discovery links in the head of each published page |

A value of this file that is out of range is brought back into it, and a `renderBaseUrl` that is not a
scheme, a host and an optional port is ignored. Two generations of llms-full.txt run at the same time
on one Jahia at most, one per site or section.

### One site

A site can list differently from the others, through its **Settings** tab or a file,
`digital-factory-data/karaf/etc/org.jahia.modules.llms.site-<name>.cfg`:

```
siteKey = academy
maxDepth = 8
includedNodeTypes = jmix:llmsListed
linkToMarkdown = true
```

- `siteKey` is required. The other keys are the eight of the first table above.
- A key left out or blank keeps the value of the module configuration.
- One file per site: when several name the same site, the one whose name sorts first counts.
- For a list of types, `none` empties the list for that site.
- The tab and the file are the same configuration: the tab writes
  `org.jahia.modules.llms.site-<siteKey>.cfg`, or the existing file of the site whatever its name.
  Where the `.cfg` files are deployed by other means (git, provisioning), the next deployment
  overwrites what the tab wrote.

### The option List in llms.txt

`jmix:llmsListed` is an option of a page, and of a content shown as a page, in the SEO section of the
content editor. It counts only on a site that lists marked pages
(`includedNodeTypes = jmix:llmsListed`). On other sites it would do nothing, so the editor shows it only
to who holds the permission `llmsListPages` on the site.

To list only chosen pages on a site:

1. Set `includedNodeTypes = jmix:llmsListed` for the site.
2. Add the permission `llmsListPages` (Roles and permissions, under jContent > engineTabs) to the role
   of the people who decide what the files list.
3. They tick **List in llms.txt** on the pages to list, and publish them.

Both steps are needed: the setting without the permission leaves no one able to mark a page, and the
permission without the setting shows an option that does nothing. The permission hides a field of the
editor; it does not forbid setting the mixin through the API.

## Permissions

All are checked on the server, on the site.

| To | Permission | Held by default by |
|---|---|---|
| Open the panel, generate, read the status | `publish` | Editor-in-chief and above |
| Save and publish the files | `publish` and `jcr:modifyProperties` | Editor-in-chief and above |
| See and change the Settings tab | `siteAdminLlmsSettings` | Site administrator |
| See the option List in llms.txt | `llmsListPages` | No role: add it where needed |

The module adds `siteAdminLlmsSettings` to the role `site-administrator` when it is installed. Add it to
another role in Roles and permissions to open the tab to other people. A role that holds every
permission, and `root`, pass all of these.

## GraphQL API

For signed-in users with the permissions above. `generate`, `startGeneration`, `save` and `status` take
an optional `path`: a page of the site, for the files of that section.

```graphql
# The pages that have files, and those that can be given some
query { llms { sections(siteKey: "mySite") { path title hasFiles } } }

# Whether each file is served, and is the saved one
query { llms { status(siteKey: "mySite") { llms { served current } llmsFull { served current } } } }

# The rules in force for the site
query { llms { rules(siteKey: "mySite") {
    excludedTypes excludedTreeTypes includedTypes maxPages maxDepth maxMainResources keepImages } } }

# llms.txt in one request (full: false). Nothing is saved.
query { llms { generate(siteKey: "mySite", language: "en", baseUrl: "https://www.example.com", full: false) {
    content pages failedPages truncated cutBy skipped } } }

# llms-full.txt runs in the background: start it, then read its progress until finished.
mutation { llms { startGeneration(siteKey: "mySite", language: "en", baseUrl: "https://www.example.com") } }
query { llms { generationProgress(siteKey: "mySite", id: "<id returned above>") {
    started total done finished error
    pages { title url state }
    generation { content pages failedPages truncated cutBy skipped } } } }

# An omitted file is left unchanged, a blank one is removed
mutation { llms { save(siteKey: "mySite", llms: "# My site ...", llmsFull: null, baseUrl: "https://www.example.com") } }

# The values the site may set, with what it inherits (siteAdminLlmsSettings)
query { llms { settings(siteKey: "mySite") { key kind value inherited min max } } }

# A blank value goes back to the default. A refused value comes back with an error, and nothing is saved.
mutation { llms { saveSettings(siteKey: "mySite", keys: ["maxDepth"], values: ["8"]) { key value error detail } } }
```

- `state` of a page: `PENDING`, `RENDERED`, `EMPTY`, `FAILED`, `SKIPPED` (left out, the file was full).
- `cutBy`: `pages`, `size` or `time`, or null when nothing was cut. `skipped` counts the pages left out
  because the file was full.
- `error` of a setting: `number`, `range`, `types` (with the unknown names in `detail`) or `flag`.

## Contract with other modules

Other modules build on this one, so the following does not change without notice:

- the module key `llms`, the mixin `jmix:llms` and the string properties `j:llms`, `j:llmsFull` and
  `j:llmsBaseUrl` on the site node, published to live. The files of a section are stored the same way
  on its page;
- `/llms.txt` and `/llms-full.txt` served as `text/plain`, a published change served at once;
- the autocreated `# Title` is not a file: its URL answers 404;
- the panel at `/jahia/jcontent/<site>/<lang>/apps/siteSettingsSeo/llms`;
- a file is served whether or not this module is enabled on the site.

geo-readiness writes `j:llms` from its dashboard, then reads `/llms.txt` back. content-governance only
reads: `j:llms` in live and both URLs, and it links to the panel.

## Limits and things not tested

- **Cluster.** A generation of llms-full.txt lives in the memory of the node that started it: without
  sticky sessions, a progress request that reaches another node does not find it. Writing the
  configuration of a site was not tested in a cluster.
- **Jahia Cloud** and a Jahia under a context path: not tested.
- **A new permission and the cache of roles.** Jahia keeps the permissions of each role in a cache it
  empties when a role changes. The module changes the role `site-administrator` at installation for
  that reason. If the Settings tab does not show for a site administrator after an upgrade, flush the
  caches (Tools > Cache management).
- **A site may raise its own numbers.** A site administrator can set `maxPages` and `maxMainResources`
  up to 2000 whatever the module configuration says. A generation stays bounded by the limits of the
  server: time, size, pages rendered at once.
- **Large sites.** The query that finds the contents shown as a page was not measured on a large
  repository.
- **Markdown versions of pages** are rendered at each request, not cached by the module.
- **The pages of a chained section** are still listed in the file of the site: chaining adds a way to
  the detail, it does not free room.
- Plurals of the English texts are not handled ("1 pages").

## Build and tests

Maven 3.8 and Java 17 (pinned in `mise.toml`). The front end (React 18, Moonstone, webpack Module
Federation for app-shell 3) is built by `frontend-maven-plugin` with Node 22 and Yarn 1. The preview
uses `marked` and `dompurify`.

```bash
mvn clean install
```

- Unit tests run with the build.
- `yarn lint` checks the JavaScript and the styles.
- End-to-end tests (Cypress, against a Jahia in Docker) are in [`tests/`](tests/README.md): the public
  URLs, the permission checks, generation, the panel, sections, contents and settings. They assert as
  ordinary users, not as `root`.

## Learn more

- [The llms.txt proposal](https://llmstxt.org/)
- [Jahia Store](https://store.jahia.com/)
