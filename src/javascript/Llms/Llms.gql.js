import {gql} from '@apollo/client';

export const LLMS_SITE = gql`
    query LlmsSite($path: String!) {
        jcr {
            nodeByPath(path: $path) {
                uuid
                path
                displayName
                llms: property(name: "j:llms") {
                    value
                }
                llmsFull: property(name: "j:llmsFull") {
                    value
                }
                baseUrl: property(name: "j:llmsBaseUrl") {
                    value
                }
                site {
                    serverName
                    defaultLanguage
                    languages {
                        language
                        displayName
                        activeInLive
                    }
                }
            }
        }
    }
`;

/**
 * What visitors are served, compared with what is saved on the server: two booleans per file, where
 * reading the live properties would download llms-full.txt a second time. Fails on its own.
 */
export const LLMS_STATUS = gql`
    query LlmsStatus($siteKey: String!, $path: String) {
        llms {
            status(siteKey: $siteKey, path: $path) {
                llms {
                    served
                    current
                }
                llmsFull {
                    served
                    current
                }
            }
        }
    }
`;

export const LLMS_GENERATE = gql`
    query LlmsGenerate($siteKey: String!, $path: String, $language: String!, $baseUrl: String!, $full: Boolean!) {
        llms {
            generate(siteKey: $siteKey, path: $path, language: $language, baseUrl: $baseUrl, full: $full) {
                content
                pages
                failedPages
                truncated
            }
        }
    }
`;

/** The pages that have their own files, and the pages that can be given some: the scopes of the panel. */
export const LLMS_SECTIONS = gql`
    query LlmsSections($siteKey: String!) {
        llms {
            sections(siteKey: $siteKey) {
                path
                title
                hasFiles
            }
        }
    }
`;

/** The files stored on the page at the top of a section. */
export const LLMS_SCOPE = gql`
    query LlmsScope($path: String!) {
        jcr {
            nodeByPath(path: $path) {
                uuid
                path
                displayName
                llms: property(name: "j:llms") {
                    value
                }
                llmsFull: property(name: "j:llmsFull") {
                    value
                }
            }
        }
    }
`;

/** Which pages generation leaves out and how many it lists, as configured: the panel states them. */
export const LLMS_RULES = gql`
    query LlmsRules($siteKey: String!) {
        llms {
            rules(siteKey: $siteKey) {
                excludedTypes
                excludedTreeTypes
                includedTypes
                maxMainResources
                maxPages
                maxDepth
            }
        }
    }
`;

/** Starts llms-full.txt in the background: it renders every page, too long for one silent request. */
export const LLMS_START = gql`
    mutation LlmsStart($siteKey: String!, $path: String, $language: String!, $baseUrl: String!) {
        llms {
            startGeneration(siteKey: $siteKey, path: $path, language: $language, baseUrl: $baseUrl)
        }
    }
`;

/** How far that generation is, page by page, and the file once it is finished. */
export const LLMS_PROGRESS = gql`
    query LlmsProgress($siteKey: String!, $id: String!) {
        llms {
            generationProgress(siteKey: $siteKey, id: $id) {
                started
                total
                done
                finished
                error
                pages {
                    title
                    url
                    state
                }
                generation {
                    content
                    pages
                    failedPages
                    truncated
                }
            }
        }
    }
`;

export const LLMS_SAVE = gql`
    mutation LlmsSave($siteKey: String!, $path: String, $llms: String, $llmsFull: String, $baseUrl: String) {
        llms {
            save(siteKey: $siteKey, path: $path, llms: $llms, llmsFull: $llmsFull, baseUrl: $baseUrl)
        }
    }
`;
