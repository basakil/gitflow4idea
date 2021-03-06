package gitflow.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRepository;
import gitflow.GitflowConfigUtil;
import gitflow.ui.NotifyUtil;
import org.jetbrains.annotations.NotNull;

public class FinishBugfixAction extends GitflowAction {

    String customBugfixName =null;

    public FinishBugfixAction() {
        super("Finish Bugfix");
    }

    public FinishBugfixAction(GitRepository repo) {
        super(repo, "Finish Bugfix");
    }

    FinishBugfixAction(GitRepository repo, String name) {
        super(repo, "Finish Bugfix");
        customBugfixName =name;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        super.actionPerformed(e);

        String currentBranchName = GitBranchUtil.getBranchNameOrRev(myRepo);
        if (currentBranchName.isEmpty()==false){

            final AnActionEvent event=e;
            final String bugfixName;
            // Check if a bugfix name was specified, otherwise take name from current branch
            if (customBugfixName !=null){
                bugfixName = customBugfixName;
            }
            else{
                bugfixName = GitflowConfigUtil.getBugfixNameFromBranch(myProject, myRepo, currentBranchName);
            }

            this.runAction(myProject, bugfixName);
        }

    }

    public void runAction(final Project project, final String bugfixName){
        super.runAction(project, null, bugfixName, null);

        final GitflowErrorsListener errorLineHandler = new GitflowErrorsListener(myProject);
        final FinishBugfixAction that = this;

        //get the base branch for this bugfix
        final String baseBranch = GitflowConfigUtil.getBaseBranch(project, myRepo, bugfixPrefix+bugfixName);

        new Task.Backgroundable(myProject,"Finishing bugfix "+bugfixName,false){
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                GitCommandResult result =  myGitflow.finishBugfix(myRepo, bugfixName, errorLineHandler);

                if (result.success()) {
                    String finishedBugfixMessage = String.format("The bugfix branch '%s%s' was merged into '%s'", bugfixPrefix, bugfixName, baseBranch);
                    NotifyUtil.notifySuccess(myProject, bugfixName, finishedBugfixMessage);
                }
                else if(errorLineHandler.hasMergeError){
                    // (merge errors are handled in the onSuccess handler)
                }
                else {
                    NotifyUtil.notifyError(myProject, "Error", "Please have a look at the Version Control console for more details");
                }

                myRepo.update();

            }

            @Override
            public void onSuccess() {
                super.onSuccess();

                //merge conflicts if necessary
                if (errorLineHandler.hasMergeError){
                    if (handleMerge()){
                        that.runAction(project, bugfixName);
                        FinishBugfixAction completeFinishBugfixAction = new FinishBugfixAction(myRepo, bugfixName);
                    }

                }

            }
        }.queue();
    }

}