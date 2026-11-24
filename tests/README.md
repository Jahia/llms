# llms: end-to-end tests

Cypress specs run against a real Jahia with the module deployed. They cover what unit tests cannot:
the public URLs, the permission checks of the GraphQL fields, generation from a published site, the
configuration of a site, and the panel in jContent.

| Spec | What it proves |
|---|---|
| `01-serving` | `/llms.txt` and `/llms-full.txt`: status codes, `text/plain`, `ETag` and 304, a published change served at once, the `# Title` placeholder never served, and a `j:llms` written by another module (geo-readiness) served as is |
| `02-authorization` | generate, save and status are refused to a guest, to a user with no role on the site and to a publisher of another site, and allowed to a publisher of the site |
| `03-generation` | only published pages are listed, links use the public URL, nothing is stored, invalid public URLs and languages are refused |
| `04-panel` | the panel as a publisher: tabs, field names for assistive technology, the rules in force, the invalid URL error, generate then edit then save and publish; a generation of llms-full.txt in one block that stays in place, its advice, and the preview of the file |
| `05-exclusions` | a page marked **No index** (sitemap module) leaves both files once the mark is published, and comes back when it is removed; a published page closed to visitors is in no file |
| `06-page-markdown` | `page.html.md` and `page.md`: a published page as Markdown, under a YAML block that names it (title, address, description, day of publication), the same text at both addresses, 404 for a page that is not published or closed to visitors, the short URL on the site's host, the vanity URL of a page followed by `.md`, a visitor's query string ignored, the servlet unreachable directly, a `.md` media file left alone; the Markdown following a publication of the page and of a content inside it; a signed-in administrator given the version of a visitor; no Markdown version on a site without the module, nor when the operator switched them off; the page names its Markdown version and the llms.txt of its site, and both links answer |
| `07-sections` | the files of a section: generated from its page, served under its path and on the site's host, apart from the site's; the pages below a menu group offered as sections; the llms.txt of the site ending with the links to the section files; unknown paths refused; a page that is not published refused as a section, with the reason; a page names the nearest llms.txt; in the panel, choosing a scope, the refusal with unsaved changes, saving a section alone |
| `08-contents` | a published content shown as a page (a news item) listed after the pages, written in llms-full.txt, served as Markdown, at its address and at its vanity URL, and when its name has a space or an accent, and naming it in its head; llms.txt linked to the Markdown versions; the configuration of one site leaving the others alone; listing only marked pages; the option List in llms.txt shown only with its permission, checked as an editor |
| `09-settings` | the settings of a site as a site administrator: what may be set and what is inherited, nothing answered to a publisher or a visitor, a wrong value refused and nothing saved, the values of the server refused, one site written and emptied without touching another, the configuration of another site never overwritten; the Settings tab with its labels, defaults, errors and focus, and its absence for a publisher |

## Run

The harness is the `@jahia/cypress` one, as in the sibling modules.

All in Docker, as CI does:

```bash
bash ci.build.sh      # builds the tests image, stages ../target/*-SNAPSHOT.jar into ./artifacts
bash ci.startup.sh    # boots Jahia, provisions, runs the specs
```

From your machine, to write or debug a spec:

```bash
mvn -f ../pom.xml clean install
JAHIA_IMAGE=jahia/jahia-ee:8.2.3.2 JAHIA_HOST_PORT=8480 JAHIA_URL=http://localhost:8480 source set-env.sh
docker compose up -d                                   # Jahia alone
curl -u root:$SUPER_USER_PASSWORD -X POST -H "Content-Type: application/yaml" \
     --data-binary @provisioning-manifest-build.yml $JAHIA_URL/modules/api/provisioning
curl -u root:$SUPER_USER_PASSWORD -X POST $JAHIA_URL/modules/api/provisioning \
     --form-string 'script=[{"installBundle":"llms-2.0.1-SNAPSHOT.jar","autoStart":true,"uninstallPreviousVersion":true,"forceUpdate":true}]' \
     --form file=@../target/llms-2.0.1-SNAPSHOT.jar
yarn install && yarn e2e:ci      # or yarn e2e:debug
```

`set-env.sh` holds the defaults; anything already exported wins, and a git-ignored `.env` is sourced
first when present. Set `JAHIA_HOST_PORT` when port 8080 is taken on your machine.

## Things that cost time here

- **Sessions count against the license.** The license bundled in the Jahia image limits the signed-in
  users. A suite that opens one session per call ends with `503 License terms violation: the maximum
  number of users has been reached`, on jContent only, a few specs in. `gql()` in `support/llms.ts`
  signs out after each call and the panel spec signs out after each test: keep it that way. If the
  limit is reached anyway, restart the Jahia container.
- **Assert as an ordinary user.** `root` passes every permission check. `PUBLISHER` (editor-in-chief on
  one site) and `OUTSIDER` (no role) are created by `support/fixtures.ts`; every `gql()` call names its user.
- **No double quote in a test title.** `@jahia/cypress` writes each title into a Groovy string on the
  server; a `"` ends that string and the rest of the title is run as Groovy.
- **The panel route is `/jahia/jcontent/<site>/<lang>/apps/siteSettingsSeo/llms`.**
- **Re-run `ci.build.sh` after any change under `tests/`**, or the change never reaches the container.
