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
        // The site still lists everything.
        generate(null).then(response => {
            expect(response.body.data.llms.generate.content).to.contain('[About us]').and.to.contain('[User guide]');
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
});
