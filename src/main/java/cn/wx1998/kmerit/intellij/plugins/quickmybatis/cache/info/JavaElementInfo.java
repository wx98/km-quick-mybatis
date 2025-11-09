package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info;

import org.jetbrains.annotations.NotNull;

/**
 * 存储与SQL ID关联的Java元素信息（类、方法、字段、方法调用）
 */
public class JavaElementInfo implements Comparable<JavaElementInfo> {
    /**
     * Java文件路径（唯一标识）
     */
    private String filePath;
    /**
     * 元素所在行号（用于跳转）
     */
    private int lineNumber;
    /**
     * 元素类型（class/method/field/methodCall）
     */
    private String elementType;
    /**
     * 元素对应的SQL ID
     */
    private String sqlId;
    /**
     * 用于精确定位标签的XPath表达式
     */
    private String xpath;

    private JavaElementInfo() {
    }

    public JavaElementInfo(@NotNull String filePath, int lineNumber, @NotNull String elementType, @NotNull String sqlId, @NotNull String xpath) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.elementType = elementType;
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

    public String getElementType() {
        return elementType;
    }

    public void setElementType(@NotNull String elementType) {
        this.elementType = elementType;
    }

    public String getSqlId() {
        return sqlId;
    }

    public void setSqlId(@NotNull String sqlId) {
        this.sqlId = sqlId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaElementInfo that = (JavaElementInfo) o;
        // 行号需要一致
        boolean flag1 = lineNumber == that.lineNumber;
        // Java文件路径 需要一致
        boolean flag2 = filePath.equals(that.filePath);
        // 元素类型 需要一致
        boolean flag3 = elementType.equals(that.elementType);
        // SQL ID 需要一致
        boolean flag4 = sqlId.equals(that.sqlId);
        boolean flag5 = java.util.Objects.equals(xpath, that.xpath);
        return flag1 && flag2 && flag3 && flag4 && flag5;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath, lineNumber, elementType, sqlId, xpath);
    }

    @Override
    public int compareTo(@NotNull JavaElementInfo o) {
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
        return Integer.compare(this.lineNumber, o.lineNumber);
    }

}