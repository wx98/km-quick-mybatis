package cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.util;

import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.MyBatisXmlParser;
import cn.wx1998.kmerit.jetbrains.plugins.quickmybatis.parser.MyBatisXmlParserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The type Dom utils.
 */
public final class DomUtils {

    // 日志前缀
    private static final String LOG_PREFIX = "[kmQuickMybatis Dom工具]";
    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(DomUtils.class);

    /**
     * Private constructor to prevent instantiation.
     * Throws UnsupportedOperationException to ensure this utility class cannot be instantiated.
     */
    private DomUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Check if a file is a MyBatis XML file.
     * Determines if the given PsiFile is a MyBatis Mapper XML file by checking file type, root tag name, etc.
     *
     * @param file The PsiFile object to check
     * @return True if it's a MyBatis XML file, false otherwise
     */
    public static boolean isMybatisFile(@Nullable PsiFile file) {
        LOG.debug(LOG_PREFIX + "Checking if file is a MyBatis XML file: " + (file != null ? file.getName() : "null"));

        if (!(file instanceof XmlFile)) {
            LOG.debug(LOG_PREFIX + "File is null or not an XmlFile");
            return false;
        }

        // Basic validation
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        if (rootTag == null || !"mapper".equals(rootTag.getName())) {
            LOG.debug(LOG_PREFIX + "File does not have mapper root tag: " + (rootTag != null ? rootTag.getName() : "null"));
            return false;
        }

        XmlAttribute namespaceAttr = rootTag.getAttribute("namespace");
        if (namespaceAttr == null || namespaceAttr.getValue() == null || namespaceAttr.getValue().trim().isEmpty()) {
            LOG.debug(LOG_PREFIX + "Mapper tag missing valid namespace attribute");
            return false;
        }

        // Use MyBatisXmlParser for enhanced validation
        try {
            Project project = file.getProject();
            MyBatisXmlParser parser = getMyBatisXmlParser(project);
            LOG.debug(LOG_PREFIX + "Using MyBatisXmlParser for enhanced validation");
            boolean isValid = parser.isValidMyBatisFile((XmlFile) file);
            LOG.debug(LOG_PREFIX + "Enhanced validation result: " + isValid);
            return isValid;
        } catch (Exception e) {
            // If parser validation fails, fallback to basic judgment result
            LOG.warn(LOG_PREFIX + "Enhanced validation failed, falling back to basic validation: " + e.getMessage());
            return true;
        }
    }

    /**
     * Get MyBatis XML parser
     *
     * @param project Current project
     * @return MyBatisXmlParser instance
     */
    public static MyBatisXmlParser getMyBatisXmlParser(@NotNull Project project) {
        return MyBatisXmlParserFactory.getRecommendedParser(project);
    }

    /**
     * Check if a file is a MyBatis configuration file.
     * Determines if the given PsiFile is a MyBatis configuration file by checking file type and root tag name.
     *
     * @param file The PsiFile object to check
     * @return True if it's a MyBatis configuration file, false otherwise
     */
    public static boolean isMybatisConfigurationFile(@NotNull PsiFile file) {
        if (!isXmlFile(file)) {
            // If file is not XML file, return false
            return false;
        }
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        // If root tag name is "configuration", return true
        return null != rootTag && "configuration".equals(rootTag.getName());
    }

    /**
     * Check if a file is a Spring Beans file.
     * Determines if the given PsiFile is a Spring Beans file by checking file type and root tag name.
     *
     * @param file The PsiFile object to check
     * @return True if it's a Spring Beans file, false otherwise
     */
    public static boolean isBeansFile(@NotNull PsiFile file) {
        if (!isXmlFile(file)) {
            // If file is not XML file, return false
            return false;
        }
        XmlTag rootTag = ((XmlFile) file).getRootTag();
        // If root tag name is "beans", return true
        return null != rootTag && "beans".equals(rootTag.getName());
    }

    /**
     * Check if a file is an XML file.
     * Determines if the given PsiFile is an XML file by checking its type.
     *
     * @param file The PsiFile object to check
     * @return True if it's an XML file, false otherwise
     */
    static boolean isXmlFile(@NotNull PsiFile file) {
        // If file is of type XmlFile, return true
        return file instanceof XmlFile;
    }

}
