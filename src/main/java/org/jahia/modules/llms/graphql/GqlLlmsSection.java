package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.LlmsService;

/**
 * A page of the site that has, or can be given, its own llms.txt covering the pages below it.
 */
@GraphQLName("LlmsSection")
@GraphQLDescription("A page that has, or can be given, its own llms.txt for the pages below it")
public class GqlLlmsSection {

    private final LlmsService.SectionRef section;

    /**
     * @param section the page
     */
    public GqlLlmsSection(LlmsService.SectionRef section) {
        this.section = section;
    }

    /**
     * @return the repository path of the page
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Repository path of the page")
    public String getPath() {
        return section.path();
    }

    /**
     * @return the title of the page
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Title of the page")
    public String getTitle() {
        return section.title();
    }

    /**
     * @return whether the page already carries files
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True when the page already carries an llms.txt or an llms-full.txt")
    public boolean isHasFiles() {
        return section.hasFiles();
    }
}
