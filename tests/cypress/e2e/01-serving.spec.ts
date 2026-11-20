import {deleteSite, publishAndWaitJobEnding, uploadFile} from '@jahia/cypress';
import {createSiteWithPages} from '../support/fixtures';
import {fetchFile, gql, ROOT, saveAsRoot, SERVLET, STATUS} from '../support/llms';

/**
 * The public URLs. They are what crawlers, proxies and the modules that write `j:llms` themselves
 * (geo-readiness) depend on: status code, content type, and a published change served at once.
 */
describe('serving llms.txt and llms-full.txt', () => {
    const SITE = 'llmsServing';
    const LLMS = `/sites/${SITE}/llms.txt`;
    const FULL = `/sites/${SITE}/llms-full.txt`;

    before(() => {
        createSiteWithPages(SITE);
    });

    after(() => {
        deleteSite(SITE);
    });

    it('answers 404 for both files until one is saved', () => {
        fetchFile(LLMS).its('status').should('eq', 404);
        fetchFile(FULL).its('status').should('eq', 404);
    });

    it('does not publish the autocreated placeholder when llms-full.txt is saved alone', () => {
        saveAsRoot(SITE, {llmsFull: '# Full\n\nEverything.'});
        fetchFile(FULL).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.eq('# Full\n\nEverything.');
        });
        // The mixin autocreates j:llms = "# Title": that is not a file an editor wrote.
        fetchFile(LLMS).its('status').should('eq', 404);
    });

    it('serves a saved llms.txt as plain text, to a visitor without an account', () => {
        saveAsRoot(SITE, {llms: '# Site\n\n> Summary\n\n## Home\n\n- [Home](https://www.example.com/home.html)'});
        fetchFile(LLMS).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/plain;\s*charset=utf-8$/i);
            expect(response.headers['x-content-type-options']).to.eq('nosniff');
            expect(response.headers.etag, 'a validator').to.match(/^"[0-9a-f]{32}"$/);
            // Kept by a cache, but revalidated on each use: never served stale.
            expect(response.headers['cache-control']).to.eq('public, no-cache');
            expect(response.body).to.contain('# Site');
        });
    });

    it('answers 304 to a conditional request for an unchanged file', () => {
        fetchFile(LLMS).then(first => {
            const etag = first.headers.etag as string;
            fetchFile(LLMS, {'If-None-Match': etag}).then(second => {
                expect(second.status).to.eq(304);
                expect(second.headers.etag).to.eq(etag);
                expect(second.body).to.eq('');
            });
        });
    });

    it('serves a change as soon as it is published, also to a conditional request', () => {
        fetchFile(LLMS).then(before => {
            const stale = before.headers.etag as string;
            saveAsRoot(SITE, {llms: '# Site, second edition'});
            fetchFile(LLMS, {'If-None-Match': stale}).then(after => {
                expect(after.status, 'the old validator no longer matches').to.eq(200);
                expect(after.body).to.eq('# Site, second edition');
                expect(after.headers.etag).to.not.eq(stale);
            });
        });
    });

    it('removes a file saved blank: its URL answers 404 again', () => {
        saveAsRoot(SITE, {llmsFull: ' '});
        fetchFile(FULL).its('status').should('eq', 404);
    });

    it('reports on the server whether each file is served and current', () => {
        gql(ROOT(), STATUS, {siteKey: SITE}).then(response => {
            const status = response.body.data.llms.status;
            expect(status.llms).to.deep.eq({served: true, current: true});
            expect(status.llmsFull).to.deep.eq({served: false, current: false});
        });
    });

    it('answers 404, not an error, for a site that does not exist or a malformed key', () => {
        fetchFile('/sites/noSuchSite/llms.txt').its('status').should('eq', 404);
        ['../systemsite', 'a/b', 'x'.repeat(65), 'noSuchSite'].forEach(key => {
            cy.request({url: SERVLET, qs: {site: key}, failOnStatusCode: false}).its('status').should('eq', 404);
        });
    });

    it('keeps the site named by the path when a query string names another', () => {
        cy.request({url: LLMS, qs: {site: 'systemsite', file: 'full'}, failOnStatusCode: false}).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.eq('# Site, second edition');
        });
    });

    it('leaves alone a media file and other URLs that merely end in llms.txt', () => {
        uploadFile('media-llms.txt', `/sites/${SITE}/files`, 'llms.txt', 'text/plain');
        publishAndWaitJobEnding(`/sites/${SITE}/files`, ['en']);
        fetchFile(`/files/live/sites/${SITE}/files/llms.txt`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.contain('a media file that happens to be named llms.txt');
        });
        // Not ours to answer: whatever Jahia says for these paths, it is not a site's file.
        ['/modules/some-module/llms.txt', '/some/folder/llms-full.txt'].forEach(url => {
            fetchFile(url).its('status').should('be.oneOf', [400, 404]);
        });
    });

    it('serves the files at the root of the site\'s own host', () => {
        // The site's server name is <key>.test; the test instance is reached as another host.
        const host = {Host: `${SITE}.test`.toLowerCase()};
        fetchFile('/llms.txt', host).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.eq('# Site, second edition');
        });
    });

    /**
     * The contract with modules that write the file themselves. geo-readiness adds the mixin, sets
     * j:llms through the repository, publishes, then reads /llms.txt back and expects it there.
     */
    it('serves a j:llms written by another module through the repository', () => {
        const OTHER = `# Written by another module ${Date.now()}`;
        gql(ROOT(), `mutation ($path: String!, $value: String!) {
            jcr { mutateNode(pathOrId: $path) {
                addMixins(mixins: ["jmix:llms"])
                mutateProperty(name: "j:llms") { setValue(value: $value) }
                publish
            } }
        }`, {path: `/sites/${SITE}`, value: OTHER}).then(response => {
            expect(response.body.errors, 'the repository mutation').to.eq(undefined);
        });
        // Publication is a background job: the file follows it, without any cache lifetime to wait out.
        cy.waitUntil(() => fetchFile(LLMS).then(response => response.body === OTHER), {timeout: 30000, interval: 1000});
        fetchFile(LLMS).its('headers').its('content-type').should('match', /^text\/plain/i);
    });
});
