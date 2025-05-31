package cn.wx1998.kmerit.intellij.plugins.quickmybatis.provider;

import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * GotoMapperXmlSchemaTypeRendererProvider 类。
 * 该类实现了 GotoTargetRendererProvider 接口，用于为 MyBatis 的 Mapper XML 文件中的元素提供导航目标渲染器。
 * 它的主要功能是根据 XML 元素的上下文信息生成适合的渲染器，以便在 IDE 中提供更好的导航体验。
 */
public class GotoMapperXmlSchemaTypeRendererProvider implements GotoTargetRendererProvider {

    @Override
    public PsiElementListCellRenderer getRenderer(@NotNull PsiElement element, @NotNull GotoTargetHandler.GotoData gotoData) {
        // 检查传入的 PsiElement 是否为 XmlTagImpl 类型，并判断是否位于 MyBatis 文件中
        if (element instanceof XmlTagImpl) {
            return new MyRenderer();
        }
        return null;
    }

    /**
     * MyRenderer 静态内部类。
     * 该类继承自 PsiElementListCellRenderer，用于为 MyBatis 的 Mapper XML 文件中的标签提供自定义的渲染逻辑。
     */
    public static class MyRenderer extends PsiElementListCellRenderer<XmlTagImpl> {

        @Override
        public String getElementText(XmlTagImpl element) {
            // 获取 XML 标签的 "id" 属性值，如果不存在则返回标签名称
            XmlAttribute attr = element.getAttribute("id", XmlUtil.XML_SCHEMA_URI);
            attr = attr == null ? element.getAttribute("id") : attr;
            return (attr == null || attr.getValue() == null ? element.getName() : attr.getValue());
        }

        @Override
        protected String getContainerText(XmlTagImpl element, String name) {
            // 获取包含当前 XML 标签的文件名，并附加数据库 ID（如果存在）
            final PsiFile file = element.getContainingFile();
            String databaseId = getDatabaseId(element);
            return databaseId + file.getVirtualFile().getName();
        }

        @NotNull
        private String getDatabaseId(XmlTagImpl element) {
            // 获取 XML 标签的 "databaseId" 属性值，如果不存在则返回空字符串
            final XmlAttribute databaseIdAttr = element.getAttribute("databaseId");
            String databaseId = null;
            if (databaseIdAttr != null) {
                databaseId = databaseIdAttr.getValue() + ",";
            }
            if (databaseId == null) {
                databaseId = "";
            }
            return databaseId;
        }

        @Override
        protected int getIconFlags() {
            // 返回图标的标志位，当前未使用任何标志位
            return 0;
        }
    }
}
