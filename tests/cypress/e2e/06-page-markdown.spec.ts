import {deleteSite, publishAndWaitJobEnding, uploadFile} from '@jahia/cypress';
import {addUnpublishedPage, createSiteWithPages} from '../support/fixtures';
import {fetchFile} from '../support/llms';

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
            expect(response.headers.link).to.eq(`<${Cypress.config('baseUrl')}${ABOUT}>; rel="canonical"`);
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
