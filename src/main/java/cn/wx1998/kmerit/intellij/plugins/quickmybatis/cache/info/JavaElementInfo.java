package cn.wx1998.kmerit.intellij.plugins.quickmybatis.cache.info;

import org.jetbrains.annotations.NotNull;

/**
 * 存储与SQL ID关联的Java元素信息（类、方法、字段、方法调用）
 */
public class JavaElementInfo implements Comparable<JavaElementInfo> {
    // Java文件路径（唯一标识）
    private String filePath;
    // 元素所在行号（用于跳转）
    private int lineNumber;
    // 元素类型（class/method/field/methodCall）
    private String elementType;
    // 元素对应的SQL ID
    private String sqlId;

    private JavaElementInfo() {
    }

    public JavaElementInfo(@NotNull String filePath, int lineNumber, @NotNull String elementType, @NotNull String sqlId) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.elementType = elementType;
        this.sqlId = sqlId;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getElementType() {
        return elementType;
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

    public void setElementType(@NotNull String elementType) {
        this.elementType = elementType;
    }

    public void setSqlId(@NotNull String sqlId) {
        this.sqlId = sqlId;
    }

    // 重写equals和hashCode，确保集合操作正确性
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaElementInfo that = (JavaElementInfo) o;
        return lineNumber == that.lineNumber &&
                filePath.equals(that.filePath) &&
                elementType.equals(that.elementType) &&
                sqlId.equals(that.sqlId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath, lineNumber, elementType, sqlId);
    }

    // 实现 compareTo 方法（按 filePath + lineNumber 排序，确保唯一）
    @Override
    public int compareTo(@NotNull JavaElementInfo o) {
        int filePathCompare = this.filePath.compareTo(o.filePath);
        if (filePathCompare != 0) {
            return filePathCompare;
        }
        //  filePath 相同则按行号排序
        return Integer.compare(this.lineNumber, o.lineNumber);
    }
}