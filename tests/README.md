# llms: end-to-end tests

Cypress specs run against a real Jahia with the module deployed. They cover what unit tests cannot:
the public URLs, the permission checks of the GraphQL fields, generation from a published site, and
the panel in jContent.

| Spec | What it proves |
|---|---|
| `01-serving` | `/llms.txt` and `/llms-full.txt`: status codes, `text/plain`, `ETag` and 304, a published change served at once, the `# Title` placeholder never served, and a `j:llms` written by another module (geo-readiness) served as is |
| `02-authorization` | generate, save and status are refused to a guest, to a user with no role on the site and to a publisher of another site, and allowed to a publisher of the site |
| `03-generation` | only published pages are listed, links use the public URL, nothing is stored, invalid public URLs and languages are refused |
| `04-panel` | the panel as a publisher: tabs, field names for assistive technology, the invalid URL error, generate then edit then save and publish |

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
     --form-string 'script=[{"installBundle":"llms-1.1.0-SNAPSHOT.jar","autoStart":true,"uninstallPreviousVersion":true,"forceUpdate":true}]' \
     --form file=@../target/llms-1.1.0-SNAPSHOT.jar
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
