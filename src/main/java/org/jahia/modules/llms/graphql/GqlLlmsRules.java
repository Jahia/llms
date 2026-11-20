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
     * @return node types a page must carry to be listed; empty when every page is
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Node types a page must carry to be listed; empty when every page is listed")
    public List<String> getIncludedTypes() {
        return rules.includedTypes();
    }

    /**
     * @return the most contents shown as a page one file lists
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Most contents shown as a page of their own one file lists; 0 when none is listed")
    public int getMaxMainResources() {
        return rules.maxMainResources();
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
