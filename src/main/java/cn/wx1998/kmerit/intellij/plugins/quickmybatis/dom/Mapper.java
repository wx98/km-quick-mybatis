package cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

@Namespace("MybatisXml")
public interface Mapper extends DomElement {

    @Required
    @NameValue
    @NotNull
    @Attribute("namespace")
    GenericAttributeValue<PsiClass> getNamespace();
}
