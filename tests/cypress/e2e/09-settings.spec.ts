import {createUser, deleteSite, deleteUser, grantRoles} from '@jahia/cypress';
import {createSiteWithPages} from '../support/fixtures';
import {errorOf, gql, User} from '../support/llms';

const FIELDS = 'key kind value inherited min max error detail';
const SETTINGS = `query ($siteKey: String!) { llms { settings(siteKey: $siteKey) { ${FIELDS} } } }`;
const SAVE_SETTINGS = `mutation ($siteKey: String!, $keys: [String!]!, $values: [String!]!) {
    llms { saveSettings(siteKey: $siteKey, keys: $keys, values: $values) { ${FIELDS} } }
}`;
const RULES = `query ($siteKey: String!) { llms { rules(siteKey: $siteKey) { maxDepth maxPages maxMainResources includedTypes } } }`;

/** Administers the site: who the settings are for. Never root, which holds every permission. */
const SITE_ADMIN: User = {name: 'llms-site-admin', password: 'Llms-site-admin-1'};
/** May publish on the site, and so generate and save the files, but not change what the site lists. */
const CHIEF: User = {name: 'llms-chief', password: 'Llms-chief-1'};

type Field = {key: string; value: string; inherited: string; error: string | null; detail: string | null};

/**
 * What a site lists, set in the panel by who administers the site. The values are written in the
 * configuration of that site alone, and a value the module would have to correct or ignore is refused.
 */
describe('the settings of a site', () => {
    const SITE = 'llmsSettings';
    const OTHER = 'llmsSettingsOther';

    const save = (user: User, values: Record<string, string>, siteKey = SITE) =>
        gql(user, SAVE_SETTINGS, {siteKey, keys: Object.keys(values), values: Object.values(values)});

    const field = (fields: Field[], key: string) => fields.find(f => f.key === key) as Field;

    const rulesOf = (siteKey: string) => gql(SITE_ADMIN, RULES, {siteKey}).then(response => {
        expect(errorOf(response)).to.eq(null);
        return response.body.data.llms.rules;
    });

    /** The configuration reaches the module a moment after it is written. */
    const applied = () => cy.wait(3000);

    before(() => {
        createSiteWithPages(SITE);
        createSiteWithPages(OTHER);
        [SITE_ADMIN, CHIEF].forEach(user => {
            deleteUser(user!.name);
            createUser(user!.name, user!.password);
        });
        [SITE, OTHER].forEach(site => {
            grantRoles(`/sites/${site}`, ['site-administrator'], SITE_ADMIN!.name, 'USER');
            grantRoles(`/sites/${site}`, ['editor-in-chief'], CHIEF!.name, 'USER');
        });
        // A run that was stopped may have left the configuration of the site behind.
        save(SITE_ADMIN, {});
        applied();
    });

    after(() => {
        save(SITE_ADMIN, {});
        [SITE_ADMIN, CHIEF].forEach(user => deleteUser(user!.name));
        deleteSite(SITE);
        deleteSite(OTHER);
    });

    afterEach(() => {
        cy.visit('/cms/logout', {failOnStatusCode: false});
    });

    it('tells who administers the site what it may set, and what an empty value inherits', () => {
        gql(SITE_ADMIN, SETTINGS, {siteKey: SITE}).then(response => {
            expect(errorOf(response)).to.eq(null);
            const fields = response.body.data.llms.settings as Field[];
            expect(fields.map(f => f.key)).to.deep.eq(['maxPages', 'maxDepth', 'maxMainResources', 'includedNodeTypes',
                'excludedNodeTypes', 'excludedTreeNodeTypes', 'linkToMarkdown', 'keepImages']);
            expect(field(fields, 'maxDepth')).to.include({value: '', inherited: '10', min: 1, max: 10});
            expect(field(fields, 'maxPages')).to.include({value: '', inherited: '200'});
            expect(field(fields, 'linkToMarkdown')).to.include({value: '', inherited: 'false'});
        });
    });

    it('answers nothing to who may publish but does not administer the site, and to a visitor', () => {
        gql(CHIEF, SETTINGS, {siteKey: SITE}).then(response => expect(errorOf(response)).to.not.eq(null));
        save(CHIEF, {maxDepth: '2'}).then(response => expect(errorOf(response)).to.not.eq(null));
        gql(null, SETTINGS, {siteKey: SITE}).then(response => expect(errorOf(response)).to.not.eq(null));
        save(null, {maxDepth: '2'}).then(response => expect(errorOf(response)).to.not.eq(null));
    });

    it('refuses a value it would have to correct, and then saves nothing at all', () => {
        save(SITE_ADMIN, {maxDepth: '99', maxPages: '50', excludedNodeTypes: 'jseomix:noIndex, acme:unknown'}).then(response => {
            expect(errorOf(response)).to.eq(null);
            const fields = response.body.data.llms.saveSettings as Field[];
            expect(field(fields, 'maxDepth').error).to.eq('range');
            expect(field(fields, 'excludedNodeTypes')).to.include({error: 'types', detail: 'acme:unknown'});
            expect(field(fields, 'maxPages').error).to.eq(null);
        });
        applied();
        // The valid value sent with the refused ones was not written either.
        rulesOf(SITE).should('deep.include', {maxDepth: 10, maxPages: 200});
    });

    it('refuses a value of the server, which no site may set', () => {
        save(SITE_ADMIN, {renderBaseUrl: 'http://internal.example'}).then(response => expect(errorOf(response)).to.eq('Unknown setting'));
        save(SITE_ADMIN, {fetchThreads: '8'}).then(response => expect(errorOf(response)).to.eq('Unknown setting'));
    });

    it('writes the values of one site, leaves the other sites alone, and removes them when emptied', () => {
        save(SITE_ADMIN, {maxDepth: '4', maxMainResources: '0', includedNodeTypes: 'jmix:llmsListed'}).then(response => {
            expect(errorOf(response)).to.eq(null);
            const fields = response.body.data.llms.saveSettings as Field[];
            expect(fields.filter(f => f.error)).to.have.length(0);
            expect(field(fields, 'maxDepth').value).to.eq('4');
        });
        applied();
        rulesOf(SITE).should('deep.eq', {maxDepth: 4, maxPages: 200, maxMainResources: 0, includedTypes: ['jmix:llmsListed']});
        rulesOf(OTHER).should('deep.eq', {maxDepth: 10, maxPages: 200, maxMainResources: 100, includedTypes: []});
        gql(SITE_ADMIN, SETTINGS, {siteKey: SITE}).then(response => {
            expect(field(response.body.data.llms.settings, 'maxDepth').value).to.eq('4');
        });

        // Saved again with one value: the others go back to what this Jahia says.
        save(SITE_ADMIN, {maxDepth: '6'}).then(response => expect(errorOf(response)).to.eq(null));
        applied();
        rulesOf(SITE).should('deep.eq', {maxDepth: 6, maxPages: 200, maxMainResources: 100, includedTypes: []});

        save(SITE_ADMIN, {}).then(response => expect(errorOf(response)).to.eq(null));
        applied();
        rulesOf(SITE).should('deep.eq', {maxDepth: 10, maxPages: 200, maxMainResources: 100, includedTypes: []});
    });

    it('never writes the configuration of another site, even one filed under the name of this site', () => {
        // An operator named a file after this site, and wrote in it the key of the other one.
        cy.request({
            method: 'POST',
            url: '/modules/api/provisioning',
            auth: {user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD')},
            headers: {'Content-Type': 'application/json'},
            body: [{editConfiguration: 'org.jahia.modules.llms.site', configIdentifier: SITE, properties: {siteKey: OTHER, maxDepth: '2'}}]
        }).its('status').should('eq', 200);
        cy.request({url: '/cms/logout', failOnStatusCode: false});
        applied();
        rulesOf(OTHER).its('maxDepth').should('eq', 2);

        save(SITE_ADMIN, {maxDepth: '4'}).then(response => expect(errorOf(response)).to.eq(null));
        applied();
        rulesOf(SITE).its('maxDepth').should('eq', 4);
        // The other site keeps its own value: its file was neither rewritten nor taken over.
        rulesOf(OTHER).its('maxDepth').should('eq', 2);

        save(SITE_ADMIN, {}).then(response => expect(errorOf(response)).to.eq(null));
        applied();
        rulesOf(SITE).its('maxDepth').should('eq', 10);
        rulesOf(OTHER).its('maxDepth').should('eq', 2);

        // The administrator of the other site removes its values: that file is theirs.
        save(SITE_ADMIN, {}, OTHER).then(response => expect(errorOf(response)).to.eq(null));
        applied();
        rulesOf(OTHER).its('maxDepth').should('eq', 10);
    });

    it('offers the Settings tab to who administers the site, with each value explained and checked', () => {
        cy.login(SITE_ADMIN!.name, SITE_ADMIN!.password);
        cy.visit(`/jahia/jcontent/${SITE}/en/apps/siteSettingsSeo/llms`);
        cy.get('[data-llms-role="panel"]', {timeout: 60000}).should('be.visible');
        cy.get('[data-llms-tab="settings"]').click();
        cy.get('#llms-tabpanel').should('have.attr', 'aria-labelledby', 'llms-tab-settings');
        // Save and publish is for the files: it is not offered beside the settings.
        cy.get('[data-llms-role="save"]').should('not.exist');
        cy.get('[data-llms-role="settings-server"]').should('contain.text', 'org.jahia.modules.llms');

        // Each value is a labelled control whose help says the range and what an empty one inherits.
        cy.get('label[for="llms-set-maxDepth"]').should('contain.text', 'Page depth');
        cy.get('#llms-set-maxDepth').should('have.attr', 'aria-describedby', 'llms-set-maxDepth-help').and('have.value', '')
            // The default shows in the empty field, without being a value of the site.
            .and('have.attr', 'placeholder', '10');
        cy.get('#llms-set-maxPages').should('have.attr', 'placeholder', '200');
        cy.get('#llms-set-excludedNodeTypes').invoke('attr', 'placeholder').should('contain', 'jseomix:noIndex');
        cy.get('#llms-set-includedNodeTypes').should('have.attr', 'placeholder', 'none');
        cy.get('#llms-set-maxDepth-help').should('contain.text', 'from 1 to 10').and('contain.text', 'Empty: the default of this Jahia (10).');
        cy.get('fieldset[data-llms-setting="linkToMarkdown"]').within(() => {
            cy.get('legend').should('contain.text', 'Links of llms.txt');
            cy.get('input[type="radio"]').should('have.length', 3);
            cy.get('#llms-set-linkToMarkdown-default').should('be.checked');
            cy.get('label[for="llms-set-linkToMarkdown-default"]').should('contain.text', 'Default of this Jahia (No)');
        });

        // A wrong value: said at the top and at the field, the focus goes to the field, nothing is saved.
        cy.get('#llms-set-maxDepth').type('99');
        cy.get('#llms-set-excludedNodeTypes').type('acme:unknown');
        cy.get('[data-llms-role="save-settings"]').click();
        cy.get('[data-llms-role="settings-errors"]').should('have.attr', 'role', 'alert').and('contain.text', '1 values to correct');
        cy.get('#llms-set-maxDepth').should('have.focus').and('have.attr', 'aria-invalid', 'true')
            .and('have.attr', 'aria-describedby', 'llms-set-maxDepth-help llms-set-maxDepth-error');
        cy.get('#llms-set-maxDepth-error').should('have.text', 'Error: enter a number from 1 to 10.');

        // Corrected: the browser has nothing more to say, the server names the type it does not know.
        cy.get('#llms-set-maxDepth').clear().type('5');
        cy.get('#llms-set-maxDepth-error').should('not.exist');
        cy.get('[data-llms-role="save-settings"]').click();
        cy.get('#llms-set-excludedNodeTypes-error').should('have.text', 'Error: not a node type of this Jahia: acme:unknown.');
        cy.get('#llms-set-excludedNodeTypes').should('have.focus');

        cy.get('#llms-set-excludedNodeTypes').clear();
        cy.get('#llms-set-linkToMarkdown-yes').check();
        cy.get('[data-llms-role="save-settings"]').click();
        cy.get('[role="status"]').should('contain.text', 'The settings of the site were saved.');
        cy.get('[data-llms-role="settings-errors"]').should('not.exist');
        // Still focused: the button is never disabled.
        cy.get('[data-llms-role="save-settings"]').should('have.focus');

        // Left and come back to, without reloading: the tab shows what was saved, not what it first read.
        cy.get('[data-llms-tab="llms"]').click();
        cy.get('[data-llms-tab="settings"]').click();
        cy.get('#llms-set-maxDepth').should('have.value', '5');
        cy.get('#llms-set-linkToMarkdown-yes').should('be.checked');
        cy.get('[data-llms-role="settings-unsaved"]').should('not.exist');

        applied();
        cy.reload();
        cy.get('[data-llms-tab="settings"]', {timeout: 60000}).click();
        cy.get('#llms-set-maxDepth').should('have.value', '5');
        cy.get('#llms-set-linkToMarkdown-yes').should('be.checked');
        // The rules of the file tabs say what is now in force for this site.
        cy.get('[data-llms-tab="llms"]').click();
        cy.get('[data-llms-rule="limits"]').should('contain.text', 'down to 5 levels');

        // Emptied and saved: the site lists as this Jahia says again.
        cy.get('[data-llms-tab="settings"]').click();
        cy.get('[data-llms-role="reset-settings"]').click();
        cy.get('[role="status"]').should('contain.text', 'Fields emptied. Not saved yet.');
        cy.get('#llms-set-maxDepth').should('have.value', '');
        cy.get('[data-llms-role="save-settings"]').click();
        cy.get('[role="status"]').should('contain.text', 'The settings of the site were saved.');
    });

    it('does not offer the Settings tab to who may only publish', () => {
        cy.login(CHIEF!.name, CHIEF!.password);
        cy.visit(`/jahia/jcontent/${SITE}/en/apps/siteSettingsSeo/llms`);
        cy.get('[data-llms-role="panel"]', {timeout: 60000}).should('be.visible');
        cy.get('[data-llms-tab="llmsFull"]').should('exist');
        cy.get('[data-llms-tab="settings"]').should('not.exist');
    });
});
