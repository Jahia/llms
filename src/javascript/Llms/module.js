/** The module key, which is also its i18n namespace. */
export const MODULE = 'llms';

/** The two files, by property name on the site node. */
export const FILES = [
    {id: 'llms', property: 'llms', fileName: 'llms.txt', full: false},
    {id: 'llmsFull', property: 'llmsFull', fileName: 'llms-full.txt', full: true}
];

/** What the CND constraint of j:llmsBaseUrl accepts: a scheme, a host and an optional port. */
export const BASE_URL = /^https?:\/\/[A-Za-z0-9.-]+(:\d{1,5})?$/;

/** The text the CND autocreates for llms.txt: not a real file. */
export const PLACEHOLDER = '# Title';
