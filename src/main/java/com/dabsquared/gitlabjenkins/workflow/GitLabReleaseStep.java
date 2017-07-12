package com.dabsquared.gitlabjenkins.workflow;

import com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
import com.dabsquared.gitlabjenkins.gitlab.api.GitLabApi;
import com.dabsquared.gitlabjenkins.gitlab.api.model.Branch;
import com.dabsquared.gitlabjenkins.gitlab.api.model.BuildState;
import com.dabsquared.gitlabjenkins.gitlab.api.model.Tag;
import com.dabsquared.gitlabjenkins.util.CommitStatusUpdater;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import javax.inject.Inject;

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
    private String artifactsFilePath = "";
    private String changelogPath = "";
    private Integer projectId = null;

    @DataBoundConstructor
    public GitLabReleaseStep(String tagSchema, String artifactsFilePath, String changelogPath, Integer projectId) {
        this.tagSchema = StringUtils.isEmpty(tagSchema) ? null : tagSchema;
        this.artifactsFilePath = StringUtils.isEmpty(artifactsFilePath) ? null : artifactsFilePath;
        this.changelogPath = StringUtils.isEmpty(changelogPath) ? null : changelogPath;
        this.projectId = projectId;
    }

    public String getTagSchema() {
        return tagSchema;
    }

    public String getArtifactsFilePath() {
        return artifactsFilePath;
    }

    public String getChangelogPath() {
        return changelogPath;
    }

    public Integer getProjectId() {
        return projectId;
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

                println("Accessing projectId " + projectId);

                // TODO This should be parameter as well (Being able to switch to a external projectId)
                if (projectId != null) {
                        GitLabApi client = getClient(run);
                        if (client == null) {
                            println("No GitLab connection configured");
                        } else {
                            println("Beginning with release process");

                            for (Tag tag : client.getTags(projectId)) {
                                println("Tags: " + tag);
                            }

                            // Create new tag
                            client.createNewTag(projectId, "master", "1.0.3", null, null);

                            client.createNewRelease(projectId, "1.0.3", "* My amaizing changes ....!!!!\n* Test 2");
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
