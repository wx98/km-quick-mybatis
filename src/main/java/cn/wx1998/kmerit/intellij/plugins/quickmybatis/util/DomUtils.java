package cn.wx1998.kmerit.intellij.plugins.quickmybatis.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type Dom utils.
 */
public final class DomUtils {

    /**
     * 私有构造函数，防止实例化。
     * 通过抛出 UnsupportedOperationException 异常，确保该工具类不能被实例化。
     */
    private DomUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * 查找 DOM 元素集合。
     * 根据指定的项目和类类型，查找所有符合条件的 DOM 元素。
     *
     * @param <T>     泛型参数，表示 DOM 元素的类型
     * @param project 当前项目
     * @param clazz   要查找的 DOM 元素类类型
     * @return 符合条件的 DOM 元素集合
     */
    @NotNull
    @NonNls
    public static <T extends DomElement> Collection<T> findDomElements(@NotNull Project project, Class<T> clazz) {
        // 定义全局搜索范围
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        // 获取指定类类型的 DOM 文件元素
        List<DomFileElement<T>> elements = DomService.getInstance().getFileElements(clazz, project, scope);
        // 将文件元素转换为根元素并收集到列表中
        return elements.stream().map(DomFileElement::getRootElement).collect(Collectors.toList());
    }

    /**
     * 判断是否为 MyBatis XML 文件。
     * 通过检查文件类型、根标签名称等条件，判断给定的 PsiFile 是否为 MyBatis 的 Mapper XML 文件。
     *
     * @param file 要判断的 PsiFile 对象
     * @return 如果是 MyBatis XML 文件，则返回 true；否则返回 false
     */
    public static boolean isMybatisFile(@Nullable PsiFile file) {
        Boolean mybatisFile = null;
        if (file == null) {
            // 如果文件为空，则直接返回 false
            mybatisFile = false;
        }
        if (mybatisFile == null) {
            if (!isXmlFile(file)) {
                // 如果文件不是 XML 文件，则返回 false
                mybatisFile = false;
            }
        }
        if (mybatisFile == null) {
            XmlTag rootTag = ((XmlFile) file).getRootTag();
            if (rootTag == null) {
                // 如果根标签为空，则返回 false
                mybatisFile = false;
            }
            if (mybatisFile == null) {
                if (!"mapper".equals(rootTag.getName())) {
                    // 如果根标签名称不是 "mapper"，则返回 false
                    mybatisFile = false;
                }
            }
        }
        if (mybatisFile == null) {
            // 如果以上条件均未满足，则认为是 MyBatis XML 文件
            mybatisFile = true;
        }
        return mybatisFile;
    }

    /**
     * 判断是否为 MyBatis 配置文件。
     * 通过检查文件类型和根标签名称，判断给定的 PsiFile 是否为 MyBatis 的配置文件。
     *
     * @param file 要判断的 PsiFile 对象
     * @return 如果是 MyBatis 配置文件，则返回 true；否则返回 false
     */
    public static boolean isMybatisConfigurationFile(@NotNull PsiFile file) {
        if (!isXmlFile(file)) {
            // 如果文件不是 XML 文件，则返回 false
            return false;
        }
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        // 如果根标签名称是 "configuration"，则返回 true
        return null != rootTag && "configuration".equals(rootTag.getName());
    }

    /**
     * 判断是否为 Spring Beans 文件。
     * 通过检查文件类型和根标签名称，判断给定的 PsiFile 是否为 Spring 的 Beans 文件。
     *
     * @param file 要判断的 PsiFile 对象
     * @return 如果是 Spring Beans 文件，则返回 true；否则返回 false
     */
    public static boolean isBeansFile(@NotNull PsiFile file) {
        if (!isXmlFile(file)) {
            // 如果文件不是 XML 文件，则返回 false
            return false;
        }
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        // 如果根标签名称是 "beans"，则返回 true
        return null != rootTag && "beans".equals(rootTag.getName());
    }

    /**
     * 判断是否为 XML 文件。
     * 通过检查文件类型，判断给定的 PsiFile 是否为 XML 文件。
     *
     * @param file 要判断的 PsiFile 对象
     * @return 如果是 XML 文件，则返回 true；否则返回 false
     */
    static boolean isXmlFile(@NotNull PsiFile file) {
        // 如果文件是 XmlFile 类型，则返回 true
        return file instanceof XmlFile;
    }

}
