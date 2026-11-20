package org.jahia.modules.llms.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.llms.service.GenerationJob;

import java.util.List;

/**
 * How far a generation of llms-full.txt is, and the file once it is finished.
 */
@GraphQLName("LlmsProgress")
@GraphQLDescription("How far a generation of llms-full.txt is, and the file once it is finished")
public class GqlLlmsProgress {

    private final GenerationJob.Snapshot snapshot;

    /**
     * @param snapshot the generation as it stands now
     */
    public GqlLlmsProgress(GenerationJob.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * @return whether the pages to render are known yet
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("False while the site is still being read: the pages are not known yet")
    public boolean isStarted() {
        return snapshot.started();
    }

    /**
     * @return how many pages the generation renders
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Pages to render")
    public int getTotal() {
        return snapshot.pages().size();
    }

    /**
     * @return how many pages are rendered, empty or failed
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("Pages no longer pending")
    public int getDone() {
        return snapshot.done();
    }

    /**
     * @return the pages, in the order of the file
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("The pages, in the order of the file")
    public List<GqlLlmsPageProgress> getPages() {
        return snapshot.pages().stream().map(GqlLlmsPageProgress::new).toList();
    }

    /**
     * @return whether the generation is over
     */
    @GraphQLField
    @GraphQLNonNull
    @GraphQLDescription("True once the generation is over, with a file or with an error")
    public boolean isFinished() {
        return snapshot.finished();
    }

    /**
     * @return why the generation failed, or null
     */
    @GraphQLField
    @GraphQLDescription("Why the generation failed, when it did")
    public String getError() {
        return snapshot.error();
    }

    /**
     * @return the generated file, once finished without error
     */
    @GraphQLField
    @GraphQLDescription("The generated file, once finished without error")
    public GqlLlmsGeneration getGeneration() {
        return snapshot.generation() == null ? null : new GqlLlmsGeneration(snapshot.generation());
    }
}
