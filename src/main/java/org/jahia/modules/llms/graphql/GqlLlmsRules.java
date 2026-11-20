package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.LlmsService;

import java.util.List;

/**
 * Which pages generation leaves out and how many it lists at most, as configured on this Jahia: the
 * panel states them, so an editor knows why a page is missing without reading the documentation.
 */
@GraphQLName("LlmsRules")
@GraphQLDescription("Which pages generation leaves out and how many it lists, as configured")
public class GqlLlmsRules {

    private final LlmsService.Rules rules;

    /**
     * @param rules the rules in force
     */
    public GqlLlmsRules(LlmsService.Rules rules) {
        this.rules = rules;
    }

    /**
     * @return node types of the pages left out, their sub-pages still listed
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Node types of the pages left out of both files; their sub-pages are still listed")
    public List<String> getExcludedTypes() {
        return rules.excludedTypes();
    }

    /**
     * @return node types of the pages left out with everything below them
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Node types of the pages left out of both files with all their sub-pages")
    public List<String> getExcludedTreeTypes() {
        return rules.excludedTreeTypes();
    }

    /**
     * @return the most pages one file lists
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Most pages one file lists")
    public int getMaxPages() {
        return rules.maxPages();
    }

    /**
     * @return levels of pages below the home page that are listed
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Levels of pages below the home page that are listed")
    public int getMaxDepth() {
        return rules.maxDepth();
    }

    /**
     * @return whether images are written in llms-full.txt
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when images are written in llms-full.txt")
    public boolean isKeepImages() {
        return rules.keepImages();
    }
}
