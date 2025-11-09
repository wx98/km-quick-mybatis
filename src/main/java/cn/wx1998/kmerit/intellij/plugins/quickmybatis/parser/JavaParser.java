package cn.wx1998.kmerit.intellij.plugins.quickmybatis.parser;

import com.intellij.psi.*;

import java.util.List;
import java.util.Map;

/**
 * Java文件解析器接口
 */
public interface JavaParser {

    /**
     * 解析Java文件
     *
     * @param file 要解析的Java文件
     * @return 解析结果，包含了Java文件中的所有相关元素
     */
    JavaParseResult parse(PsiJavaFile file);

    /**
     * 判断是否为有效的Java文件
     *
     * @param file 要检查的Java文件
     * @return 如果是有效的Java文件，返回true；否则返回false
     */
    boolean isValidJavaFile(PsiJavaFile file);

    /**
     * 解析结果接口
     * 包含了解析Java文件后得到的所有元素
     */
    interface JavaParseResult {

        String getQualifiedName();

        Map<String, PsiClass> getClasses();

        Map<String, PsiClass> getInterfaces();

        Map<String, List<PsiMethod>> getAllInterfaceMethods();

        List<PsiMethod> getInterfaceMethodsByName(String interfaceMethodName);

        Map<String, List<PsiMethod>> getAllClassMethods();

        List<PsiMethod> getClassMethodsByName(String classMethodName);

        Map<String, List<PsiMethodCallExpression>> getClassMethodCall();

        Map<String, List<PsiField>> getStaticStringField();

        List<PsiField> getStaticStringFieldByName(String methodName);
    }
}