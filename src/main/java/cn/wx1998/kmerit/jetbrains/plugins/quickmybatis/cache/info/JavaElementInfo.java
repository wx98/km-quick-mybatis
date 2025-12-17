package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.cache.info;

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
     * 标签开始偏移量
     */
    private int startOffset;
    /**
     * 标签结束偏移量
     */
    private int endOffset;
    /**
     * 元素类型（class/method/field/methodCall）
     */
    private String elementType;
    /**
     * 元素对应的SQL ID
     */
    private String sqlId;

    public JavaElementInfo() {
    }

    public JavaElementInfo(@NotNull String filePath, int startOffset, int endOffset, @NotNull String elementType, @NotNull String sqlId) {
        this.filePath = filePath;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.elementType = elementType;
        this.sqlId = sqlId;
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
        boolean flag1 = startOffset == that.startOffset;
        // 行号需要一致
        boolean flag2 = endOffset == that.endOffset;
        // Java文件路径 需要一致
        boolean flag3 = filePath.equals(that.filePath);
        // 元素类型 需要一致
        boolean flag4 = elementType.equals(that.elementType);
        // SQL ID 需要一致
        boolean flag5 = sqlId.equals(that.sqlId);
        return flag1 && flag2 && flag3 && flag4 && flag5;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(filePath, startOffset, endOffset, elementType, sqlId);

    }

    @Override
    public int compareTo(@NotNull JavaElementInfo o) {
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
        return "JavaElementInfo{filePath='" + filePath + "', startOffset=" + startOffset + "', endOffset=" + endOffset + ", tagName='" + elementType + "', sqlId='" + sqlId + "'}";
    }
}