package org.jabref.gui.groups;

import java.util.ArrayList;
import java.util.List;

import org.jabref.gui.undo.AbstractUndoableJabRefEdit;
import org.jabref.model.groups.GroupTreeNode;

public class UndoableModifySubtree extends AbstractUndoableJabRefEdit {

    /**
     * A backup of the groups before the modification
     */
    private final GroupTreeNode mGroupRoot;

    private final GroupTreeNode mSubtreeBackup;

    /**
     * The path to the global groups root node
     */
    private final List<Integer> mSubtreeRootPath;

    /**
     * This holds the new subtree (the root's modified children) to allow redo.
     */
    private final List<GroupTreeNode> mModifiedSubtree = new ArrayList<>();

    private final String mName;

    /**
     * @param subtree The root node of the subtree that was modified (this node may not be modified, it is just used as a convenience handle).
     */
    public UndoableModifySubtree(GroupTreeNodeViewModel groupRoot,
                                 GroupTreeNodeViewModel subtree, String name) {
        mSubtreeBackup = subtree.getNode().copySubtree();
        mGroupRoot = groupRoot.getNode();
        mSubtreeRootPath = subtree.getNode().getIndexedPathFromRoot();
        mName = name;
    }

    @Override
    public String getPresentationName() {
        return mName;
    }

    @Override
    public void undo() {
        super.undo();
        // remember modified children for redo
        mModifiedSubtree.clear();
        // get node to edit
        final GroupTreeNode subtreeRoot = mGroupRoot.getDescendant(mSubtreeRootPath).get(); // TODO: NULL
        mModifiedSubtree.addAll(subtreeRoot.getChildren());
        // keep subtree handle, but restore everything else from backup
        subtreeRoot.removeAllChildren();
        for (GroupTreeNode child : mSubtreeBackup.getChildren()) {
            child.copySubtree().moveTo(subtreeRoot);
        }
    }

    @Override
    public void redo() {
        super.redo();
        final GroupTreeNode subtreeRoot = mGroupRoot.getDescendant(mSubtreeRootPath).get(); // TODO: NULL
        subtreeRoot.removeAllChildren();
        for (GroupTreeNode modifiedNode : mModifiedSubtree) {
            modifiedNode.moveTo(subtreeRoot);
        }
    }
}
