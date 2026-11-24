import {addNode, addVanityUrl, createSite, deleteSite, publishAndWaitJobEnding, setNodeProperty, uploadFile} from '@jahia/cypress';
import {addUnpublishedPage, createSiteWithPages, TEMPLATE_SET} from '../support/fixtures';
import {fetchFile, ROOT, saveAsRoot, signOut} from '../support/llms';

/**
 * The Markdown version of a page, at the page's own URL followed by .md, as the llms.txt proposal asks.
 * A visitor gets as Markdown exactly what they may read as HTML, and nothing they may not.
 */
describe('the Markdown version of a page (page.html.md)', () => {
    const SITE = 'llmsPageMd';
    const ABOUT = `/sites/${SITE}/home/about.html`;

    before(() => {
        createSiteWithPages(SITE);
        addUnpublishedPage(SITE, 'draft', 'Unpublished draft');
        // Saved before any page is rendered: the links of a page are cached with the page.
        saveAsRoot(SITE, {llms: '# Site\n\n## Home\n\n- [About us](https://www.example.com/home/about.html)'});
    });

    after(() => {
        serveMarkdown(true);
        deleteSite(SITE);
    });

    /** Switches the Markdown versions on or off for the whole Jahia, as an operator does in the .cfg file. */
    const serveMarkdown = (served: boolean) => {
        cy.request({
            method: 'POST',
            url: '/modules/api/provisioning',
            auth: {user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD')},
            headers: {'Content-Type': 'application/json'},
            body: [{editConfiguration: 'org.jahia.modules.llms', properties: {servePageMarkdown: String(served)}}]
        }).its('status').should('eq', 200);
        signOut();
        // The configuration reaches the module a moment after the call returns.
        cy.wait(3000);
    };

    /** Asks again until the file holds the text: what Jahia kept of a page goes a moment after a publication. */
    const untilItHolds = (path: string, text: string, tries = 15): void => {
        fetchFile(path).then(response => {
            if (tries > 0 && !String(response.body).includes(text)) {
                cy.wait(1000);
                untilItHolds(path, text, tries - 1);
                return;
            }

            expect(response.status).to.eq(200);
            expect(response.body).to.contain(text);
        });
    };

    /** The same request as a signed-in administrator, who may read what no visitor may. */
    const fetchAsRoot = (path: string) => {
        cy.clearCookies();
        return cy.request({url: path, auth: {user: ROOT()!.name, pass: ROOT()!.password}, failOnStatusCode: false}).then(response => {
            signOut();
            return cy.wrap(response, {log: false});
        });
    };

    it('serves a published page as Markdown, under its title, and names the page as the reference', () => {
        fetchFile(`${ABOUT}.md`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/markdown;\s*charset=utf-8$/i);
            expect(response.headers['x-content-type-options']).to.eq('nosniff');
            // What names the page to a reader that only has the file: its title, the address of the page
            // as HTML, which is the one to quote, and the day it was last published.
            expect(response.body.startsWith(`---\ntitle: "About us"\nurl: "${Cypress.config('baseUrl')}${ABOUT}"\n`), 'header').to.eq(true);
            expect(response.body).to.match(/\ndescription: "Who we are"\nlast_updated: \d{4}-\d{2}-\d{2}\n---\n\n# About us\n/);
            expect(response.body, 'no page chrome').to.not.match(/Login|All Rights Reserved/);
            // Markdown has no <head>: the page it is another form of, and the llms.txt that covers it, are a header.
            expect(response.headers.link).to.eq(
                `<${Cypress.config('baseUrl')}${ABOUT}>; rel="canonical", </sites/${SITE}/llms.txt>; rel="describedby"`);
        });
    });

    /**
     * What lets an agent holding a page find the rest without guessing: the page names its Markdown
     * version and the llms.txt of its site. Each link is followed, not only read.
     */
    it('names in each page its Markdown version and the llms.txt of the site, and both answer', () => {
        fetchFile(ABOUT).then(response => {
            expect(response.status).to.eq(200);
            const head = new DOMParser().parseFromString(response.body, 'text/html').head;
            const markdown = head.querySelector('link[rel="alternate"][type="text/markdown"]');
            const llms = head.querySelector('link[rel="describedby"]');
            expect(markdown, 'link to the Markdown version').to.not.eq(null);
            expect(llms, 'link to llms.txt').to.not.eq(null);
            expect(markdown!.getAttribute('href')).to.eq(`${ABOUT}.md`);
            expect(llms!.getAttribute('href')).to.eq(`/sites/${SITE}/llms.txt`);

            fetchFile(markdown!.getAttribute('href') as string).then(md => {
                expect(md.status).to.eq(200);
                expect(md.headers['content-type']).to.match(/^text\/markdown/i);
            });
            fetchFile(llms!.getAttribute('href') as string).then(file => {
                expect(file.status).to.eq(200);
                expect(file.body).to.contain('# Site');
            });
        });
    });

    it('answers 304 to a conditional request for an unchanged page', () => {
        fetchFile(`${ABOUT}.md`).then(first => {
            fetchFile(`${ABOUT}.md`, {'If-None-Match': first.headers.etag as string}).its('status').should('eq', 304);
        });
    });

    it('serves it at the short URL of the site\'s own host too', () => {
        fetchFile('/home/about.html.md', {Host: `${SITE}.test`.toLowerCase()}).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.match(/\n---\n\n# About us\n/);
        });
    });

    /**
     * The address of the page with .md in place of .html: the Markdown view Jahia renders by itself.
     * One page, one Markdown version, whichever of the two addresses asks for it.
     */
    it('serves the same Markdown at the address of the page ending in .md', () => {
        const short = `/sites/${SITE}/home/about.md`;
        fetchFile(`${ABOUT}.md`).then(long => {
            fetchFile(short).then(response => {
                expect(response.status).to.eq(200);
                expect(response.headers['content-type']).to.match(/^text\/markdown;\s*charset=utf-8$/i);
                expect(response.headers['x-content-type-options']).to.eq('nosniff');
                expect(response.body).to.eq(long.body);
                expect(response.headers.etag).to.eq(long.headers.etag);
                // The reference is the page a visitor reads, not one of its Markdown addresses.
                expect(response.headers.link).to.eq(
                    `<${Cypress.config('baseUrl')}${ABOUT}>; rel="canonical", </sites/${SITE}/llms.txt>; rel="describedby"`);
                fetchFile(short, {'If-None-Match': response.headers.etag as string}).its('status').should('eq', 304);
            });
        });
        fetchFile('/home/about.md', {Host: `${SITE}.test`.toLowerCase()}).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.match(/\n---\n\n# About us\n/);
            expect(response.headers.link).to.contain('/home/about.html>; rel="canonical"');
        });
        fetchFile(`/sites/${SITE}/home/no-such-page.md`).its('status').should('eq', 404);
        fetchFile(`/sites/${SITE}/home/draft.md`).its('status').should('eq', 404);
    });

    it('renders the node of the address, whatever node a visitor names in the query string', () => {
        fetchFile(`/sites/${SITE}/home/about.md`).then(plain => {
            cy.request({
                url: `/sites/${SITE}/home/about.md`,
                qs: {node: `/cms/render/live/en/sites/${SITE}/home/draft`},
                failOnStatusCode: false
            }).then(response => {
                expect(response.status).to.eq(200);
                expect(response.body).to.eq(plain.body);
            });
        });
        cy.request({url: '/modules/llmspage', qs: {node: `/cms/render/live/en/sites/${SITE}/home/about`}, failOnStatusCode: false})
            .its('status').should('eq', 404);
    });

    /**
     * Jahia keeps the Markdown of a page as it keeps its HTML. What it kept must go when the page is
     * published again: its own properties, and a content inside it.
     */
    it('follows the publication of the page and of a content inside it', () => {
        const CONTACT = `/sites/${SITE}/home/contact`;
        addNode({
            parentPathOrId: `/sites/${SITE}/home`,
            primaryNodeType: 'jnt:page',
            name: 'contact',
            properties: [{name: 'jcr:title', value: 'Contact', language: 'en'}, {name: 'j:templateName', value: 'simple'}],
            children: [{
                name: 'area-main',
                primaryNodeType: 'jnt:contentList',
                children: [{
                    name: 'text',
                    primaryNodeType: 'jnt:bigText',
                    properties: [{name: 'text', value: '<p>Write to us first.</p>', language: 'en'}]
                }]
            }]
        });
        publishAndWaitJobEnding(CONTACT, ['en']);
        // Both addresses are asked, so both are kept by Jahia before anything changes.
        untilItHolds(`${CONTACT}.md`, 'Write to us first.');
        untilItHolds(`${CONTACT}.html.md`, 'Write to us first.');

        setNodeProperty(`${CONTACT}/area-main/text`, 'text', '<p>Call us instead.</p>', 'en');
        // Changed in the editing workspace only: visitors, and the Markdown, still have the published text.
        fetchFile(`${CONTACT}.md`).its('body').should('contain', 'Write to us first.').and('not.contain', 'Call us instead.');
        publishAndWaitJobEnding(CONTACT, ['en']);
        untilItHolds(`${CONTACT}.md`, 'Call us instead.');
        untilItHolds(`${CONTACT}.html.md`, 'Call us instead.');
        fetchFile(`${CONTACT}.md`).its('body').should('not.contain', 'Write to us first.');

        setNodeProperty(CONTACT, 'jcr:title', 'Contact us', 'en');
        publishAndWaitJobEnding(CONTACT, ['en']);
        untilItHolds(`${CONTACT}.md`, '# Contact us');
    });

    /**
     * Jahia keeps one Markdown of a page for every visitor, and a request names the host it likes: the
     * links of the Markdown carry the host of the request that reads them, never the one of the request
     * that happened to make Jahia render the page.
     */
    it('writes its links on the host of each request, not on the host of whoever asked first', () => {
        const LINKS = `/sites/${SITE}/home/links`;
        addNode({
            parentPathOrId: `/sites/${SITE}/home`,
            primaryNodeType: 'jnt:page',
            name: 'links',
            properties: [{name: 'jcr:title', value: 'Links', language: 'en'}, {name: 'j:templateName', value: 'simple'}],
            children: [{
                name: 'area-main',
                primaryNodeType: 'jnt:contentList',
                children: [{
                    name: 'text',
                    primaryNodeType: 'jnt:bigText',
                    properties: [{name: 'text', value: `<p><a href="/sites/${SITE}/home.html">Back home</a></p>`, language: 'en'}]
                }]
            }]
        });
        publishAndWaitJobEnding(LINKS, ['en']);
        // The first request for this page, so the one Jahia renders it for, names a host of its own.
        fetchFile(`${LINKS}.md`, {Host: 'someone-else.test'}).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.contain(`[Back home](http://someone-else.test/sites/${SITE}/home.html)`);
        });
        fetchFile(`${LINKS}.md`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.contain(`[Back home](${Cypress.config('baseUrl')}/sites/${SITE}/home.html)`);
            expect(response.body).to.not.contain('someone-else.test').and.to.not.contain('.invalid');
        });
        fetchFile(`${LINKS}.html.md`).its('body').should('not.contain', 'someone-else.test').and('not.contain', '.invalid');
    });

    /**
     * The Markdown version is the one of a visitor without an account, for everyone: a signed-in
     * administrator gets no draft and nothing a visitor may not read, and what Jahia keeps is one version.
     */
    it('gives a signed-in user the version of a visitor without an account', () => {
        fetchFile(`${ABOUT}.md`).then(visitor => {
            fetchAsRoot(`/sites/${SITE}/home/about.md`).then(response => {
                expect(response.status).to.eq(200);
                expect(response.body).to.eq(visitor.body);
            });
            fetchAsRoot(`${ABOUT}.md`).its('body').should('eq', visitor.body);
        });
        fetchAsRoot(`/sites/${SITE}/home/draft.md`).then(response => {
            expect(response.status).to.eq(404);
            expect(String(response.body)).to.not.contain('Unpublished draft');
        });
        fetchAsRoot(`/sites/${SITE}/home/draft.html.md`).its('status').should('eq', 404);
    });

    it('serves a page at its vanity URL followed by .md, on the hosts that serve the vanity URL', () => {
        const host = {Host: `${SITE}.test`.toLowerCase()};
        addVanityUrl(`/sites/${SITE}/home/about`, 'en', '/who-we-are');
        // A vanity URL counts for visitors once it is published.
        fetchFile('/who-we-are.md', host).its('status').should('eq', 404);
        publishAndWaitJobEnding(`/sites/${SITE}/home/about`, ['en']);
        fetchFile('/who-we-are.md', host).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/markdown;\s*charset=utf-8$/i);
            expect(response.body).to.match(/\n---\n\n# About us\n/);
            expect(response.headers.link).to.contain('/who-we-are>; rel="canonical"');
            // The address written in the file is the one that was asked for, as in the header.
            expect(response.body).to.contain('\nurl: "http://llmspagemd.test/who-we-are"\n');
        });
        // The address of the page keeps its own Markdown version.
        fetchFile('/home/about.html.md', host).its('status').should('eq', 200);
        // A vanity URL is a whole address: nothing is guessed from a part of one.
        fetchFile('/who-we.md', host).its('status').should('eq', 404);
        // Jahia answers a vanity URL on a host that is not its site's (a host several sites share, as an
        // authoring one): its Markdown version is there too.
        fetchFile('/who-we-are', {Host: 'shared-host.test'}).its('status').should('eq', 200);
        fetchFile('/who-we-are.md', {Host: 'shared-host.test'}).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.match(/\n---\n\n# About us\n/);
        });
    });

    /**
     * The Markdown version is a view of the module, and a view has an address of its own in Jahia: the
     * address of the node followed by its template type. Served there it would leave as HTML, without
     * the headers of a Markdown version and with the text of the page no longer escaped.
     */
    it('answers the Markdown of a page at its Markdown addresses and nowhere else', () => {
        [
            `/sites/${SITE}/home/about.md/`,
            `/sites/${SITE}/home/about.html.md.ajax`,
            `/sites/${SITE}/home/about.md.ajax`,
            `/cms/render/live/en/sites/${SITE}/home/about.md/`
        ].forEach(address => {
            fetchFile(address).then(response => {
                expect(response.status, address).to.eq(404);
                expect(String(response.body), address).to.not.contain('# About us');
                expect(String(response.body), address).to.not.contain('.invalid');
            });
        });
        // Asked for right after, the Markdown version is there: nothing was kept from the refusals.
        fetchFile(`${ABOUT}.md`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/markdown/i);
            expect(response.headers['x-content-type-options']).to.eq('nosniff');
        });
    });

    /** What a visitor adds to the address changes nothing of what Jahia keeps for everyone. */
    it('renders the page the same whatever a visitor adds to its address', () => {
        fetchFile(`${ABOUT}.md`).then(plain => {
            fetchFile(`${ABOUT}.md?page=2&q=%3Cb%3Ex`).then(response => {
                expect(response.status).to.eq(200);
                expect(response.body).to.eq(plain.body);
                expect(response.body).to.not.contain('<b>');
            });
        });
    });

    it('has no Markdown version of a page that does not exist or is not published', () => {
        fetchFile(`/sites/${SITE}/home/no-such-page.html.md`).its('status').should('eq', 404);
        fetchFile(`/sites/${SITE}/home/draft.html.md`).then(response => {
            expect(response.status).to.eq(404);
            expect(String(response.body)).to.not.contain('Unpublished draft');
        });
    });

    it('has no Markdown version of a page closed to visitors without an account', () => {
        cy.executeGroovy('groovy/breakAclInheritance.groovy', {NODE_PATH: `/sites/${SITE}/home/about`});
        cy.clearCookies();
        // The fixture itself: the page is refused as HTML. Its Markdown version must be refused as well.
        fetchFile(ABOUT).its('status').should('be.oneOf', [401, 403, 404]);
        fetchFile(`${ABOUT}.md`).then(response => {
            expect(response.status).to.eq(404);
            expect(String(response.body)).to.not.contain('About us');
        });
        // What Jahia kept of the page while it was open is not served either, at any of its addresses.
        fetchFile(`/sites/${SITE}/home/about.md`).then(response => {
            expect(response.status).to.eq(404);
            expect(String(response.body)).to.not.contain('About us');
        });
        fetchFile('/who-we-are.md', {Host: `${SITE}.test`.toLowerCase()}).its('status').should('eq', 404);
    });

    it('renders the page of the URL, whatever a visitor adds to the query string', () => {
        const HOME = `/sites/${SITE}/home.html`;
        fetchFile(`${HOME}.md`).then(plain => {
            expect(plain.status).to.eq(200);
            cy.request({url: `${HOME}.md`, qs: {path: '/sites/systemsite/home.html'}, failOnStatusCode: false}).then(response => {
                expect(response.status).to.eq(200);
                expect(response.body).to.eq(plain.body);
            });
        });
    });

    it('is reached through the URL of a page only: the servlet answers nothing when called directly', () => {
        cy.request({url: '/modules/llmspage', qs: {path: `/sites/${SITE}/home.html`}, failOnStatusCode: false})
            .its('status').should('eq', 404);
        cy.request({url: '/modules/llmspage', qs: {path: '/modules/graphql'}, failOnStatusCode: false})
            .its('status').should('eq', 404);
    });

    it('leaves a Markdown file of the media library at its own URL', () => {
        uploadFile('notes.md', `/sites/${SITE}/files`, 'notes.md', 'text/markdown');
        publishAndWaitJobEnding(`/sites/${SITE}/files`, ['en']);
        fetchFile(`/files/live/sites/${SITE}/files/notes.md`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.contain('A Markdown file of the media library.');
        });
    });

    /** The Markdown version is a view of the module: Jahia finds it on the sites that use the module. */
    it('has no Markdown version on a site where the module is not enabled', () => {
        const BARE = 'llmsPageMdBare';
        deleteSite(BARE);
        createSite(BARE, {templateSet: TEMPLATE_SET, serverName: `${BARE}.test`, locale: 'en', languages: 'en'});
        publishAndWaitJobEnding(`/sites/${BARE}`, ['en']);
        fetchFile(`/sites/${BARE}/home.html`).its('status').should('eq', 200);
        fetchFile(`/sites/${BARE}/home.md`).its('status').should('eq', 404);
        fetchFile(`/sites/${BARE}/home.html.md`).its('status').should('eq', 404);
        deleteSite(BARE);
    });

    it('serves no Markdown version at all when the operator switched them off', () => {
        const HOME = `/sites/${SITE}/home`;
        fetchFile(`${HOME}.md`).its('status').should('eq', 200);
        serveMarkdown(false);
        // Kept by Jahia or not: none of the addresses answers.
        fetchFile(`${HOME}.md`).its('status').should('eq', 404);
        fetchFile(`${HOME}.html.md`).its('status').should('eq', 404);
        fetchFile('/home.md', {Host: `${SITE}.test`.toLowerCase()}).its('status').should('eq', 404);
        // The page itself stays, and no longer names a Markdown version.
        fetchFile(`${HOME}.html`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.not.contain('type="text/markdown"');
        });
        serveMarkdown(true);
        fetchFile(`${HOME}.md`).its('status').should('eq', 200);
    });
});
