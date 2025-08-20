[![CircleCI](https://circleci.com/gh/Jahia/llms/tree/master.svg?style=svg)](https://circleci.com/gh/Jahia/llms/tree/master)  
![GitHub tag (latest by version)](https://img.shields.io/github/v/tag/Jahia/llms?sort=semver)  
![License](https://img.shields.io/github/license/jahia/llms)

# Jahia `llms.txt` Manager

The **`llms.txt` Manager Module** provides a simple interface in Jahia to configure and publish [`llms.txt`](https://llmstxt.org/) files directly from your site settings.  
No manual file editing is required — everything is handled from the Jahia UI.

---

## 📖 What is `llms.txt`?

`llms.txt` is an emerging standard designed to give Large Language Models (LLMs) a **structured and curated entry point** into your website.  
It highlights the most relevant resources (in Markdown), making your content easier for LLMs to use at **inference time**.

Think of it as **robots.txt for LLMs** — but instead of crawl instructions, it provides high-quality content references.

---

## 🚀 Features

- Dedicated **site settings panel** in Jahia  
- Supports **per-site configuration** at the domain root (`/llms.txt`)  
- Fully integrated into the Jahia UI — no manual file creation or deployment  

---

## 🔧 Installation & Access

1. Ask your **administrator** to install and activate the `llms.txt` Manager module  
   - Available from the Jahia Store  
2. Once activated, go to:  
   **jContent → Additional → SEO → llms.txt**  
3. Enter your `llms.txt` content in the editor panel  
4. Click **Save & Publish** (top-right) to make it live  

To verify: open your site URL and append `/llms.txt`  
👉 Example: `https://www.example.com/llms.txt`

---

## 📂 Example Usage

Imagine two sites in Jahia: `site1` and `site2`.

### Configuration for `site1`

```markdown
# site1 Documentation

> Primary API documentation for site1, ideal for developers.

## Overview
- /docs/overview
- /docs/api

## Tutorials
- /docs/tutorials/getting-started

## Optional Resources
- /docs/changelog
```

### Configuration for `site2`

```markdown
# site2 Docs

> Documentation for the second site with advanced usage guides.

## Guides
- /docs/setup
- /docs/advanced

## Optional
- /community
```
---

## 💡 Why This Matters

- **Clarity for LLMs** → Structured sections (titles, summaries, links) make it easier for models to find what matters  
- **Efficiency** → Reduces noise from full HTML pages and keeps LLMs focused on relevant resources  
- **Editor-friendly** → Content editors can manage everything directly in Jahia, without developer intervention  

---

## 🔗 Learn More

- [llms.txt Initiative](https://llmstxt.org/)  
- [Jahia Store](https://store.jahia.com/) (module availability)  
