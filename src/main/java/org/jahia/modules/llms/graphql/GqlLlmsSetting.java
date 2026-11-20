package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.LlmsService;

/**
 * One value a site may set for its generation: what it sets, what it inherits from the configuration of
 * the module, and why a value was refused.
 */
@GraphQLName("LlmsSetting")
@GraphQLDescription("One value a site may set for the generation of its files")
public class GqlLlmsSetting {

    private final LlmsService.Setting setting;

    /**
     * @param setting the value
     */
    public GqlLlmsSetting(LlmsService.Setting setting) {
        this.setting = setting;
    }

    /**
     * @return the key in the configuration
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Key of the value in the configuration")
    public String getKey() {
        return setting.key();
    }

    /**
     * @return NUMBER, TYPES or FLAG
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("NUMBER, TYPES (node type names separated by commas) or FLAG (true or false)")
    public String getKind() {
        return setting.kind();
    }

    /**
     * @return what the site sets, or an empty text
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What the site sets; empty when it sets nothing")
    public String getValue() {
        return setting.value();
    }

    /**
     * @return what applies when the site sets nothing
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("What applies when the site sets nothing: the value of the module configuration")
    public String getInherited() {
        return setting.inherited();
    }

    /**
     * @return the smallest number allowed
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Smallest number allowed, for a NUMBER")
    public int getMin() {
        return setting.min();
    }

    /**
     * @return the largest number allowed
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Largest number allowed, for a NUMBER")
    public int getMax() {
        return setting.max();
    }

    /**
     * @return why the value was refused, or null
     */
    @GraphQLField
    @GraphQLDescription("Why the value was refused: number, range, types or flag; null when it is accepted")
    public String getError() {
        return setting.error();
    }

    /**
     * @return the names that are not known types
     */
    @GraphQLField
    @GraphQLDescription("The names that are not node types of this Jahia, when the error is types")
    public String getDetail() {
        return setting.detail();
    }
}
