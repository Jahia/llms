import {addMixins, addNode, deleteSite, publishAndWaitJobEnding, removeMixins} from '@jahia/cypress';
import {createSiteWithPages} from '../support/fixtures';
import {errorOf, fetchFile, GENERATE, gql, ROOT} from '../support/llms';

/**
 * Which pages an editor can keep out of the files. The mark is the one editors already set for search
 * engines: the "No index" option the sitemap module adds to a page (jseomix:noIndex).
 */
describe('leaving a page out of llms.txt and llms-full.txt', () => {
    const SITE = 'llmsExclusions';
    const ABOUT = `/sites/${SITE}/home/about`;

    const generated = (full: boolean) =>
        gql(ROOT(), GENERATE, {siteKey: SITE, language: 'en', baseUrl: 'https://www.example.com', full}).then(response => {
            expect(errorOf(response)).to.eq(null);
            return response.body.data.llms.generate.content as string;
        });

    before(() => {
        createSiteWithPages(SITE);
    });

    after(() => {
        deleteSite(SITE);
    });

    it('lists a page that carries no mark', () => {
        generated(false).should('contain', '[About us]');
    });

    it('leaves a page marked No index out of both files, once the mark is published', () => {
        addMixins(ABOUT, ['jseomix:noIndex']);
        // Marked in the editing workspace only: visitors, and so the files, do not know yet.
        generated(false).should('contain', '[About us]');

        publishAndWaitJobEnding(ABOUT, ['en']);
        generated(false).should('not.contain', 'About us');
        generated(true).should('not.contain', 'About us');
    });

    it('lists the page again once the mark is removed and published', () => {
        removeMixins(ABOUT, ['jseomix:noIndex']);
        publishAndWaitJobEnding(ABOUT, ['en']);
        generated(false).should('contain', '[About us]');
    });

    /**
     * The files describe what a visitor without an account gets, nothing more. A page that is published
     * but not readable by guests is content of the site, and still none of their business.
     */
    it('leaves out a page that is published but that visitors without an account may not read', () => {
        const MEMBERS = `/sites/${SITE}/home/members`;
        addNode({
            parentPathOrId: `/sites/${SITE}/home`,
            primaryNodeType: 'jnt:page',
            name: 'members',
            properties: [
                {name: 'jcr:title', value: 'Members only', language: 'en'},
                {name: 'jcr:description', value: 'Reserved to signed-in members', language: 'en'},
                {name: 'j:templateName', value: 'simple'}
            ]
        });
        publishAndWaitJobEnding(MEMBERS, ['en']);
        // Published and public so far: it is listed. This is what makes the next assertion mean something.
        generated(false).should('contain', '[Members only]');

        // Inheritance broken and nothing granted: no visitor reads the page, in live as in default.
        cy.executeGroovy('groovy/breakAclInheritance.groovy', {NODE_PATH: MEMBERS});
        // The fixture itself: a visitor is refused the page.
        cy.clearCookies();
        fetchFile(`/sites/${SITE}/home/members.html`).its('status').should('be.oneOf', [401, 403, 404]);

        generated(false).should('not.contain', 'Members only');
        generated(true).then(content => {
            expect(content).to.not.contain('Members only');
            expect(content).to.not.contain('Reserved to signed-in members');
        });
    });
});
