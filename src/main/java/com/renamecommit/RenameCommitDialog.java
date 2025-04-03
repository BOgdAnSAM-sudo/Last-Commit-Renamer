package com.renamecommit;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RenameCommitDialog extends DialogWrapper {
    private JPanel contentPanel;
    private EditorTextField commitMessageArea;

    public RenameCommitDialog(@NotNull Project project, @NotNull String commit) {
        super(project);
        contentPanel = new JPanel();
        commitMessageArea = new EditorTextField();
        setTitle("Rename Commit");
        init();

        commitMessageArea.setText(commit);
        commitMessageArea.setMinimumSize(new Dimension(200, 200));
        commitMessageArea.setPreferredSize(new Dimension(200, 200));

        contentPanel.add(commitMessageArea);
    }

    @Override
    protected JComponent createCenterPanel() {
        return contentPanel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (commitMessageArea.getText().trim().isEmpty()) {
            return new ValidationInfo("Commit message cannot be empty", commitMessageArea);
        }
        return super.doValidate();
    }
}