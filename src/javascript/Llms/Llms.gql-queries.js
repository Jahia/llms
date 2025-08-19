import gql from 'graphql-tag';

export const LlmsQuery = gql`
    query llms($path: String!, $language: String!) {
        jcr {
            nodeByPath(path:$path) {
                workspace
                uuid
                path
                displayName
                property(name:"j:llms") {
                    value
                }
                aggregatedPublicationInfo(language:$language) {
                    publicationStatus
                }
            }
        }
    }
`;
