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

/** What visitors are served. The site may not be published at all, so this query fails on its own. */
export const LLMS_LIVE = gql`
    query LlmsLive($path: String!) {
        jcr(workspace: LIVE) {
            nodeByPath(path: $path) {
                uuid
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

export const LLMS_SAVE = gql`
    mutation LlmsSave($siteKey: String!, $llms: String, $llmsFull: String, $baseUrl: String) {
        llms {
            save(siteKey: $siteKey, llms: $llms, llmsFull: $llmsFull, baseUrl: $baseUrl)
        }
    }
`;
