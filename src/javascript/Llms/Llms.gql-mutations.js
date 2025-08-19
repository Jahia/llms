import gql from 'graphql-tag';

export const LlmsMutation = gql`
    mutation llms($path: String!, $value: String!, $publish: Boolean!) {
      jcr {
        mutateNode(pathOrId:$path) {
          addMixins(mixins:["jmix:llms"])
          mutateProperty(name:"j:llms") {
            setValue(value:$value)
          }
          publish @include(if: $publish)
        }
      }
    }
`;
