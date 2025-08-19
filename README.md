[![CircleCI](https://circleci.com/gh/Jahia/llms/tree/master.svg?style=svg)](https://circleci.com/gh/Jahia/llms/tree/master)\
![GitHub tag (latest by
version)](https://img.shields.io/github/v/tag/Jahia/llms?sort=semver)\
![License](https://img.shields.io/github/license/jahia/llms)

# Jahia `llms.txt` Manager Module

This module provides an interface in Jahia to manage **`llms.txt`
configuration files** directly from site settings.

📖 Learn more about the `llms.txt` initiative at <https://llmstxt.org/>.

The goal of `llms.txt` is to give Large Language Models (LLMs) a
**structured, curated entry point** into your site, highlighting the
most relevant resources (in Markdown) so they can use your content more
effectively at **inference time**.

------------------------------------------------------------------------

## 🚀 Features

-   Adds a **site settings panel** in Jahia for configuring `llms.txt`.\
-   Supports **per-site configuration**, aggregated at the server root
    (`/llms.txt`).\
-   Automatically prefixes entries with the site path when multiple
    sites are managed.\
-   Fully integrated into the Jahia UI---no manual file creation or
    editing required.

------------------------------------------------------------------------

## 📂 Example Usage

Imagine two sites: `mySite` and `mySite2`.

### `mySite` configuration

``` markdown
# mySite Documentation

> Primary API documentation for mySite, ideal for developers.

## Overview
- /sites/mySite/docs/overview
- /sites/mySite/docs/api

## Tutorials
- /sites/mySite/docs/tutorials/getting-started

## Optional Resources
- /sites/mySite/docs/changelog
```

### `mySite2` configuration

``` markdown
# mySite2 Docs

> Documentation for the second site with advanced usage guides.

## Guides
- /sites/mySite2/docs/setup
- /sites/mySite2/docs/advanced

## Optional
- /sites/mySite2/community
```

### Resulting aggregated `llms.txt`

``` markdown
# mySite Documentation

> Primary API documentation for mySite, ideal for developers.

## Overview
- /sites/mySite/docs/overview
- /sites/mySite/docs/api

## Tutorials
- /sites/mySite/docs/tutorials/getting-started

## Optional Resources
- /sites/mySite/docs/changelog
Path: /sites/mySite

---

# mySite2 Docs

> Documentation for the second site with advanced usage guides.

## Guides
- /sites/mySite2/docs/setup
- /sites/mySite2/docs/advanced

## Optional
- /sites/mySite2/community
Path: /sites/mySite2
```

------------------------------------------------------------------------

## 💡 Why This Matters

-   **LLMs gain clarity** → The structured format (titles, summaries,
    sectioned links, optional info) makes it easier for language models
    to use your site content.\
-   **Efficient** → Reduces noise from full HTML pages and preserves
    valuable context space for LLMs.\
-   **Editor-friendly** → Empower content editors to manage `llms.txt`
    within Jahia---no manual file handling required.
