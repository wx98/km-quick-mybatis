package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info;

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
     * 行号
     */
    private int lineNumber;
    /**
     * 标签类型（select/insert/update/delete/sql/resultMap）
     */
    private String tagName;
    /**
     * 标签对应的SQL ID（若为sql片段则是其id）
     */
    private String sqlId;
    /**
     * 用于精确定位标签的XPath表达式
     */
    private String xpath;

    private XmlElementInfo() {
    }


    public XmlElementInfo(@NotNull String filePath, int lineNumber, @NotNull String tagName, @NotNull String sqlId, @NotNull String xpath) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.tagName = tagName;
        this.sqlId = sqlId;
        this.xpath = xpath;
    }

    public String getXpath() {
        return xpath;
    }

    public void setXpath(@NotNull String xpath) {
        this.xpath = xpath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(@NotNull String filePath) {
        this.filePath = filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
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

    // 重写equals和hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XmlElementInfo that = (XmlElementInfo) o;
        // 行号需要一致
        boolean flag1 = lineNumber == that.lineNumber;
        // Java文件路径 需要一致
        boolean flag2 = filePath.equals(that.filePath);
        // 标签类型 需要一致
        boolean flag3 = tagName.equals(that.tagName);
        // SQL ID 需要一致
        boolean flag4 = sqlId.equals(that.sqlId);
        //
        boolean flag5 = java.util.Objects.equals(xpath, that.xpath);

        return flag1 && flag2 && flag3 && flag4 && flag5;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath, lineNumber, tagName, sqlId, xpath);
    }

    @Override
    public int compareTo(@NotNull XmlElementInfo o) {
        int filePathCompare = this.filePath.compareTo(o.filePath);
        if (filePathCompare != 0) {
            return filePathCompare;
        }
        if (this.xpath != null && o.xpath != null) {
            int xpathCompare = this.xpath.compareTo(o.xpath);
            if (xpathCompare != 0) {
                return xpathCompare;
            }
        }
        // filePath 相同则按行号排序
        return Integer.compare(this.lineNumber, o.lineNumber);
    }

    @Override
    public String toString() {
        return "XmlElementInfo{filePath='" + filePath + "', lineNumber=" + lineNumber + ", tagName='" + tagName + "', sqlId='" + sqlId + "', xpath='" + xpath + "'}";
    }
}