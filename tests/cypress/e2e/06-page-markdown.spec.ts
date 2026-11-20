import {addVanityUrl, deleteSite, publishAndWaitJobEnding, uploadFile} from '@jahia/cypress';
import {addUnpublishedPage, createSiteWithPages} from '../support/fixtures';
import {fetchFile, saveAsRoot} from '../support/llms';

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
        deleteSite(SITE);
    });

    it('serves a published page as Markdown, under its title, and names the page as the reference', () => {
        fetchFile(`${ABOUT}.md`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/markdown;\s*charset=utf-8$/i);
            expect(response.headers['x-content-type-options']).to.eq('nosniff');
            expect(response.body).to.match(/^# About us\n/);
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
            expect(response.body).to.match(/^# About us\n/);
        });
    });

    it('serves a page at its vanity URL followed by .md, on the host of its site', () => {
        const host = {Host: `${SITE}.test`.toLowerCase()};
        addVanityUrl(`/sites/${SITE}/home/about`, 'en', '/who-we-are');
        // A vanity URL counts for visitors once it is published.
        fetchFile('/who-we-are.md', host).its('status').should('eq', 404);
        publishAndWaitJobEnding(`/sites/${SITE}/home/about`, ['en']);
        fetchFile('/who-we-are.md', host).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/markdown;\s*charset=utf-8$/i);
            expect(response.body).to.match(/^# About us\n/);
            expect(response.headers.link).to.contain('/who-we-are>; rel="canonical"');
        });
        // The address of the page keeps its own Markdown version.
        fetchFile('/home/about.html.md', host).its('status').should('eq', 200);
        // A vanity URL is a whole address: nothing is guessed from a part of one, or from another host.
        fetchFile('/who-we.md', host).its('status').should('eq', 404);
        fetchFile('/who-we-are.md').its('status').should('eq', 404);
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
});
