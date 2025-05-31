package cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * The interface Select.
 *
 * @author yanglin
 */
public interface Select extends IdDomElement {

    /**
     * select 标签对应的 resultType
     *
     * @return the result type
     */
    @NotNull
    @Attribute("resultType")
    GenericAttributeValue<PsiClass> getResultType();

}
