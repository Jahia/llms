import {addNode, createSite, createUser, deleteSite, deleteUser, enableModule, grantRoles, publishAndWaitJobEnding} from '@jahia/cypress';
import {User} from './llms';

/**
 * The sites and users the suite works on. Created by the specs that need them and removed after,
 * so a spec never depends on what another one left behind.
 */

export const TEMPLATE_SET = 'dx-base-demo-templates';

/** May publish on SITE_A only: the ordinary user every permission assertion is made with. */
export const PUBLISHER: User = {name: 'llms-publisher', password: 'Llms-publisher-1'};
/** Signed in, with no role on any test site. */
export const OUTSIDER: User = {name: 'llms-outsider', password: 'Llms-outsider-1'};

/** A site with the module enabled, a published home page and one published sub-page. */
export const createSiteWithPages = (siteKey: string, languages = 'en'): void => {
    deleteSite(siteKey);
    createSite(siteKey, {templateSet: TEMPLATE_SET, serverName: `${siteKey}.test`, locale: 'en', languages});
    enableModule('llms', siteKey);
    addNode({
        parentPathOrId: `/sites/${siteKey}/home`,
        primaryNodeType: 'jnt:page',
        name: 'about',
        properties: [
            {name: 'jcr:title', value: 'About us', language: 'en'},
            {name: 'jcr:description', value: 'Who we are', language: 'en'},
            {name: 'j:templateName', value: 'simple'}
        ]
    });
    publishAndWaitJobEnding(`/sites/${siteKey}`, ['en']);
};

/** A page that exists in the editing workspace only: no visitor may ever read about it. */
export const addUnpublishedPage = (siteKey: string, name: string, title: string): void => {
    addNode({
        parentPathOrId: `/sites/${siteKey}/home`,
        primaryNodeType: 'jnt:page',
        name,
        properties: [
            {name: 'jcr:title', value: title, language: 'en'},
            {name: 'j:templateName', value: 'simple'}
        ]
    });
};

export const createUsers = (publisherSite: string): void => {
    [PUBLISHER, OUTSIDER].forEach(user => {
        deleteUser(user!.name);
        createUser(user!.name, user!.password);
    });
    // Editor-in-chief is the stock role that carries the publish permission.
    grantRoles(`/sites/${publisherSite}`, ['editor-in-chief'], PUBLISHER!.name, 'USER');
};

export const deleteUsers = (): void => {
    [PUBLISHER, OUTSIDER].forEach(user => deleteUser(user!.name));
};
