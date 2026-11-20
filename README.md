![GitHub tag (latest by version)](https://img.shields.io/github/v/tag/Jahia/llms?sort=semver)
![License](https://img.shields.io/github/license/jahia/llms)

# Jahia `llms.txt` Manager

Write, generate and publish a site's [`llms.txt`](https://llmstxt.org/) and `llms-full.txt` from jContent.
No file to edit by hand, no deployment.

- **llms.txt** is an index of the site for AI assistants: the site's name, a one-line summary, then its
  pages by section, each with a link and its description.
- **llms-full.txt** holds the text of those pages, converted to Markdown, so an assistant can read the
  whole site in one file.

## Features

- A panel in **jContent > Additional > SEO > llms.txt**, built with Jahia's Moonstone components: one tab
  per file, a status per file (not created, unsaved changes, published), and **Save and publish** in the
  header.
- **Generate** proposes either file from the site's published pages. You review and edit the text before
  anything is saved; **Discard changes** brings back the saved file.
- Both files are served at the site's domain root (`/llms.txt`, `/llms-full.txt`) and under
  `/sites/<siteKey>/llms.txt` and `/sites/<siteKey>/llms-full.txt`, whatever the host.
- Per-site: each site has its own files, its own public URL and its own language choice.

## Installation

1. Install the module (Jahia 8.2 or later, jContent 3) from the Jahia Store or the module manager.
2. Enable it on the site: **Administration > Sites > (site) > Modules**, or the site's module list.
3. Open **jContent > Additional > SEO > llms.txt**. You need the **publish** permission on the site.

## Generating the files

1. Pick the **Language** (the site's default language is preselected). The site has one `llms.txt` and
   one `llms-full.txt`, in the language you generate them in.
2. Check the **Public site URL**: the scheme, host and optional port the links start with, for example
   `https://www.example.com`. It is preset from the site's server name, else from the address jContent
   runs on, and is saved with the files.
3. Select **Generate** on the tab of the file, review the text, edit it if needed, then select
   **Save and publish**.

What generation reads:

- Only pages **published** and visible to a visitor **without an account**: the pages are read as the
  guest user in the live workspace.
- Pages hidden from the navigation menu, marked **No index** (`jseomix:noIndex`, the option of the sitemap module) or without a published
  translation in the chosen language are left out; their sub-pages are still listed.
- Descriptions come from the page's SEO description (`jcr:description`), titles from its title in the
  language.
- Sections: the home page and the first-level pages without sub-pages come first, then one section per
  first-level page with sub-pages, then the pages and menu groups beside the home page.
- **llms-full.txt** renders each listed page as a visitor gets it (anonymous request to this Jahia,
  output cache included) and keeps its main content (`<main>`), converted to Markdown. Links between
  listed pages use their public URL. Pages that fail to render are named after generation and left out.

Saving writes the files on the site node and publishes **the site node alone**, never its pages or
content. An empty file is removed, and its URL then answers 404.

## Configuration

Generation limits come from the OSGi configuration `org.jahia.modules.llms`. The module ships no file;
add `digital-factory-data/karaf/etc/org.jahia.modules.llms.cfg` to change a value:

| Key | Default | Meaning |
|---|---|---|
| `maxDepth` | `3` | Levels of pages below the home page that are listed |
| `maxPages` | `200` | Most pages one file lists |
| `fetchTimeoutSeconds` | `20` | Time allowed to render one page for llms-full.txt |
| `fetchThreads` | `4` | Pages rendered at the same time (8 at most) |
| `maxPageKb` | `2048` | Largest rendered page read |
| `maxFullKb` | `4096` | Largest llms-full.txt produced |
| `maxTotalSeconds` | `300` | Time allowed for one generation of llms-full.txt, all pages together. Pages not rendered in time are named after generation and left out. |
| `excludedNodeTypes` | `jseomix:noIndex, jcmix:hidePage, jacademix:hidePage` | Node types (mixins) of the pages left out of both files, separated by commas, as many as needed. Their sub-pages are still listed. The defaults are the **No index** option of the sitemap module and the "hidden in the menu" mark of the jahia.com and Academy templates. A type no installed module defines is ignored. Setting this key replaces the defaults: repeat the ones to keep. |
| `excludedTreeNodeTypes` | empty | Node types (mixins) of the pages left out of both files **with all their sub-pages**: put one on the page at the top of a section to leave the section out. |
| `servePageMarkdown` | `true` | Serve each published page as Markdown at its URL followed by `.md`. |
| `discoveryLinks` | `true` | Write in the head of each published page the links to its Markdown version and to the llms.txt of the site. |
| `linkToMarkdown` | `false` | In a generated llms.txt, link each page to its Markdown version rather than to the page. |
| `keepImages` | `false` | Write the images of a page (those with a text alternative) in llms-full.txt. Off, images are left out: a text reader gains little from them, and most are logos and decorations. |
| `renderBaseUrl` | blank | Scheme, host and port this Jahia answers on from inside its own container. Blank: `http://127.0.0.1` on the port the editor's request came in on. Set it when Jahia sits behind a connector on another port. |

A value out of range is brought back into it (for example `maxPages` between 1 and 2000, sizes up to
64 MB), and a `renderBaseUrl` that is not a scheme, a host and an optional port is ignored. Two
generations of llms-full.txt run at the same time on one Jahia at most, one per site.

## Serving

`/llms.txt` and `/llms-full.txt` answer with an `ETag`; a request with `If-None-Match` for an unchanged
file gets `304 Not Modified`. `Cache-Control` is `public, no-cache` for a visitor without an account
(`private, no-cache` for a signed-in user): a proxy or CDN may keep the file but revalidates it on each
request, so a published change is served at once.

## Markdown version of each page

The llms.txt proposal asks that what an llms.txt links to be readable by a language model: each page
as Markdown, at the page's own URL followed by `.md`. The module serves it:

```
https://www.example.com/home/about.html      the page
https://www.example.com/home/about.html.md   the same page as Markdown
```

- It is the page as a visitor without an account gets it, converted like a page of llms-full.txt. A page
  that is not published, or not open to visitors, has no Markdown version (404), as it has no HTML one.
- Served as `text/markdown`, with an `ETag` and a `Link: <page>; rel="canonical"` header.
- Only pages of a site: a Markdown file of the media library keeps its URL. A page reached only through
  a vanity URL that does not end in `.html` has no Markdown version yet.
- Each request renders the page: eight are converted at the same time at most, beyond which the answer
  is `503` with `Retry-After`.

### Discovery links

So that an agent holding a page finds the rest without guessing, each published page of a site where the
module is enabled carries two links in its `<head>`, as the proposal asks:

```html
<link rel="alternate" type="text/markdown" href="/home/about.html.md"/>
<link rel="describedby" href="/sites/mySite/llms.txt"/>
```

The first is written when the page has a Markdown version, the second when the site serves an llms.txt.
A Markdown version says the same in a `Link` header, with the page as `rel="canonical"`. The links are
cached with the page: a site that gets its first llms.txt shows the second link on a page once the cache
of that page is renewed. `discoveryLinks` (default `true`) switches them off.

Two configuration keys: `servePageMarkdown` (default `true`) switches this off; `linkToMarkdown`
(default `false`) makes a generated llms.txt link each page to its `.md` version, as the proposal
recommends. It is off by default because geo-readiness and content-governance compare the links of
llms.txt with the addresses of the pages: switch it on once they accept `.md` links.

## Other modules

Other modules build on this one, so the following is a contract:

- the module key `llms`, the mixin `jmix:llms` and the string property `j:llms` on the site node,
  published to live;
- `/llms.txt` and `/llms-full.txt` served as `text/plain`, a published change served at once;
- the autocreated `# Title` is not a file: its URL answers 404;
- the panel at `/jahia/jcontent/<site>/<lang>/apps/siteSettingsSeo/llms`;
- a file is served whether or not this module is enabled on the site.

geo-readiness writes `j:llms` from its dashboard, then reads `/llms.txt` back. content-governance only
reads: `j:llms` in live and both URLs, and it links to the panel.

## API

The panel uses five GraphQL fields, open to signed-in users with the right permissions on the site:

```graphql
# publish on the site: whether each file is served, and is the saved one
query { llms { status(siteKey: "mySite") { llms { served current } llmsFull { served current } } } }

# publish on the site
query { llms { generate(siteKey: "mySite", language: "en", baseUrl: "https://www.example.com", full: false) {
    content pages failedPages truncated } } }

# publish on the site: llms-full.txt renders every page, so it runs in the background.
# Start it, then read its progress until finished: each page and what became of it, then the file.
mutation { llms { startGeneration(siteKey: "mySite", language: "en", baseUrl: "https://www.example.com") } }
query { llms { generationProgress(siteKey: "mySite", id: "<id returned above>") {
    started total done finished error pages { title url state } generation { content pages failedPages truncated } } } }

# jcr:modifyProperties and publish on the site; an omitted file is left unchanged, a blank one removed
mutation { llms { save(siteKey: "mySite", llms: "# My site ...", llmsFull: null, baseUrl: "https://www.example.com") } }
```

The files are stored on the site node, with the `jmix:llms` mixin: `j:llms`, `j:llmsFull` and
`j:llmsBaseUrl`.

## Example

```markdown
# Example Corp

> Products and services of Example Corp.

## Home

- [Home](https://www.example.com/home.html): Welcome to Example Corp
- [Contact](https://www.example.com/contact.html)

## Products

- [Products](https://www.example.com/products.html): Everything we sell
- [Widget](https://www.example.com/products/widget.html): The widget, in three sizes
```

## Build

Maven 3.8 and Java 17 (pinned in `mise.toml`); the front end (React 18, webpack Module Federation for
app-shell 3) is built by `frontend-maven-plugin` with Node 22 and Yarn 1.

```bash
mvn clean install
```

## Learn more

- [llms.txt initiative](https://llmstxt.org/)
- [Jahia Store](https://store.jahia.com/)
