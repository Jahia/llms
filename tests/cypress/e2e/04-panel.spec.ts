import {deleteSite} from '@jahia/cypress';
import {createSiteWithPages, createUsers, deleteUsers, PUBLISHER} from '../support/fixtures';
import {fetchFile} from '../support/llms';

/**
 * The panel, in a browser. What an editor sees and can do cannot be proven by a GraphQL call: these
 * specs load jContent. They sign in as an ordinary publisher, not as root, for the same reason.
 */
describe('the llms.txt panel in jContent', () => {
    const SITE = 'llmsPanel';
    // Not .../siteSettingsSeo/llms: that URL redirects to the home page.
    const PANEL = `/jahia/jcontent/${SITE}/en/apps/siteSettingsSeo/llms`;

    const openPanel = () => {
        cy.login(PUBLISHER!.name, PUBLISHER!.password);
        cy.visit(PANEL);
        cy.get('[data-llms-role="panel"]', {timeout: 60000}).should('be.visible');
    };

    before(() => {
        createSiteWithPages(SITE);
        createUsers(SITE);
    });

    // Each test signs in again: the session of the one before is ended, not left to time out.
    afterEach(() => {
        cy.visit('/cms/logout', {failOnStatusCode: false});
    });

    after(() => {
        deleteUsers();
        deleteSite(SITE);
    });

    it('opens for a publisher, with one tab per file and nothing created yet', () => {
        openPanel();
        cy.get('[role="tablist"]').should('have.attr', 'aria-label', 'Files');
        cy.get('[data-llms-tab="llms"]').should('have.attr', 'aria-selected', 'true').and('have.attr', 'aria-controls', 'llms-tabpanel');
        cy.get('[data-llms-tab="llmsFull"]').should('have.attr', 'aria-selected', 'false');
        cy.get('#llms-tabpanel').should('have.attr', 'role', 'tabpanel').and('have.attr', 'aria-labelledby', 'llms-tab-llms');
        cy.get('[data-llms-status]').should('have.attr', 'data-llms-status', 'none');
        cy.get('[data-llms-role="save"]').should('be.disabled');
    });

    it('names its fields for assistive technology', () => {
        openPanel();
        cy.get('label[for="llms-baseurl"]').should('have.text', 'Public site URL');
        cy.get('#llms-baseurl').should('have.attr', 'aria-invalid', 'false').and('have.attr', 'aria-describedby', 'llms-baseurl-help');
        cy.get('#llms-editor-llms').then($editor => {
            const label = Cypress.$(`label[for="${$editor.attr('id')}"]`);
            expect(label.text()).to.eq('Content of llms.txt');
        });
        cy.get('[role="group"][aria-label="Language"]').should('exist');
    });

    it('ties an invalid public URL to its field and blocks saving', () => {
        openPanel();
        cy.get('#llms-baseurl').clear().type('https://www.example.com/path');
        cy.get('#llms-baseurl').should('have.attr', 'aria-invalid', 'true')
            .and('have.attr', 'aria-describedby', 'llms-baseurl-help llms-baseurl-error');
        cy.get('#llms-baseurl-error').should('contain.text', 'with no path');
        cy.get('[data-llms-role="save"]').should('be.disabled');
        cy.get('[data-llms-role="generate"]').should('be.disabled');
    });

    it('generates a proposal, lets the editor change it, then saves and publishes it', () => {
        openPanel();
        cy.get('#llms-baseurl').clear().type('https://www.example.com');
        cy.get('[data-llms-role="generate"]').should('contain.text', 'Generate llms.txt').click();
        cy.get('[data-llms-role="generated"]', {timeout: 60000}).should('be.visible');
        // Generate is never disabled by its own run: the focus stays on it, the proposal shows below.
        cy.get('[data-llms-role="generate"]').should('be.enabled').and('have.focus');
        cy.get('#llms-editor-llms').invoke('val').should('contain', 'https://www.example.com/');
        // A proposal is not a publication.
        cy.get('[data-llms-status]').should('have.attr', 'data-llms-status', 'unsaved');
        cy.get('[data-llms-tab="llms"]').should('have.attr', 'aria-label', 'llms.txt, unsaved changes');
        fetchFile(`/sites/${SITE}/llms.txt`).its('status').should('eq', 404);

        cy.get('#llms-editor-llms').type('{moveToEnd}\n\nEdited by hand.', {delay: 0});
        cy.get('[data-llms-role="save"]').should('be.enabled').click();
        cy.get('[data-llms-status="published"]', {timeout: 30000}).should('exist');
        cy.get('[data-llms-role="save"]').should('be.disabled');
        // The address is shown once the file is served, as a link that says where it leads.
        cy.get('[data-llms-role="view"]').should('have.attr', 'href', `/sites/${SITE}/llms.txt`)
            .and('have.attr', 'target', '_blank').and('contain.text', `/sites/${SITE}/llms.txt`);
        fetchFile(`/sites/${SITE}/llms.txt`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.contain('Edited by hand.');
        });
    });

    it('shows the pages go by while llms-full.txt is generated, and keeps the log', () => {
        openPanel();
        cy.get('[data-llms-tab="llmsFull"]').click();
        cy.get('#llms-baseurl').clear().type('https://www.example.com');
        cy.get('[data-llms-role="generate"]').should('contain.text', 'Generate llms-full.txt').click();
        cy.get('[data-llms-role="generated"]', {timeout: 120000}).should('be.visible');

        // The bar is a real progress element, named and described: nothing to guess for a screen reader.
        cy.get('[data-llms-role="progress"] progress').should('have.attr', 'aria-labelledby', 'llms-progress-label')
            .and('have.attr', 'aria-describedby', 'llms-progress-count');
        cy.get('[data-llms-role="progress-count"]').invoke('text').should('match', /^(\d+) \/ \1 pages$/);
        // One line per page, with what became of it written out.
        cy.get('[data-llms-role="log"]').should('have.attr', 'role', 'region').and('have.attr', 'tabindex', '0');
        cy.get('[data-llms-role="log"] li').should('have.length.at.least', 2)
            .each($line => expect($line.text()).to.match(/(Rendered|Empty, left out|Failed, left out)$/));
        cy.get('[data-llms-role="log"] li[data-llms-state="RENDERED"]').should('contain.text', 'Rendered');
        // The focus has not moved, and the proposal is in the editor.
        cy.get('[data-llms-role="generate"]').should('have.focus');
        cy.get('#llms-editor-llmsFull').invoke('val').should('contain', 'Source: https://www.example.com/');

        // Discarding the proposal takes its log away with it.
        cy.get('[data-llms-role="revert"]').click();
        cy.get('[data-llms-role="progress"]').should('not.exist');
    });

    it('keeps the two files apart: llms-full.txt is still not created', () => {
        openPanel();
        cy.get('[data-llms-status="published"]', {timeout: 30000}).should('exist');
        cy.get('[data-llms-tab="llmsFull"]').click();
        cy.get('#llms-tabpanel').should('have.attr', 'aria-labelledby', 'llms-tab-llmsFull');
        cy.get('[data-llms-status]').should('have.attr', 'data-llms-status', 'none');
        // Not served: said in words, with no link to a file that is not there.
        cy.get('[data-llms-role="view"]').should('not.exist');
        cy.get('[data-llms-role="served"]').should('contain.text', 'Not served yet').and('contain.text', `/sites/${SITE}/llms-full.txt`);
    });
});
