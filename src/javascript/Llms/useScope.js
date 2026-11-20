import {useState} from 'react';
import {useQuery} from '@apollo/client';
import {LLMS_SCOPE, LLMS_SECTIONS} from './Llms.gql';

/**
 * The scope of the panel: the whole site (null) or the path of a page at the top of a section, with the
 * files stored on that page. The files of the site are read by the panel itself: this hook reads only
 * what a section adds, so the panel stays in place while a section loads.
 */
export const useScope = siteKey => {
    const [scope, setScope] = useState(null);
    // Read on its own: without it the panel shows the whole site only.
    const sections = useQuery(LLMS_SECTIONS, {variables: {siteKey}, errorPolicy: 'all'});
    const files = useQuery(LLMS_SCOPE, {variables: {path: scope}, skip: !scope, fetchPolicy: 'network-only'});
    const isSection = Boolean(scope);

    const list = sections.data?.llms?.sections || [];

    return {
        scope,
        setScope,
        sections: list,
        // The title of a section, or undefined for the whole site and for a path that is not listed.
        titleOf: path => list.find(section => section.path === path)?.title,
        node: isSection ? files.data?.jcr?.nodeByPath : undefined,
        isLoading: isSection && files.loading,
        error: isSection ? files.error : undefined,
        refetch: () => Promise.all([sections.refetch(), isSection ? files.refetch() : null])
    };
};
