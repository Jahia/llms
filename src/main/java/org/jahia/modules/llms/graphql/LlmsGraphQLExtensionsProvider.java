package org.jahia.modules.llms.graphql;

import org.jahia.modules.graphql.provider.dxm.DXGraphQLExtensionsProvider;
import org.osgi.service.component.annotations.Component;

/**
 * Registers this bundle with graphql-dxm-provider so its {@code @GraphQLTypeExtension} classes are scanned.
 */
@Component(service = DXGraphQLExtensionsProvider.class, immediate = true)
public class LlmsGraphQLExtensionsProvider implements DXGraphQLExtensionsProvider {
}
