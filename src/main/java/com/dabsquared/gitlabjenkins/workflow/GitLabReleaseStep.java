package com.dabsquared.gitlabjenkins.workflow;

import com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabApi;
import com.dabsquared.gitlabjenkins.gitlab.api.model.Tag;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.dabsquared.gitlabjenkins.connection.GitLabConnectionProperty.getClient;

/**
 * @author Thomas "Alien2150" Zimmer
 */
@ExportedBean
public class GitLabReleaseStep extends AbstractStepImpl {

    private static final Logger LOGGER = Logger.getLogger(GitLabReleaseStep.class.getName());

    private String tagSchema = "1.0.%";
    private String changelog = "";
    private Integer projectId = null;
    private String ref = "master";

    @DataBoundConstructor
    public GitLabReleaseStep(String tagSchema, String changelog, Integer projectId, String ref) {
        this.tagSchema = StringUtils.isEmpty(tagSchema) ? null : tagSchema;
        this.changelog = StringUtils.isEmpty(changelog) ? null : changelog;
        this.projectId = projectId;
        this.ref = StringUtils.isEmpty(ref) ? "master" : ref;
    }

    public String getTagSchema() {
        return tagSchema;
    }

    public String getChangelog() {
        return changelog;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {
        private static final long serialVersionUID = 1;

        @StepContextParameter
        private transient Run<?, ?> run;

        @Inject
        private transient GitLabReleaseStep step;

        private BodyExecution body;

        @Override
        protected Void run() throws Exception {
            try {
                Integer projectId = step.getProjectId();
                GitLabWebHookCause cause = run.getCause(GitLabWebHookCause.class);
                if (cause != null) {
                     // Get from the web-hook cause
                     projectId = cause.getData().getTargetProjectId();
                }

                if (projectId != null) {

                    List<String> tagList = new ArrayList<String>();

                    GitLabApi client = getClient(run);
                    if (client == null) {
                        println("No GitLab connection configured");
                    } else {
                        println("Beginning with release process");

                        for (Tag tag : client.getTags(projectId)) {
                            println("Name" + tag.getName());
                            if (tag.getName().matches(step.getTagSchema())) {
                                tagList.add(tag.getName());
                                println("Added this tag");
                            }
                        }
                    }

                    // Sort the collections


                    if (tagList.size() == 0) {
                        println("No matching tag-name found");
                    } else {
                        Collections.sort(tagList);

                        println("Last entry is : " + tagList.get(0));
                        println("Last entry is : " + tagList.get(tagList.size()-1));

                        // "split" by "."
                        //String newTag = generateTag(latestTag);
                        //client.createNewTag(projectId, step.getRef(), newTag, null, null);
                        //client.createNewRelease(projectId, newTag, step.getChangeLog());
                    }
                } else {
                    println("Project Id missing");
                }
            } catch (Exception e) {
                e.printStackTrace();
                println("Error accessing the tags : " + e.getMessage());
            }

            return null;
        }

        private void println(String message) {
            TaskListener listener = getTaskListener();
            if (listener == null) {
                LOGGER.log(Level.FINE, "failed to print message {0} due to null TaskListener", message);
            } else {
                listener.getLogger().println(message);
            }
        }

        private void printf(String message, Object... args) {
            TaskListener listener = getTaskListener();
            if (listener == null) {
                LOGGER.log(Level.FINE, "failed to print message {0} due to null TaskListener", String.format(message, args));
            } else {
                listener.getLogger().printf(message, args);
            }
        }

        private TaskListener getTaskListener() {
            StepContext context = getContext();
            if (!context.isReady()) {
                return null;
            }
            try {
                return context.get(TaskListener.class);
            } catch (Exception x) {
                return null;
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(GitLabReleaseStep.Execution.class);
        }

        @Override
        public String getDisplayName() {
            return "Create a new tag and release it to Gitlab";
        }

        @Override
        public String getFunctionName() {
            return "gitlabRelease";
        }
    }
}
