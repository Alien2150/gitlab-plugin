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

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
    private String schemaSeparator = ".";

    @DataBoundConstructor
    public GitLabReleaseStep(String tagSchema, String changelog, Integer projectId, String ref, String schemaSeperator) {
        this.tagSchema = StringUtils.isEmpty(tagSchema) ? "(.*)" : tagSchema;
        this.changelog = StringUtils.isEmpty(changelog) ? "" : changelog;
        this.projectId = projectId;
        this.ref = StringUtils.isEmpty(ref) ? "master" : ref;
        this.schemaSeparator = StringUtils.isEmpty(schemaSeperator) ? "." : schemaSeperator;
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

    public String getSchemaSeparator() { return schemaSeparator; }

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

                    ArrayList<String> tagList = new ArrayList<String>();

                    GitLabApi client = getClient(run);
                    if (client == null) {
                        println("No GitLab connection configured");
                    } else {
                        for (Tag tag : client.getTags(projectId)) {
                            if (tag.getName().matches(step.getTagSchema())) {
                                tagList.add(tag.getName());
                            }
                        }
                    }

                    // Sort the collections


                    if (tagList.size() == 0) {
                        println("No matching tag-name found");
                    } else {
                        String newTag = generateTag(tagList);
                        println("Generating new tag: " + newTag);

                        // First create a new tag
                        client.createNewTag(projectId, step.getRef(), newTag, null, null);

                        // Add the changelog / release notes to it
                        client.createNewRelease(projectId, newTag, step.getChangelog());
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

        protected String generateTag(ArrayList<String> tagList) {
            Collections.sort(tagList);
            String latestTag = tagList.get(tagList.size()-1);
            String[] chunks = latestTag.split(Pattern.quote(step.getSchemaSeparator()));
            if (chunks.length == 0) {
                println("Could not split the latest tag: " + latestTag);
                return "";
            }
            String lastChunk = chunks[chunks.length - 1];
            // Try to parse to number
            try {
                Integer lastChunkAsNumber = Integer.parseInt(lastChunk) + 1;
                // Replace last chunk
                chunks[chunks.length - 1] = lastChunkAsNumber.toString();

                // JAVA 8 - version:
                // return String.join(step.getSchemaSeparator(), Arrays.asList(chunks));

                // Java 7 - version:
                String res = "";
                for (String chunk : chunks) {
                    res += String.format("%s%s", chunk, step.getSchemaSeparator());
                }

                // Remove last character (".")
                res = res.substring(0, res.length() - 1);

                // Return joined string
                return res;
            } catch (NumberFormatException nfe) {
                println("Not a number. Please use a valid tags in the repository like 1.0.0");
                throw nfe;
            }
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
