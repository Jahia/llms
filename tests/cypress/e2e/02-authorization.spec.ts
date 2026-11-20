import {deleteSite} from '@jahia/cypress';
import {createSiteWithPages, createUsers, deleteUsers, OUTSIDER, PUBLISHER} from '../support/fixtures';
import {errorOf, fetchFile, GENERATE, GUEST, gql, SAVE, STATUS, User} from '../support/llms';

/**
 * Who may generate and save. Asserted as ordinary users on purpose: root holds no role and passes
 * every permission check, so a suite written as root is blind to every authorization defect.
 */
describe('authorization of the llms GraphQL fields', () => {
    const SITE_A = 'llmsAuthA';
    const SITE_B = 'llmsAuthB';
    const BASE_URL = 'https://www.example.com';

    const generate = (user: User, siteKey: string) =>
        gql(user, GENERATE, {siteKey, language: 'en', baseUrl: BASE_URL, full: false});
    const save = (user: User, siteKey: string) =>
        gql(user, SAVE, {siteKey, llms: `# Saved by ${user ? user.name : 'guest'}`, llmsFull: null, baseUrl: null});
    const status = (user: User, siteKey: string) => gql(user, STATUS, {siteKey});

    before(() => {
        createSiteWithPages(SITE_A);
        createSiteWithPages(SITE_B);
        createUsers(SITE_A);
    });

    after(() => {
        deleteUsers();
        deleteSite(SITE_A);
        deleteSite(SITE_B);
    });

    [
        {who: 'a visitor without an account', user: GUEST, site: SITE_A},
        {who: 'a signed-in user with no role on the site', user: OUTSIDER, site: SITE_A},
        {who: 'a publisher of another site', user: PUBLISHER, site: SITE_B}
    ].forEach(({who, user, site}) => {
        it(`refuses generate, save and status to ${who}`, () => {
            generate(user, site).then(response => expect(errorOf(response), 'generate').to.eq('Permission denied'));
            status(user, site).then(response => expect(errorOf(response), 'status').to.eq('Permission denied'));
            save(user, site).then(response => expect(errorOf(response), 'save').to.eq('Permission denied'));
            // And nothing was written: the refusal is not only a message.
            fetchFile(`/sites/${site}/llms.txt`).its('status').should('eq', 404);
        });
    });

    it('refuses to start a generation, or to read one, to whoever may not publish on the site', () => {
        const START = `mutation ($siteKey: String!) {
            llms { startGeneration(siteKey: $siteKey, language: "en", baseUrl: "${BASE_URL}") }
        }`;
        const PROGRESS = `query ($siteKey: String!, $id: String!) {
            llms { generationProgress(siteKey: $siteKey, id: $id) { finished } }
        }`;
        [GUEST, OUTSIDER].forEach(user => {
            gql(user, START, {siteKey: SITE_A}).then(response => expect(errorOf(response)).to.eq('Permission denied'));
        });
        gql(PUBLISHER, START, {siteKey: SITE_B}).then(response => expect(errorOf(response)).to.eq('Permission denied'));

        // A generation of site A is not readable through site B, nor by someone with no role on A.
        gql(PUBLISHER, START, {siteKey: SITE_A}).then(response => {
            expect(errorOf(response)).to.eq(null);
            const id = response.body.data.llms.startGeneration;
            gql(OUTSIDER, PROGRESS, {siteKey: SITE_A, id}).then(r => expect(errorOf(r)).to.eq('Permission denied'));
            gql(PUBLISHER, PROGRESS, {siteKey: SITE_B, id}).then(r => expect(errorOf(r)).to.eq('Permission denied'));
            gql(PUBLISHER, PROGRESS, {siteKey: SITE_A, id: 'no-such-generation'}).then(r => expect(errorOf(r)).to.eq('Unknown generation'));
            gql(PUBLISHER, PROGRESS, {siteKey: SITE_A, id}).then(r => expect(errorOf(r)).to.eq(null));
        });
    });

    it('answers the same for a site that does not exist as for one the caller may not touch', () => {
        generate(PUBLISHER, 'noSuchSite').then(response => expect(errorOf(response)).to.eq('Permission denied'));
        generate(PUBLISHER, '../systemsite').then(response => expect(errorOf(response)).to.eq('Permission denied'));
    });

    it('lets a publisher generate, save and read the status of their own site', () => {
        generate(PUBLISHER, SITE_A).then(response => {
            expect(errorOf(response), 'generate').to.eq(null);
            expect(response.body.data.llms.generate.pages).to.be.greaterThan(0);
        });
        save(PUBLISHER, SITE_A).then(response => {
            expect(errorOf(response), 'save').to.eq(null);
            expect(response.body.data.llms.save).to.eq(true);
        });
        fetchFile(`/sites/${SITE_A}/llms.txt`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.eq(`# Saved by ${PUBLISHER!.name}`);
        });
        status(PUBLISHER, SITE_A).then(response => {
            expect(response.body.data.llms.status.llms).to.deep.eq({served: true, current: true});
        });
    });
});
