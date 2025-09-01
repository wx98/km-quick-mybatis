package cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * MyBatis XML 解析器工厂
 * 用于创建和管理解析器实例
 */
public final class MyBatisXmlParserFactory {

    // 私有构造函数防止实例化
    private MyBatisXmlParserFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * 创建默认的 MyBatis XML 解析器
     *
     * @param project 当前项目
     * @return 默认的 MyBatis XML 解析器
     */
    public static MyBatisXmlParser createDefaultParser(@NotNull Project project) {
        return DefaultMyBatisXmlParser.create(project);
    }


    /**
     * 获取当前环境推荐的 MyBatis XML 解析器
     *
     * @param project 当前项目
     * @return 推荐的 MyBatis XML 解析器
     */
    public static MyBatisXmlParser getRecommendedParser(@NotNull Project project) {
        return createDefaultParser(project);
    }
}