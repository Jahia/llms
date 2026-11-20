import {addNode, deleteSite, publishAndWaitJobEnding} from '@jahia/cypress';
import {createSiteWithPages} from '../support/fixtures';
import {errorOf, fetchFile, GENERATE, gql, ROOT, saveAsRoot, STATUS} from '../support/llms';

/**
 * The files of a section: an llms.txt placed under the path of a page covers the pages below that page,
 * as the llms.txt proposal defines a file in a sub-path. The site keeps its own files.
 */
describe('llms.txt of a section of the site', () => {
    const SITE = 'llmsSections';
    const DOCS = `/sites/${SITE}/home/docs`;

    const page = (parent: string, name: string, title: string) => addNode({
        parentPathOrId: parent,
        primaryNodeType: 'jnt:page',
        name,
        properties: [
            {name: 'jcr:title', value: title, language: 'en'},
            {name: 'j:templateName', value: 'simple'}
        ]
    });

    const generate = (path: string | null) =>
        gql(ROOT(), GENERATE, {siteKey: SITE, path, language: 'en', baseUrl: 'https://www.example.com', full: false});

    before(() => {
        createSiteWithPages(SITE);
        page(`/sites/${SITE}/home`, 'docs', 'Documentation');
        page(DOCS, 'guide', 'User guide');
        page(DOCS, 'api', 'API reference');
        // A menu group is a label of the navigation, not a page: the pages it gathers are sections too.
        addNode({
            parentPathOrId: `/sites/${SITE}/home`,
            primaryNodeType: 'jnt:navMenuText',
            name: 'products',
            properties: [{name: 'jcr:title', value: 'Products', language: 'en'}]
        });
        page(`/sites/${SITE}/home/products`, 'tools', 'Tools');
        page(`/sites/${SITE}/home/products/tools`, 'hammer', 'Hammer');
        publishAndWaitJobEnding(`/sites/${SITE}/home`, ['en']);
        saveAsRoot(SITE, {llms: '# The whole site'});
    });

    after(() => {
        deleteSite(SITE);
    });

    it('generates from the page of the section: its pages, under its title, and no other', () => {
        generate(DOCS).then(response => {
            expect(errorOf(response)).to.eq(null);
            const {content, pages} = response.body.data.llms.generate;
            expect(content).to.match(/^# Documentation\n/);
            expect(content).to.contain('[User guide]').and.to.contain('[API reference]');
            expect(content, 'a page outside the section').to.not.contain('About us');
            expect(pages).to.eq(3);
        });
        // The site still lists everything. No section has a file yet: it links to none.
        generate(null).then(response => {
            expect(response.body.data.llms.generate.content).to.contain('[About us]').and.to.contain('[User guide]');
            expect(response.body.data.llms.generate.content).to.not.contain('## llms.txt');
        });
    });

    it('serves the file of a section under the path of its page, and leaves the file of the site alone', () => {
        fetchFile(`${DOCS}/llms.txt`).its('status').should('eq', 404);
        saveAsRoot(SITE, {path: DOCS, llms: '# Documentation\n\n- [User guide](https://www.example.com/guide.html)'});

        fetchFile(`${DOCS}/llms.txt`).then(response => {
            expect(response.status).to.eq(200);
            expect(response.headers['content-type']).to.match(/^text\/plain/i);
            expect(response.body).to.match(/^# Documentation\n/);
        });
        fetchFile(`/sites/${SITE}/llms.txt`).its('body').should('eq', '# The whole site');
        fetchFile(`${DOCS}/llms-full.txt`).its('status').should('eq', 404);
        // A page that carries no file has none, whatever its parents carry.
        fetchFile(`${DOCS}/guide/llms.txt`).its('status').should('eq', 404);
    });

    it('serves it at the short URL of the site\'s own host too', () => {
        fetchFile('/home/docs/llms.txt', {Host: `${SITE}.test`.toLowerCase()}).then(response => {
            expect(response.status).to.eq(200);
            expect(response.body).to.match(/^# Documentation\n/);
        });
    });

    it('reports the status of the files of the section, apart from the site\'s', () => {
        gql(ROOT(), STATUS, {siteKey: SITE, path: DOCS}).then(response => {
            const status = response.body.data.llms.status;
            expect(status.llms).to.deep.eq({served: true, current: true});
            expect(status.llmsFull).to.deep.eq({served: false, current: false});
        });
    });

    it('lists the pages that can be a section, those that carry files marked so', () => {
        gql(ROOT(), 'query ($siteKey: String!) { llms { sections(siteKey: $siteKey) { path title hasFiles } } }', {siteKey: SITE}).then(response => {
            expect(errorOf(response)).to.eq(null);
            expect(response.body.data.llms.sections).to.deep.include({path: DOCS, title: 'Documentation', hasFiles: true});
            // A page below a menu group is offered, named after its group; the group itself is not.
            expect(response.body.data.llms.sections).to.deep.include(
                {path: `/sites/${SITE}/home/products/tools`, title: 'Products / Tools', hasFiles: false});
            expect(response.body.data.llms.sections.map((section: {path: string}) => section.path))
                .to.not.include(`/sites/${SITE}/home/products`);
        });
    });

    it('ends the llms.txt of the site with a link to the llms.txt of each section that has one', () => {
        generate(null).then(response => {
            expect(errorOf(response)).to.eq(null);
            const {content} = response.body.data.llms.generate;
            // After the pages, under the name of the file, at the address that answers on any host.
            expect(content).to.match(new RegExp(
                `\\n## llms\\.txt\\n\\n- \\[Documentation\\]\\(https://www\\.example\\.com/sites/${SITE}/home/docs/llms\\.txt\\)\\n$`));
            expect(content.indexOf('[About us]')).to.be.lessThan(content.indexOf('## llms.txt'));
        });
        fetchFile(`${DOCS}/llms.txt`).its('status').should('eq', 200);
        // The file of a section links to the sections below it, never to itself.
        generate(DOCS).then(response => {
            expect(response.body.data.llms.generate.content).to.not.contain('## llms.txt');
        });
    });

    it('refuses a path that is not a page of this site', () => {
        [`/sites/systemsite/home`, `/sites/${SITE}/home/../../systemsite`, `/sites/${SITE}/files`, `/sites/${SITE}/home/no-such-page`, '/'].forEach(path => {
            generate(path).then(response => expect(errorOf(response), path).to.eq('Unknown section'));
            gql(ROOT(), STATUS, {siteKey: SITE, path}).then(response => expect(errorOf(response), path).to.eq('Unknown section'));
        });
    });

    /**
     * "The most specific file applies": a page inside the section names the file of the section, a page
     * outside names the file of the site.
     */
    it('names in a page the nearest llms.txt going up', () => {
        const describedBy = (url: string) => fetchFile(url).then(response => {
            expect(response.status).to.eq(200);
            const link = new DOMParser().parseFromString(response.body, 'text/html').head.querySelector('link[rel="describedby"]');
            return link ? link.getAttribute('href') : null;
        });
        describedBy(`${DOCS}/guide.html`).should('eq', `${DOCS}/llms.txt`);
        describedBy(`/sites/${SITE}/home/about.html`).should('eq', `/sites/${SITE}/llms.txt`);
    });

    /**
     * The same in the panel: the editor chooses what the two files are about. In a browser, since what an
     * editor sees cannot be proven by a GraphQL call.
     */
    describe('in the panel', () => {
        const PANEL = `/jahia/jcontent/${SITE}/en/apps/siteSettingsSeo/llms`;
        const choose = (label: string) => {
            cy.get('[data-llms-role="scope"] [role="listbox"]').click();
            cy.contains('[role="option"], li', label).click();
        };

        beforeEach(() => {
            cy.login();
            cy.visit(PANEL);
            cy.get('[data-llms-role="panel"]', {timeout: 60000}).should('be.visible');
        });

        afterEach(() => {
            cy.visit('/cms/logout', {failOnStatusCode: false});
        });

        it('shows the files of the whole site first, and offers the sections', () => {
            cy.get('[data-llms-role="scope"] [role="group"]').should('have.attr', 'aria-label', 'Files of');
            cy.get('#llms-editor-llms').should('have.value', '# The whole site');
            cy.get('[data-llms-role="view"]').should('have.attr', 'href', `/sites/${SITE}/llms.txt`);
        });

        it('shows the files of a section once it is chosen, where they are served, and keeps the panel in place', () => {
            choose('Documentation');
            cy.get('h1').should('contain.text', 'Documentation');
            cy.get('#llms-editor-llms').invoke('val').should('match', /^# Documentation\n/);
            cy.get('[data-llms-role="view"]').should('have.attr', 'href', `${DOCS}/llms.txt`);
            // The tabs still control the panel, and the public URL, which is the site's, cannot be changed here.
            cy.get('#llms-tabpanel').should('have.attr', 'aria-labelledby', 'llms-tab-llms').and('have.attr', 'aria-busy', 'false');
            cy.get('#llms-baseurl').should('have.attr', 'readonly');
            cy.get('[data-llms-role="generate"]').should('contain.text', 'Generate llms.txt again');
        });

        it('refuses to change scope with unsaved changes, and says so next to the control', () => {
            cy.get('#llms-editor-llms').type('{moveToEnd}\n\nNot saved yet.', {delay: 0});
            choose('Documentation');
            cy.get('[data-llms-role="scope-refusal"]').should('have.attr', 'role', 'alert')
                .and('have.text', 'Save or discard your changes before choosing another scope.');
            // Nothing changed, nothing was lost.
            cy.get('#llms-editor-llms').invoke('val').should('contain', 'Not saved yet.');
            cy.get('[data-llms-role="view"]').should('have.attr', 'href', `/sites/${SITE}/llms.txt`);
        });

        it('saves the file of the section, and only that one', () => {
            choose('Documentation');
            cy.get('#llms-editor-llms').invoke('val').should('match', /^# Documentation\n/);
            cy.get('#llms-editor-llms').type('{moveToEnd}\n\nEdited in the panel.', {delay: 0});
            cy.get('[data-llms-role="save"]').should('be.enabled').click();
            cy.get('[data-llms-status="published"]', {timeout: 30000}).should('exist');
            fetchFile(`${DOCS}/llms.txt`).its('body').should('contain', 'Edited in the panel.');
            fetchFile(`/sites/${SITE}/llms.txt`).its('body').should('eq', '# The whole site');
        });
    });
});
