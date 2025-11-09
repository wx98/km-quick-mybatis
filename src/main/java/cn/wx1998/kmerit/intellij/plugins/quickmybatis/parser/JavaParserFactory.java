package cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Java文件解析器：提取类名、方法名及方法第一个参数
 */
public class JavaParserFactory {

    // 私有构造函数防止实例化
    private JavaParserFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * 创建默认的 Java 文件 解析器
     *
     * @param project 当前项目
     * @return 默认的 Java 文件 解析器
     */
    public static JavaParser createDefaultParser(@NotNull Project project) {
        return JavaParserDefault.create(project);
    }


    /**
     * 获取当前环境推荐的 Java 文件 解析器
     *
     * @param project 当前项目
     * @return 推荐的 Java 文件 解析器
     */
    public static JavaParser getRecommendedParser(@NotNull Project project) {
        return createDefaultParser(project);
    }
}