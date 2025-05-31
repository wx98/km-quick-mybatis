package cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom;

import com.intellij.util.xml.*;

/**
 * ID DOM元素接口。
 * 该接口定义了与ID相关的DOM元素的行为，主要用于MyBatis的XML配置文件中。
 * 
 * @author yanglin
 */
public interface IdDomElement extends DomElement {

    /**
     * 获取ID属性值。
     * 该方法返回一个通用属性值对象，表示当前元素的ID属性。
     * 
     * @return 表示ID属性的通用属性值对象
     */
    @Required
    @NameValue
    @Attribute("id")
    GenericAttributeValue<Object> getId();

    /**
     * 设置值。
     * 该方法用于为当前元素设置值。
     * 
     * @param content 要设置的内容
     */
    void setValue(String content);
}
