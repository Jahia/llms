/**
 * What the specs share: where the module answers, and how to call it AS A GIVEN USER.
 *
 * Every call names its user. @jahia/cypress's Apollo helpers act as root unless told otherwise, and
 * root bypasses every permission check: a permission spec written with them passes for the wrong reason.
 */

export const SERVLET = '/modules/llms/llms';

export type User = {name: string; password: string} | null;

export const ROOT = (): User => ({name: 'root', password: Cypress.env('SUPER_USER_PASSWORD')});
export const GUEST: User = null;

export const origin = (): string => new URL(Cypress.config('baseUrl') as string).origin;

/** Ends the current session, so it stops counting against the instance's license. */
export const signOut = (): void => {
    cy.request({url: '/cms/logout', failOnStatusCode: false});
    cy.clearCookies();
};

/**
 * A GraphQL call as the given user (null: no session, no credentials). Never fails on status.
 *
 * Each call starts without cookies, so the credentials decide who is asking and not a session left by
 * an earlier call, and it signs out afterwards: a Jahia license counts the signed-in users, and a suite
 * that leaves one session per call gets "the maximum number of users has been reached" (503).
 * Not for use while a browser session is open in the same test: it would end that session too.
 */
export const gql = (user: User, query: string, variables: Record<string, unknown> = {}): Cypress.Chainable<Cypress.Response<any>> => {
    cy.clearCookies();
    return cy.request({
        method: 'POST',
        url: '/modules/graphql',
        headers: {Origin: origin(), 'Content-Type': 'application/json'},
        body: {query, variables},
        ...(user ? {auth: {user: user.name, pass: user.password}} : {}),
        failOnStatusCode: false
    }).then(response => {
        if (user) {
            signOut();
        }

        return cy.wrap(response, {log: false});
    });
};

/** The first GraphQL error message of an answer, or null. */
export const errorOf = (response: Cypress.Response<any>): string | null =>
    response.body?.errors?.[0]?.message ?? null;

// $path names a page of the site, for the files of that section; left out, the files are the site's.
export const SAVE = `mutation ($siteKey: String!, $path: String, $llms: String, $llmsFull: String, $baseUrl: String) {
    llms { save(siteKey: $siteKey, path: $path, llms: $llms, llmsFull: $llmsFull, baseUrl: $baseUrl) }
}`;

export const GENERATE = `query ($siteKey: String!, $path: String, $language: String!, $baseUrl: String!, $full: Boolean!) {
    llms { generate(siteKey: $siteKey, path: $path, language: $language, baseUrl: $baseUrl, full: $full) {
        content pages failedPages truncated
    } }
}`;

export const STATUS = `query ($siteKey: String!, $path: String) {
    llms { status(siteKey: $siteKey, path: $path) { llms { served current } llmsFull { served current } } }
}`;

/** Saves and publishes as root, and fails the spec if that did not work: a fixture, not a subject. */
export const saveAsRoot = (siteKey: string, files: {llms?: string | null; llmsFull?: string | null; baseUrl?: string | null; path?: string}): void => {
    gql(ROOT(), SAVE, {siteKey, path: null, llms: null, llmsFull: null, baseUrl: null, ...files}).then(response => {
        expect(errorOf(response), 'saving as root').to.eq(null);
        expect(response.body.data.llms.save).to.eq(true);
    });
};

/** A public file, fetched as a visitor without an account. */
export const fetchFile = (path: string, headers: Record<string, string> = {}): Cypress.Chainable<Cypress.Response<any>> =>
    cy.request({url: path, headers, failOnStatusCode: false});
