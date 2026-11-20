import {deleteSite} from '@jahia/cypress';
import {addUnpublishedPage, createSiteWithPages} from '../support/fixtures';
import {errorOf, fetchFile, GENERATE, gql, ROOT} from '../support/llms';

/**
 * What generation reads and writes: only what a visitor without an account can read, with links on
 * the public URL the editor gave, and nothing stored until the editor saves.
 */
describe('generating llms.txt and llms-full.txt', () => {
    const SITE = 'llmsGeneration';
    const BASE_URL = 'https://www.example.com';

    const generate = (variables: Record<string, unknown>) =>
        gql(ROOT(), GENERATE, {siteKey: SITE, language: 'en', baseUrl: BASE_URL, full: false, ...variables});

    before(() => {
        createSiteWithPages(SITE);
        addUnpublishedPage(SITE, 'draft', 'Unpublished draft');
    });

    after(() => {
        deleteSite(SITE);
    });

    it('lists the published pages by section, with their description and public URL', () => {
        generate({}).then(response => {
            expect(errorOf(response)).to.eq(null);
            const {content, pages, truncated} = response.body.data.llms.generate;
            expect(content, 'starts with the site title').to.match(/^# \S/);
            expect(content).to.match(/- \[About us\]\(https:\/\/www\.example\.com\/[^)]+\): Who we are/);
            expect(pages).to.be.at.least(2);
            expect(truncated).to.eq(false);
        });
    });

    it('never lists a page that is not published', () => {
        generate({}).then(response => {
            expect(response.body.data.llms.generate.content).to.not.contain('Unpublished draft');
        });
        generate({full: true}).then(response => {
            expect(response.body.data.llms.generate.content).to.not.contain('Unpublished draft');
        });
    });

    it('writes every link on the public URL, never on the address Jahia runs on', () => {
        [false, true].forEach(full => {
            generate({full}).then(response => {
                expect(errorOf(response), `full: ${full}`).to.eq(null);
                const {content} = response.body.data.llms.generate;
                ['localhost', '127.0.0.1', '/cms/render', new URL(Cypress.config('baseUrl') as string).host].forEach(internal => {
                    expect(content, `no "${internal}" in the generated file (full: ${full})`).to.not.contain(internal);
                });
            });
        });
    });

    it('renders the listed pages into llms-full.txt, each under its title and source', () => {
        generate({full: true}).then(response => {
            expect(errorOf(response)).to.eq(null);
            const {content, pages, failedPages} = response.body.data.llms.generate;
            expect(failedPages, 'pages that could not be rendered').to.deep.eq([]);
            expect(pages).to.be.at.least(2);
            expect(content).to.match(/\n---\n\n# About us\n\nSource: https:\/\/www\.example\.com\/\S+\n/);
        });
    });

    it('stores nothing: the files stay absent until an editor saves', () => {
        generate({});
        generate({full: true});
        fetchFile(`/sites/${SITE}/llms.txt`).its('status').should('eq', 404);
        fetchFile(`/sites/${SITE}/llms-full.txt`).its('status').should('eq', 404);
    });

    [
        'https://www.example.com/path',
        'https://user@www.example.com',
        'https://[::1]',
        'https://www.example.com\n',
        'ftp://www.example.com',
        'javascript:alert(1)'
    ].forEach(baseUrl => {
        // No double quote in a test title: @jahia/cypress writes titles into a Groovy string on the server.
        it(`refuses the public URL ${baseUrl.replace('\n', '<newline>')}`, () => {
            generate({baseUrl}).then(response => {
                expect(errorOf(response)).to.eq('The public URL must be a scheme, a host and an optional port');
            });
        });
    });

    it('refuses a language the site does not serve', () => {
        generate({language: 'zz'}).then(response => {
            expect(errorOf(response)).to.eq('Unknown language for this site');
        });
        generate({language: '../en'}).then(response => {
            expect(errorOf(response)).to.eq('Unknown language for this site');
        });
    });
});
