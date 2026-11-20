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
    query LlmsStatus($siteKey: String!) {
        llms {
            status(siteKey: $siteKey) {
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
    query LlmsGenerate($siteKey: String!, $language: String!, $baseUrl: String!, $full: Boolean!) {
        llms {
            generate(siteKey: $siteKey, language: $language, baseUrl: $baseUrl, full: $full) {
                content
                pages
                failedPages
                truncated
            }
        }
    }
`;

/** Starts llms-full.txt in the background: it renders every page, too long for one silent request. */
export const LLMS_START = gql`
    mutation LlmsStart($siteKey: String!, $language: String!, $baseUrl: String!) {
        llms {
            startGeneration(siteKey: $siteKey, language: $language, baseUrl: $baseUrl)
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
    mutation LlmsSave($siteKey: String!, $llms: String, $llmsFull: String, $baseUrl: String) {
        llms {
            save(siteKey: $siteKey, llms: $llms, llmsFull: $llmsFull, baseUrl: $baseUrl)
        }
    }
`;
