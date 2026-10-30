# llms: agent harness

Generic Jahia knowledge lives in AIStartupKit (`../AIStartupKit`, the sole reference): read its
`CLAUDE.md`, then the `jahia-osgi-ui-extension`, `jahia-osgi-module` and `jahia-java-security` skills.
This file holds what is specific to this module.

## Architecture

| Part | Where | Role |
|---|---|---|
| Panel | `src/javascript/Llms/` | jContent > Additional > SEO > llms.txt (`jcontent-siteSettingsSeo:75`), React 18, Moonstone, webpack (app-shell 3) |
| GraphQL | `src/main/java/.../graphql/` | `llms { generate }` (publish on the site), `llms { save }` (modify + publish) |
| Service | `.../service/LlmsService.java` | generation and save; limits from `LlmsConfig` (PID `org.jahia.modules.llms`) |
| Walk | `.../service/internal/SiteWalker.java` | pages as `guest` in `live`, sections, exclusions |
| Markdown | `.../service/internal/HtmlToMarkdown.java` | jsoup (embedded), main content only; unit-tested |
| Fetch | `.../service/internal/PageFetcher.java` | anonymous GET of a page's render URL on 127.0.0.1, same-origin redirects only |
| Serving | `.../servlet/LlmsServlet.java` + `META-INF/last-urlrewrite-llms.xml` | `/llms.txt`, `/llms-full.txt`, `/sites/<key>/...` |

Storage: `jmix:llms` on the site node, `j:llms`, `j:llmsFull`, `j:llmsBaseUrl`. content-governance
(`../content-governance`) also writes `j:llms` from its GEO dashboard: keep the mixin and property
names stable.

## Traps found here

- **The panel route is `/jahia/jcontent/<site>/<lang>/apps/siteSettingsSeo/llms`**, not
  `.../siteSettingsSeo/llms` (that URL redirects to the home page).
- **`mvn clean` empties `javascript/apps`** (maven-clean-plugin fileset). A build with
  `-Dskip.yarn` after a clean packages a jar without the panel: run `yarn build:production` first, then
  `mvn package` without `clean`, or the full `mvn clean install`.
- **Generated links use the public URL saved per site, never the request host.** The fetch for
  llms-full.txt goes to this Jahia's own address (config `renderBaseUrl`, else 127.0.0.1 on the request's
  local port), and links between listed pages are mapped back to their public URL.
- Generation of llms-full.txt is limited to one run per site at a time (`IllegalStateException`,
  answered as a GraphQL error).

## Verify

Local Jahia on 8080: `dsfr-demo` and `dsfr-dev` have the module enabled (dsfr-dev is the default site,
so `http://localhost:8080/llms.txt` serves it). After a deploy: generate both files from the panel,
save, then `curl` `/sites/<key>/llms.txt` and `/sites/<key>/llms-full.txt`.
