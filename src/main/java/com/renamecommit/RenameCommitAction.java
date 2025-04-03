package com.renamecommit;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import git4idea.GitCommit;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class RenameCommitAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent actionEvent) {
        Project project = actionEvent.getProject();
        boolean hasRepository = project != null &&
                !GitRepositoryManager.getInstance(project).getRepositories().isEmpty();
        actionEvent.getPresentation().setEnabledAndVisible(hasRepository);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent actionEvent) {
        Project project = actionEvent.getProject();
        if (project == null || project.getProjectFile() == null) return;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Renaming commit", true) {
            private GitRepository repository;
            private String commitMessage;
            private VcsException exception;

            @Override
            public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                try {
                    VirtualFile projectRoot = GitUtil.getRootForFile(project, project.getProjectFile());
                    repository = GitUtil.getRepositoryForFile(project, projectRoot);

                    if (repository == null) {
                        exception = new VcsException("No Git repository found");
                        return;
                    }

                    List<GitCommit> commits = GitHistoryUtils.history(project, repository.getRoot());
                    if (commits.isEmpty()) {
                        exception = new VcsException("Log is empty");
                        return;
                    }

                    Hash lastCommitId = commits.get(0).getId();
                    commitMessage = Objects.requireNonNull(
                            Objects.requireNonNull(GitHistoryUtils.collectCommitsMetadata(project, projectRoot,
                                            String.valueOf(lastCommitId)))
                                    .get(0).getFullMessage());

                } catch (VcsException e) {
                    exception = e;
                }
            }

            @Override
            public void onSuccess() {
                if (!ApplicationManager.getApplication().isDispatchThread()) {
                    Messages.showErrorDialog("Executing UI changes in BGT thread", "Error");
                }

                if (exception != null) {
                    Messages.showErrorDialog(project, exception.getMessage(), "Error");
                    return;
                }

                RenameCommitDialog dialog = new RenameCommitDialog(project, commitMessage);
                dialog.show();

                if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
                    String newMessage = dialog.getCommitMessage();
                    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating commit", true) {
                        @Override
                        public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                            renameCommit(project, repository, newMessage);
                        }
                    });
                }
            }
        });
    }

    private void renameCommit(Project project, GitRepository repository, String newMessage) {
        try {
            GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.COMMIT);
            handler.addParameters("--amend", "-m", newMessage);
            handler.setSilent(false);
            handler.setStdoutSuppressed(false);
            Git.getInstance().runCommand(handler);
            repository.update();
            ChangeListManager.getInstance(project).invokeAfterUpdate(false, () -> {});
        } catch (Exception e) {
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog(project, "Failed to rename commit: " + e.getMessage(), "Error"));
        }
    }
}