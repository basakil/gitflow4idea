package gitflow;

import com.intellij.openapi.project.Project;
import git4idea.commands.*;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import gitflow.ui.NotifyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Opher Vishnia / opherv.com / opherv@gmail.com
 */

public class GitflowImpl extends GitImpl implements Gitflow {

    //we must use reflection to add this command, since the git4idea implementation doesn't expose it
    private GitCommand GitflowCommand() {

        return GitCommand("flow");
    }


    private Method getAccesibleGitMethod() {
        Method m = null;
        try {
            m = GitCommand.class.getDeclaredMethod("write", String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        m.setAccessible(true);

        return m;
    }

    private GitCommand GitCommand(String arg) {
        Method m = getAccesibleGitMethod();
        GitCommand command = null;
        if (m != null) {
            try {
                command = (GitCommand) m.invoke(null, arg);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return command;
    }


    private void addOptionsCommand(GitLineHandler h, Project project, String optionId){
        HashMap<String,String> optionMap = GitflowOptionsFactory.getOptionById(optionId);
        if (GitflowConfigurable.isOptionActive(project, optionMap.get("id"))){
            h.addParameters(optionMap.get("flag"));
        }
    }

    private GitCommandResult preInitRepo(@NotNull GitRepository repository, @Nullable GitLineHandlerListener... listeners) {
        GitCommandResult gitCommandResult = null;
/*
        gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", "-t", "origin/"+BAConstants.PRODUCTION_BRANCH);
        if (!gitCommandResult.success()) {
            return gitCommandResult;
        }

        gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", "-t", "origin/"+BAConstants.DEVELOPMENT_BRANCH);
        if (!gitCommandResult.success()) {
            return gitCommandResult;
        }
  */

        //does not pollute clients repo:
//        gitCommandResult = runGitCommandVisual(repository, listeners,"fetch");

        gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", BAConstants.PRODUCTION_BRANCH);
        if (!gitCommandResult.success()) {
            gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", "-b" , BAConstants.PRODUCTION_BRANCH);
        }
        gitCommandResult = runGitCommandVisual(repository, listeners,"pull", "origin", BAConstants.PRODUCTION_BRANCH);
        gitCommandResult = runGitCommandVisual(repository, listeners,"push", "origin", BAConstants.PRODUCTION_BRANCH);


        gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", BAConstants.DEVELOPMENT_BRANCH);
        if (!gitCommandResult.success()) {
            gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", "-b" , BAConstants.DEVELOPMENT_BRANCH);
        }
        gitCommandResult = runGitCommandVisual(repository, listeners,"pull", "origin", BAConstants.DEVELOPMENT_BRANCH);
        gitCommandResult = runGitCommandVisual(repository, listeners,"push", "origin", BAConstants.DEVELOPMENT_BRANCH);

        return newSuccessResult();
    }

    public GitCommandResult initRepo(@NotNull GitRepository repository,
                                     GitflowInitOptions initOptions, @Nullable GitLineHandlerListener... listeners) {

        GitCommandResult result = preInitRepo(repository, listeners);
        if (!result.success()) {
            return result;
        }

        if (initOptions.isUseDefaults()) {
            final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
            h.setSilent(false);
            h.setStdoutSuppressed(false);
            h.setStderrSuppressed(false);

            h.addParameters("init");
            h.addParameters("-d");

            result = runCommand(h);
        } else {


            final GitInitLineHandler h = new GitInitLineHandler(initOptions, repository.getProject(), repository.getRoot(), GitflowCommand());

            h.setSilent(false);
            h.setStdoutSuppressed(false);
            h.setStderrSuppressed(false);

            h.addParameters("init");

            for (GitLineHandlerListener listener : listeners) {
                h.addLineListener(listener);
            }
            result = runCommand(h);
        }


        return result;
    }


    //feature

    public GitCommandResult startFeature(@NotNull GitRepository repository,
                                         @NotNull String featureName,
                                         @Nullable String baseBranch,
                                         @Nullable GitLineHandlerListener... listeners) {

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        h.setSilent(false);

        h.addParameters("feature");
        h.addParameters("start");

//        addOptionsCommand(h, repository.getProject(),"FEATURE_fetchFromOrigin");

        h.addParameters(featureName);

        if (baseBranch != null) {
            h.addParameters(baseBranch);
        }

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    private GitCommandResult runGitCommand(@NotNull GitRepository repository,
                                           @Nullable GitLineHandlerListener[] listeners,
                                           @NotNull String command,
                                           @Nullable String ...params) {

        GitCommand gitCommand = GitCommand(command);

        final GitLineHandler gitHandler = new GitLineHandler(
                repository.getProject(),
                repository.getRoot(),
                gitCommand,
                new ArrayList<String>());

        gitHandler.setSilent(false);
        setUrl(gitHandler, repository);

        if (params != null) {
            for (String param : params) {
                gitHandler.addParameters(param);
            }
        }
        if (listeners != null) {
            for (GitLineHandlerListener listener : listeners) {
                gitHandler.addLineListener(listener);
            }
        }

        return runCommand(gitHandler);
    }

    private GitCommandResult runGitCommandVisual(@NotNull GitRepository repository,
                                                 @Nullable GitLineHandlerListener[] listeners,
                                                 @NotNull String command,
                                                 @Nullable String ...params) {

        GitCommandResult gcr = runGitCommand(repository, listeners, command, params);

        if (! gcr.success()) {
            NotifyUtil.notifyError(repository.getProject(), "git " + command + " error", "Please have a look at the Version Control console for more details");
        }

        return gcr;
    }

    public GitCommandResult finishFeature(@NotNull GitRepository repository,
                                          @NotNull String featureName,
                                          @Nullable GitLineHandlerListener... listeners) {

        GitCommandResult gitCommandResult = null;
        gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", BAConstants.DEVELOPMENT_BRANCH);
        if (!gitCommandResult.success()) {
            return gitCommandResult;
        }

        gitCommandResult = runGitCommandVisual(repository, listeners,"pull", "origin", BAConstants.DEVELOPMENT_BRANCH);
        if (!gitCommandResult.success()) {
            return gitCommandResult;
        }

        gitCommandResult = runGitCommandVisual(repository, listeners,"checkout", BAConstants.FEATURE_PREFIX+featureName);
        if (!gitCommandResult.success()) {
            return gitCommandResult;
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());

        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("feature");
        h.addParameters("finish");

//        addOptionsCommand(h, repository.getProject(),"FEATURE_keepRemote");
//        addOptionsCommand(h, repository.getProject(),"FEATURE_keepLocal");
//        addOptionsCommand(h, repository.getProject(),"FEATURE_keepBranch");
//        addOptionsCommand(h, repository.getProject(),"FEATURE_fetchFromOrigin");
//        addOptionsCommand(h, repository.getProject(),"FEATURE_pushOnFinish");
        h.addParameters("--push");

//        addOptionsCommand(h, repository.getProject(),"FEATURE_squash");

        h.addParameters(featureName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }

        gitCommandResult = runCommand(h);

        return gitCommandResult;
    }


    public GitCommandResult publishFeature(@NotNull GitRepository repository,
                                           @NotNull String featureName,
                                           @Nullable GitLineHandlerListener... listeners) {

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("feature");
        h.addParameters("publish");
        h.addParameters(featureName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }


    // feature pull seems to be kind of useless. see
    // http://stackoverflow.com/questions/18412750/why-doesnt-git-flow-feature-pull-track
    public GitCommandResult pullFeature(@NotNull GitRepository repository,
                                        @NotNull String featureName,
                                        @NotNull GitRemote remote,
                                        @Nullable GitLineHandlerListener... listeners) {
        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);
        h.addParameters("feature");
        h.addParameters("pull");
        h.addParameters(remote.getName());
        h.addParameters(featureName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult trackFeature(@NotNull GitRepository repository,
                                         @NotNull String featureName,
                                         @NotNull GitRemote remote,
                                         @Nullable GitLineHandlerListener... listeners) {
        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);
        h.addParameters("feature");
        h.addParameters("track");
        h.addParameters(featureName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }


    private GitCommandResult newNotImpelentedResult() {
        return new GitCommandResult(true, 404, new ArrayList<String>(){{
            add("not implemented");
        }}, new ArrayList<String>(){{
            add("not implemented");
        }});
    }

    private GitCommandResult newSuccessResult() {
        return new GitCommandResult(false, 0, new ArrayList<String>(){{
            add("Success");
        }}, new ArrayList<String>(){{
            add("Completed Successfully");
        }});
    }

    //release

    public GitCommandResult startRelease(@NotNull GitRepository repository,
                                         @NotNull String releaseName,
                                         @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        h.setSilent(false);

        h.addParameters("release");
        h.addParameters("start");

        addOptionsCommand(h, repository.getProject(),"RELEASE_fetchFromOrigin");

        h.addParameters(releaseName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult finishRelease(@NotNull GitRepository repository,
                                          @NotNull String releaseName,
                                          @NotNull String tagMessage,
                                          @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("release");
        h.addParameters("finish");

        addOptionsCommand(h, repository.getProject(),"RELEASE_fetchFromOrigin");
        addOptionsCommand(h, repository.getProject(),"RELEASE_pushOnFinish");
        addOptionsCommand(h, repository.getProject(),"RELEASE_keepRemote");
        addOptionsCommand(h, repository.getProject(),"RELEASE_keepLocal");
        addOptionsCommand(h, repository.getProject(),"RELEASE_keepBranch");
//        addOptionsCommand(h, repository.getProject(),"RELEASE_squash");

        HashMap<String,String> dontTag = GitflowOptionsFactory.getOptionById("RELEASE_dontTag");
        if (GitflowConfigurable.isOptionActive(repository.getProject(), dontTag.get("id"))){
            h.addParameters(dontTag.get("flag"));
        }
        else{
            h.addParameters("-m");
            h.addParameters(tagMessage);
        }

        h.addParameters(releaseName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }


    public GitCommandResult publishRelease(@NotNull GitRepository repository,
                                           @NotNull String releaseName,
                                           @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);

        h.setSilent(false);

        h.addParameters("release");
        h.addParameters("publish");
        h.addParameters(releaseName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult trackRelease(@NotNull GitRepository repository,
                                         @NotNull String releaseName,
                                         @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("release");
        h.addParameters("track");
        h.addParameters(releaseName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }


    //hotfix

    public GitCommandResult startHotfix(@NotNull GitRepository repository,
                                        @NotNull String hotfixName,
                                        @Nullable String baseBranch,
                                        @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        h.setSilent(false);

        h.addParameters("hotfix");
        h.addParameters("start");

        addOptionsCommand(h, repository.getProject(),"HOTFIX_fetchFromOrigin");

        h.addParameters(hotfixName);

        if (baseBranch != null) {
            h.addParameters(baseBranch);
        }

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult finishHotfix(@NotNull GitRepository repository,
                                         @NotNull String hotfixName,
                                         @NotNull String tagMessage,
                                         @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("hotfix");
        h.addParameters("finish");

        addOptionsCommand(h, repository.getProject(),"HOTFIX_fetchFromOrigin");
        addOptionsCommand(h, repository.getProject(),"HOTFIX_pushOnFinish");

        HashMap<String,String> dontTag = GitflowOptionsFactory.getOptionById("HOTFIX_dontTag");
        if (GitflowConfigurable.isOptionActive(repository.getProject(), dontTag.get("id"))){
            h.addParameters(dontTag.get("flag"));
        }
        else{
            h.addParameters("-m");
            h.addParameters(tagMessage);
        }

        h.addParameters(hotfixName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult publishHotfix(@NotNull GitRepository repository,
                                          @NotNull String hotfixName,
                                          @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);

        h.setSilent(false);

        h.addParameters("hotfix");
        h.addParameters("publish");
        h.addParameters(hotfixName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    private void setUrl(GitLineHandler h, GitRepository repository) {
        ArrayList<GitRemote> remotes = new ArrayList<GitRemote>(repository.getRemotes());

        //make sure a remote repository is available
        if (!remotes.isEmpty()) {
            h.setUrl(remotes.iterator().next().getFirstUrl());
        }
    }

    // Bugfix

    public GitCommandResult startBugfix(@NotNull GitRepository repository,
                                         @NotNull String bugfixName,
                                         @Nullable String baseBranch,
                                         @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        h.setSilent(false);

        h.addParameters("bugfix");
        h.addParameters("start");

        addOptionsCommand(h, repository.getProject(),"BUGFIX_fetchFromOrigin");

        h.addParameters(bugfixName);

        if (baseBranch != null) {
            h.addParameters(baseBranch);
        }

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult finishBugfix(@NotNull GitRepository repository,
                                          @NotNull String bugfixName,
                                          @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());

        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("bugfix");
        h.addParameters("finish");


        addOptionsCommand(h, repository.getProject(),"BUGFIX_keepRemote");
        addOptionsCommand(h, repository.getProject(),"BUGFIX_keepLocal");
        addOptionsCommand(h, repository.getProject(),"BUGFIX_keepBranch");
        addOptionsCommand(h, repository.getProject(),"BUGFIX_fetchFromOrigin");
//        addOptionsCommand(h, repository.getProject(),"BUGFIX_squash");

        h.addParameters(bugfixName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }


    public GitCommandResult publishBugfix(@NotNull GitRepository repository,
                                           @NotNull String bugfixName,
                                           @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);

        h.addParameters("bugfix");
        h.addParameters("publish");
        h.addParameters(bugfixName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }


    // feature/bugfix pull seems to be kind of useless. see
    // http://stackoverflow.com/questions/18412750/why-doesnt-git-flow-feature-pull-track
    public GitCommandResult pullBugfix(@NotNull GitRepository repository,
                                        @NotNull String bugfixName,
                                        @NotNull GitRemote remote,
                                        @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);
        h.addParameters("bugfix");
        h.addParameters("pull");
        h.addParameters(remote.getName());
        h.addParameters(bugfixName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

    public GitCommandResult trackBugfix(@NotNull GitRepository repository,
                                         @NotNull String bugfixName,
                                         @NotNull GitRemote remote,
                                         @Nullable GitLineHandlerListener... listeners) {

        if (1==1) {
            return newNotImpelentedResult();
        }

        final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitflowCommand());
        setUrl(h, repository);
        h.setSilent(false);
        h.addParameters("bugfix");
        h.addParameters("track");
        h.addParameters(bugfixName);

        for (GitLineHandlerListener listener : listeners) {
            h.addLineListener(listener);
        }
        return runCommand(h);
    }

}
