package cn.wx1998.kmerit.intellij.plugins.quickmybatis;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.PsiErrorElementUtil;
import org.junit.Test;

/**
 * MyPluginTest 类继承自 BasePlatformTestCase，用于测试 MyPlugin 插件的功能。
 */
@TestDataPath("$CONTENT_ROOT/src/test/testData")
public class MyPluginTest extends BasePlatformTestCase {

    /**
     * 测试 XML 文件的解析功能。
     */
    @Test
    public void testXMLFile() {
        // 配置一个 XML 文件内容
        XmlFile xmlFile = (XmlFile) myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>");

        // 确保没有解析错误
        assertFalse(PsiErrorElementUtil.hasErrors(getProject(), xmlFile.getVirtualFile()));

        // 确保根标签存在
        assertNotNull(xmlFile.getRootTag());

        // 检查根标签的名称和值
        if (xmlFile.getRootTag() != null) {
            assertEquals("foo", xmlFile.getRootTag().getName());
            assertEquals("bar", xmlFile.getRootTag().getValue().getText());
        }
    }

    /**
     * 测试重命名功能。
     */
    @Test
    public void testRename() {
        // 执行重命名操作并验证结果
        myFixture.testRename("foo.xml", "foo_after.xml", "a2");
    }

    /**
     * 获取测试数据路径。
     *
     * @return 测试数据路径字符串。
     */
    @Override
    protected String getTestDataPath() {
        return "src/test/testData/rename";
    }
}