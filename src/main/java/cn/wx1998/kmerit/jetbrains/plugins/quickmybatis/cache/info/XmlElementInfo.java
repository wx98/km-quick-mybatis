package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info;

import org.jetbrains.annotations.NotNull;

/**
 * 存储与SQL ID关联的XML元素信息（select/insert/update/delete等标签）
 */
public class XmlElementInfo implements Comparable<XmlElementInfo> {
    /**
     * XML文件路径（唯一标识）
     */
    private String filePath;
    /**
     * 标签开始偏移量
     */
    private int startOffset;
    /**
     * 标签结束偏移量
     */
    private int endOffset;
    /**
     * 标签类型（select/insert/update/delete/sql/resultMap）
     */
    private String tagName;
    /**
     * 标签对应的SQL ID（若为sql片段则是其id）
     */
    private String sqlId;
    /**
     * 标签对应的SQL ID（若为sql片段则是其id）
     */
    private String databaseId;

    public XmlElementInfo() {
    }


    public XmlElementInfo(@NotNull String filePath, int startOffset, int endOffset, @NotNull String tagName, @NotNull String sqlId, @NotNull String databaseId) {
        this.filePath = filePath;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.tagName = tagName;
        this.sqlId = sqlId;
        this.databaseId = databaseId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(@NotNull String filePath) {
        this.filePath = filePath;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(@NotNull String tagName) {
        this.tagName = tagName;
    }

    public String getSqlId() {
        return sqlId;
    }

    public void setSqlId(@NotNull String sqlId) {
        this.sqlId = sqlId;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    // 重写equals和hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlElementInfo that = (XmlElementInfo) o;
        // 行号需要一致
        boolean flag1 = startOffset == that.startOffset;
        // 行号需要一致
        boolean flag2 = endOffset == that.endOffset;
        // Java文件路径 需要一致
        boolean flag3 = filePath.equals(that.filePath);
        // 标签类型 需要一致
        boolean flag4 = tagName.equals(that.tagName);
        // SQL ID 需要一致
        boolean flag5 = sqlId.equals(that.sqlId);

        return flag1 && flag2 && flag3 && flag4 && flag5;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath, startOffset, endOffset, tagName, sqlId);
    }

    @Override
    public int compareTo(@NotNull XmlElementInfo o) {
        int filePathCompare = this.filePath.compareTo(o.filePath);
        if (filePathCompare != 0) {
            return filePathCompare;
        }
        if (this.startOffset != o.startOffset) {
            return Integer.compare(this.startOffset, o.startOffset);
        } else {
            return Integer.compare(this.endOffset, o.endOffset);
        }
    }

    @Override
    public String toString() {
        return "XmlElementInfo{filePath='" + filePath + "', startOffset=" + startOffset + "', endOffset=" + endOffset + ", tagName='" + tagName + "', sqlId='" + sqlId + "'}";
    }
}