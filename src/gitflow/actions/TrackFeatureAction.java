package gitflow.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import git4idea.GitVcs;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRemote;
import git4idea.util.GitUIUtil;
import gitflow.GitflowConfigUtil;
import gitflow.ui.GitflowBranchChooseDialog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;

public class TrackFeatureAction extends GitflowAction {

    TrackFeatureAction(){
        super("Track Feature");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        super.actionPerformed(e);

        ArrayList<String> remoteBranches = branchUtil.getRemoteBranchNames();
        ArrayList<String> remoteFeatureBranches = new ArrayList<String>();

        //get only the branches with the proper prefix
        for(Iterator<String> i = remoteBranches.iterator(); i.hasNext(); ) {
            String item = i.next();
            if (item.contains(featurePrefix)){
                remoteFeatureBranches.add(item);
            }
        }

        if (remoteBranches.size()>0){
            GitflowBranchChooseDialog branchChoose = new GitflowBranchChooseDialog(myProject,remoteFeatureBranches);

            branchChoose.show();
            if (branchChoose.isOK()){
                String branchName= branchChoose.getSelectedBranchName();
                final String featureName= GitflowConfigUtil.getFeatureNameFromBranch(myProject, branchName);
                final GitRemote remote=branchUtil.getRemoteByBranch(branchName);
                final GitflowErrorsListener errorLineHandler = new GitflowErrorsListener(myProject);

                new Task.Backgroundable(myProject,"Tracking feature "+featureName,false){
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        GitCommandResult result = myGitflow.trackFeature(repo, featureName, remote, errorLineHandler);

                        if (result.success()){
                            String trackedFeatureMessage = String.format("A new branch '%s%s' was created", featurePrefix, featureName);
                            GitUIUtil.notifySuccess(myProject, featureName, trackedFeatureMessage);

                        }
                        else{
                            GitUIUtil.notifyError(myProject,"Error","Please have a look at the Version Control console for more details");
                        }

                        repo.update();

                    }
                }.queue();
            }
        }
        else{
            new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), "Error", "No remote branches", NotificationType.ERROR).notify(myProject);
        }

    }
}