package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom.Mapper;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * The type Java service.
 *
 * @author yanglin
 */
public class JavaService implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Project project;

    private final JavaPsiFacade javaPsiFacade;

    private final EditorService editorService;


    public JavaPsiFacade getJavaPsiFacade() {
        return javaPsiFacade;
    }

    public EditorService getEditorService() {
        return editorService;
    }

    public Project getProject() {
        return project;
    }

    /**
     * Instantiates a new Java service.
     *
     * @param project the project
     */
    public JavaService(Project project) {
        this.project = project;
        this.javaPsiFacade = JavaPsiFacade.getInstance(project);
        this.editorService = EditorService.getInstance(project);
    }

    /**
     * Gets instance.
     *
     * @param project the project
     * @return the instance
     */
    public static JavaService getInstance(@NotNull Project project) {
        return project.getService(JavaService.class);
    }

    /**
     * Gets reference clazz of psi field.
     *
     * @param field the field
     * @return the reference clazz of psi field
     */
    public Optional<PsiClass> getReferenceClazzOfPsiField(@NotNull PsiElement field) {
        if (!(field instanceof PsiField)) {
            return Optional.empty();
        }
        PsiType type = ((PsiField) field).getType();
        return type instanceof PsiClassReferenceType ? Optional.ofNullable(((PsiClassReferenceType) type).resolve()) : Optional.empty();
    }

    public static String parseExpression(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) {
            // 直接返回字面量值
            return String.valueOf(((PsiLiteralExpression) expression).getValue());
        } else if (expression instanceof PsiBinaryExpression) {
            // 处理二元表达式（如字符串拼接）
            Deque<String> parts = new ArrayDeque<>();
            flattenBinaryExpression((PsiBinaryExpression) expression, parts);
            return String.join("", parts);
        } else if (expression instanceof PsiMethodCallExpression) {
            // 处理方法调用表达式
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if ("getName".equals(methodName)) {
                // 模拟 getName() 方法返回类名
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (qualifier instanceof PsiClassObjectAccessExpression) {
                    PsiType type = ((PsiClassObjectAccessExpression) qualifier).getOperand().getType();
                    if (type instanceof PsiClassReferenceType) {
                        PsiClass psiClass = ((PsiClassReferenceType) type).resolve();
                        if (psiClass != null) {
                            return psiClass.getQualifiedName();
                        }
                    }
                }
            }
            return "";
        } else if (expression instanceof PsiReferenceExpression) {
            // 处理变量引用
            PsiElement resolved = ((PsiReferenceExpression) expression).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiExpression initializer = field.getInitializer();
                if (initializer != null) {
                    return parseExpression(initializer);
                }
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable localVar = (PsiLocalVariable) resolved;
                PsiExpression initializer = localVar.getInitializer();
                if (initializer != null) {
                    return parseExpression(initializer);
                }
                // 处理直接使用变量名作为字符串值的场景
                if (localVar.getType() instanceof PsiClassReferenceType && String.class.getName().equals(((PsiClassReferenceType) localVar.getType()).getCanonicalText())) {
                    return localVar.getName();
                }
            }
        } else if (expression instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
            StringBuilder result = new StringBuilder();
            for (PsiExpression operand : polyadicExpression.getOperands()) {
                result.append(parseExpression(operand));
            }
            return result.toString();
        }
        return "";
    }

    private static void flattenBinaryExpression(PsiBinaryExpression exp, Deque<String> parts) {
        PsiExpression lOperand = exp.getLOperand();
        PsiExpression rOperand = exp.getROperand();

        if (lOperand instanceof PsiBinaryExpression) {
            flattenBinaryExpression((PsiBinaryExpression) lOperand, parts);
        } else {
            parts.addFirst(parseExpression(lOperand));
        }

        if (rOperand instanceof PsiBinaryExpression) {
            flattenBinaryExpression((PsiBinaryExpression) rOperand, parts);
        } else {
            parts.addLast(parseExpression(rOperand));
        }
    }

    private boolean isType(PsiField type, Class<String> targetClass) {
        if (!(type.getType() instanceof PsiClassReferenceType)) {
            return false;
        }
        // 获取泛型擦除后的原始类型
        PsiClass psiClass = ((PsiClassReferenceType) type.getType()).rawType().resolve();
        return psiClass != null && targetClass.getName().equals(psiClass.getQualifiedName());
    }

    /**
     * 判断方法是否是 SqlSession 的方法
     *
     * @param method
     * @return
     */
    private boolean isSqlSessionMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        if (!method.getModifierList().hasExplicitModifier(PsiModifier.PUBLIC)) {
            return false;
        }
        final var classFilters = MyPluginSettings.getInstance().getClassFilters();
        if (classFilters != null) {
            final var qualifiedName = containingClass.getQualifiedName();
            for (ClassFilter classFilter : classFilters) {
                final var pattern = classFilter.getPattern();
                if (StringUtil.equals(pattern, qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }


    public void processField(@NotNull PsiField field, @NotNull Processor<Mapper> processor) {
        if (!isType(field, String.class)) {
            return;
        }
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
            String fieldValue = parseExpression(initializer);
            // 定义全局搜索范围
            GlobalSearchScope scope = GlobalSearchScope.allScope(field.getProject());
            // 获取指定类类型的 DOM 文件元素
            List<DomFileElement<Mapper>> elements = DomService.getInstance().getFileElements(Mapper.class, field.getProject(), scope);
            // 将文件元素转换为根元素并收集到列表中
            final var collect = elements.stream().map(DomFileElement::getRootElement).toList();

            for (Mapper mapper : collect) {
                final var namespace = mapper.getNamespace().getRawText();
                if (fieldValue.equals(namespace) || fieldValue.equals(namespace + "Mapper")) {
                    processor.process(mapper);
                }

            }
        }
    }

    public void processMethodCall(@NotNull PsiMethodCallExpression methodCall, @NotNull Processor<DomElement> processor) {
        // 检查是否属于org.apache.ibatis.session包的方法
        PsiMethod method = methodCall.resolveMethod();
        final var sqlSessionMethod = isSqlSessionMethod(method);
        System.out.println(sqlSessionMethod);

    }


}
