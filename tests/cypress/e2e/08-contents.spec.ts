import {addMixins, addNode, deleteSite, publishAndWaitJobEnding} from '@jahia/cypress';
import {createSiteWithPages} from '../support/fixtures';
import {errorOf, fetchFile, GENERATE, gql, ROOT} from '../support/llms';

const RULES = `query ($siteKey: String!) {
    llms { rules(siteKey: $siteKey) { includedTypes maxMainResources maxDepth } }
}`;

/**
 * Two things a site may need beyond its page tree: the contents that are shown as a page of their own
 * (a news item, an article: jmix:mainResource), and a configuration of its own, among which listing only
 * the pages an editor marked.
 */
describe('contents shown as a page, and the configuration of one site', () => {
    const SITE = 'llmsContents';
    const OTHER = 'llmsContentsOther';
    const ABOUT = `/sites/${SITE}/home/about`;
    const NEWS = `/sites/${SITE}/contents/launch`;

    const generated = (full: boolean, siteKey = SITE) =>
        gql(ROOT(), GENERATE, {siteKey, language: 'en', baseUrl: 'https://www.example.com', full}).then(response => {
            expect(errorOf(response)).to.eq(null);
            return response.body.data.llms.generate.content as string;
        });

    /** Writes the configuration of the site, as an operator does with a .cfg file, and waits for it to apply. */
    const configureSite = (properties: Record<string, string>) => {
        cy.request({
            method: 'POST',
            url: '/modules/api/provisioning',
            auth: {user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD')},
            headers: {'Content-Type': 'application/json'},
            body: [{
                editConfiguration: 'org.jahia.modules.llms.site',
                configIdentifier: SITE,
                properties: {siteKey: SITE, maxMainResources: '', includedNodeTypes: '', maxDepth: '', ...properties}
            }]
        }).its('status').should('eq', 200);
        cy.request({url: '/cms/logout', failOnStatusCode: false});
        // The configuration reaches the module a moment after the call returns.
        cy.wait(2000);
    };

    const news = (name: string, title: string, body: string) => addNode({
        parentPathOrId: `/sites/${SITE}/contents`,
        primaryNodeType: 'jnt:news',
        name,
        properties: [
            {name: 'jcr:title', value: title, language: 'en'},
            {name: 'desc', value: `<p>${body}</p>`, language: 'en'}
        ]
    });

    before(() => {
        createSiteWithPages(SITE);
        createSiteWithPages(OTHER);
        news('launch', 'Product launch', 'The launch went well.');
        publishAndWaitJobEnding(`/sites/${SITE}/contents`, ['en']);
        // Never published: no visitor reads it, so no file names it.
        news('embargo', 'Embargoed news', 'Not for visitors yet.');
    });

    after(() => {
        configureSite({});
        deleteSite(SITE);
        deleteSite(OTHER);
    });

    it('lists a published content shown as a page after the pages, under the folder that holds it', () => {
        generated(false).then(content => {
            // The address is the one Jahia gives the content: here the vanity URL a news item gets.
            expect(content).to.match(/\n## Contents\n\n- \[Product launch\]\(https:\/\/www\.example\.com\/[^)\s]*launch[^)\s]*\)/);
            expect(content.indexOf('[About us]'), 'pages come first').to.be.lessThan(content.indexOf('[Product launch]'));
            expect(content).to.not.contain('Embargoed news');
        });
    });

    it('writes that content in llms-full.txt, and leaves the unpublished one out', () => {
        generated(true).then(content => {
            expect(content).to.contain('# Product launch').and.to.contain('The launch went well.');
            expect(content).to.not.contain('Not for visitors yet.');
        });
    });

    it('serves that content as Markdown and names its Markdown version in its head', () => {
        fetchFile(`${NEWS}.html.md`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.contain('The launch went well.');
        });
        fetchFile(`${NEWS}.html`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.match(/<link rel="alternate" type="text\/markdown" href="[^"]*launch\.html\.md"/);
        });
    });

    it('lets one site list no such content, without changing the other sites', () => {
        configureSite({maxMainResources: '0', maxDepth: '7'});
        generated(false).should('not.contain', 'Product launch').and('contain', '[About us]');
        gql(ROOT(), RULES, {siteKey: SITE}).then(response => {
            expect(response.body.data.llms.rules).to.deep.eq({includedTypes: [], maxMainResources: 0, maxDepth: 7});
        });
        gql(ROOT(), RULES, {siteKey: OTHER}).then(response => {
            expect(response.body.data.llms.rules).to.deep.eq({includedTypes: [], maxMainResources: 100, maxDepth: 3});
        });
    });

    it('lists only the pages and contents an editor marked, when the site is configured so', () => {
        configureSite({includedNodeTypes: 'jmix:llmsListed'});
        gql(ROOT(), RULES, {siteKey: SITE}).then(response => {
            expect(response.body.data.llms.rules.includedTypes).to.deep.eq(['jmix:llmsListed']);
        });
        // Nothing is marked yet.
        generated(false).should('not.contain', '[About us]').and('not.contain', '[Product launch]');

        addMixins(ABOUT, ['jmix:llmsListed']);
        addMixins(NEWS, ['jmix:llmsListed']);
        // Marked in the editing workspace only: the files follow what is published.
        generated(false).should('not.contain', '[About us]');
        publishAndWaitJobEnding(ABOUT, ['en']);
        publishAndWaitJobEnding(NEWS, ['en']);
        generated(false).then(content => {
            expect(content).to.contain('[About us]').and.to.contain('[Product launch]');
            // The home page is not marked.
            expect(content).to.not.match(/\]\(https:\/\/www\.example\.com\/(sites\/llmsContents\/)?home\.html\)/);
        });
        // The other site lists every page, as before.
        generated(false, OTHER).should('contain', '[About us]');
    });
});
