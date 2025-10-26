package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info;

import org.jetbrains.annotations.NotNull;

/**
 * 存储与SQL ID关联的XML元素信息（select/insert/update/delete等标签）
 */
public class XmlElementInfo implements Comparable<XmlElementInfo> {
    // XML文件路径（唯一标识）
    private String filePath;
    // 标签所在行号（用于跳转）
    private int lineNumber;
    // 标签类型（select/insert/update/delete/sql/resultMap）
    private String tagName;
    // 标签对应的SQL ID（若为sql片段则是其id）
    private String sqlId;


    public XmlElementInfo() {
    }


    public XmlElementInfo(@NotNull String filePath, int lineNumber, @NotNull String tagName, @NotNull String sqlId) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.tagName = tagName;
        this.sqlId = sqlId;
    }

    // Getter
    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getTagName() {
        return tagName;
    }

    public String getSqlId() {
        return sqlId;
    }

    public void setFilePath(@NotNull String filePath) {
        this.filePath = filePath;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void setTagName(@NotNull String tagName) {
        this.tagName = tagName;
    }

    public void setSqlId(@NotNull String sqlId) {
        this.sqlId = sqlId;
    }

    // 重写equals和hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlElementInfo that = (XmlElementInfo) o;
        return lineNumber == that.lineNumber &&
                filePath.equals(that.filePath) &&
                tagName.equals(that.tagName) &&
                sqlId.equals(that.sqlId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath, lineNumber, tagName, sqlId);
    }

    @Override
    public int compareTo(@NotNull XmlElementInfo o) {
        int filePathCompare = this.filePath.compareTo(o.filePath);
        if (filePathCompare != 0) {
            return filePathCompare;
        }
        // filePath 相同则按行号排序
        return Integer.compare(this.lineNumber, o.lineNumber);
    }
}