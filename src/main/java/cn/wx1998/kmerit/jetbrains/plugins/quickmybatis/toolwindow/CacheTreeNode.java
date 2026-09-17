package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.toolwindow;

import javax.swing.tree.DefaultMutableTreeNode;

public class CacheTreeNode extends DefaultMutableTreeNode {
    private final String key;
    private final String filePath;
    private final int offset;

    public CacheTreeNode(String key, String text) {
        this(key, text, null, -1);
    }

    public CacheTreeNode(String key, String text, String filePath, int offset) {
        super(text);
        this.key = key;
        this.filePath = filePath;
        this.offset = offset;
    }

    public String getKey() {
        return key;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getOffset() {
        return offset;
    }

    public boolean canNavigate() {
        return filePath != null && !filePath.isEmpty();
    }
}
