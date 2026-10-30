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
- Pages hidden from the navigation menu, marked `noindex` (`jmix:noindex`) or without a published
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
| `renderBaseUrl` | blank | Scheme, host and port this Jahia answers on from inside its own container. Blank: `http://127.0.0.1` on the port the editor's request came in on. Set it when Jahia sits behind a connector on another port. |

## API

The panel uses two GraphQL fields, open to signed-in users with the right permissions on the site:

```graphql
# publish on the site
query { llms { generate(siteKey: "mySite", language: "en", baseUrl: "https://www.example.com", full: false) {
    content pages failedPages truncated } } }

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
