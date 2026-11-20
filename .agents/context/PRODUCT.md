# Product

## Register

product

## Users

Editors and site administrators of a Jahia site who hold the publish permission. They work in jContent
(Additional > SEO), between other site-wide settings such as robots.txt and the sitemap. They are not
developers: they know their site's pages, not Markdown conventions or what an AI crawler expects. They
come here rarely, to set the files up once and to refresh them after the site changes.

## Product Purpose

A jContent panel to write, generate and publish a site's `llms.txt` and `llms-full.txt`. Success is an
editor who generates a file, understands what it contains and where it will be served, corrects it, and
publishes it without help, and who can tell at a glance whether what visitors get is what was saved.

## Brand Personality

Jahia's own product voice: plain, precise, calm. The panel should feel like part of jContent, not like
a module with its own look. Text states facts ("Published", "3 pages could not be rendered") and never
sells the feature.

## Anti-references

- A module that brings its own colours, fonts or card styles and stands out from the rest of jContent.
- An "AI" look: gradients, sparkles, glowing accents.
- Dashboards of metrics for their own sake; the stats line is a check, not a headline.
- Dense developer tooling that assumes the editor reads Markdown fluently.

## Design Principles

1. Moonstone as it is. Use the design system's components and tokens; override one only to repair an
   accessibility defect, and say why in a comment.
2. Nothing is published by surprise. Generation proposes, the editor reviews, one explicit action
   publishes; the state of each file is always visible.
3. Say what is served. Each file shows its public URL and whether the served version is the saved one.
4. One job per screen area: settings for generation, then the file. No decoration between them.
5. Errors name the cause and the next step, next to the control they concern.

## Accessibility & Inclusion

WCAG 2.2 AA. Every control has a programmatic name, errors are tied to their field, status changes are
announced through a live region, the panel is fully usable with the keyboard, and focus is never lost
after an action. Motion respects `prefers-reduced-motion`. Known gap, owned by Moonstone: the contrast
of the selected tab and of the default focus outline.
