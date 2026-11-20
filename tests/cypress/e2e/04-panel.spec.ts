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

    it('says which pages are left out, from the rules in force on this Jahia', () => {
        openPanel();
        cy.get('[data-llms-role="rules"]').within(() => {
            cy.contains('Left out of both files');
            cy.contains('li', 'Pages hidden from the navigation menu');
            cy.contains('li', 'Pages not translated in the chosen language');
            // The default configuration: the No index option of the sitemap module.
            cy.get('[data-llms-rule="noIndex"]').should('have.text', 'Pages marked No index in their SEO options');
            // The marks of template sets this Jahia does not have are not shown: no page here can carry them.
            cy.get('[data-llms-rule="types"]').should('not.exist');
            cy.get('[data-llms-rule="limits"]').invoke('text').should('match', /^At most \d+ pages are listed, down to \d+ levels below the home page\. Content displayed as a page of its own \(news, articles\) is listed too, the \d+ most recent at most\. That is \d+ entries at most in one file\.$/);
        });
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
        // At once, one block says what runs and holds the bar and the pages: no second, empty banner beside it.
        cy.get('[data-llms-role="progress"][data-llms-running="true"]').should('be.visible')
            .and('have.attr', 'role', 'group').and('contain.text', 'Generating llms-full.txt')
            .then($block => {
                ($block[0] as unknown as {llmsSeen: boolean}).llmsSeen = true;
            });
        cy.get('[data-llms-role="generating"]').should('not.exist');
        cy.get('[data-llms-role="generated"]', {timeout: 120000}).should('be.visible');
        // The same element stays once it is over, under another name: a reader in the log keeps their place.
        cy.get('[data-llms-role="progress"]').should('have.attr', 'data-llms-running', 'false')
            .and('contain.text', 'Pages of the last generation').and('not.contain.text', 'Generating')
            .then($block => expect(($block[0] as unknown as {llmsSeen: boolean}).llmsSeen).to.eq(true));

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

        // The file is the pages put end to end: the editor is told to work on it before publishing.
        cy.get('[data-llms-role="advice"]').should('contain.text', 'strongly advised before publishing');

        // The same text as a person reads it, in place of the editor, and back.
        cy.get('[data-llms-role="preview-toggle"]').should('have.attr', 'aria-pressed', 'false').click();
        cy.get('[data-llms-role="preview-toggle"]').should('have.attr', 'aria-pressed', 'true').and('have.focus');
        cy.get('#llms-editor-llmsFull').should('not.exist');
        cy.get('#llms-preview-llmsFull').should('have.attr', 'role', 'region').and('have.attr', 'tabindex', '0')
            .and('have.attr', 'aria-label', 'Preview of llms-full.txt');
        // Under the h2 of the card: the titles of the file start at h3, and no h1 or h2 comes from it.
        cy.get('#llms-preview-llmsFull h3').should('have.length.at.least', 1);
        cy.get('#llms-preview-llmsFull').find('h1, h2').should('not.exist');
        cy.get('#llms-preview-llmsFull a[href^="https://www.example.com/"]').first()
            .should('have.attr', 'target', '_blank').and('have.attr', 'rel', 'noopener noreferrer').and('contain.text', '(opens in a new tab)');
        cy.get('[role="status"]').should('contain.text', 'Preview shown.');
        cy.get('[data-llms-role="preview-toggle"]').click();
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
