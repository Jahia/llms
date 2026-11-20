import './commands';
import {SERVLET} from './llms';

/**
 * Run-level guard. Every spec reads a status code from the module's servlet or from its GraphQL
 * fields. If the module is not deployed, Jahia answers 404 to all of it, and a spec that asserts an
 * absence would pass for the wrong reason. So the precondition is stated once, in its own name: the
 * servlet answers 404 for a site that does not exist, with the nosniff header only this servlet sets
 * on its answers; a servlet that is not mounted answers Jahia's own 404 page.
 */
before(() => {
    cy.request({
        method: 'POST',
        url: '/modules/graphql',
        headers: {Origin: Cypress.config('baseUrl') as string},
        body: {query: '{ __type(name: "LlmsQuery") { name } }'},
        auth: {user: 'root', pass: Cypress.env('SUPER_USER_PASSWORD')},
        failOnStatusCode: false
    }).then(response => {
        expect(response.status, 'GraphQL must answer').to.eq(200);
        expect(
            response.body?.data?.__type?.name,
            `the llms module must be deployed and ACTIVE: its GraphQL type LlmsQuery must exist (servlet ${SERVLET})`
        ).to.eq('LlmsQuery');
    });
});
