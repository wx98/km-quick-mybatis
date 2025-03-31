package cn.wx1998.kmerit.intellij.plugins.quickmybatis.services;

import cn.wx1998.kmerit.intellij.plugins.quickmybatis.dom.Mapper;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.setting.MyPluginSettings;
import cn.wx1998.kmerit.intellij.plugins.quickmybatis.util.DomUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.xml.*;
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

    // 获取日志记录器实例
    private static final Logger LOG = Logger.getInstance(JavaService.class);

    @Serial
    private static final long serialVersionUID = 1L;

    private final Project project;

    private final JavaPsiFacade javaPsiFacade;

    public JavaPsiFacade getJavaPsiFacade() {
        return javaPsiFacade;
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
     * 处理类跳转逻辑
     *
     * @param psiClass
     * @param processor
     */
    public void processClass(PsiClass psiClass, CommonProcessors.CollectProcessor<DomElement> processor) {
        LOG.debug("1:\t" + psiClass.getText().replaceAll("\n", ""));
        String ns = psiClass.getQualifiedName();
        for (Mapper mapper : DomUtils.findDomElements(project, Mapper.class)) {
            final String namespace = mapper.getNamespace().getStringValue();
            if (namespace != null && (namespace.equals(ns) || namespace.equals(ns + "Mapper"))) {
                processor.process(mapper);
            }
        }
    }

    /**
     * 处理方法跳转逻辑
     *
     * @param psiMethod
     * @param processor
     */
    public void processMethod(PsiMethod psiMethod, CommonProcessors.CollectProcessor<DomElement> processor) {
        LOG.debug("2:\t" + psiMethod.getText().replaceAll("\n", ""));

        PsiClass psiClass = psiMethod.getContainingClass();
        if (null == psiClass) {
            return;
        }
        Collection<Mapper> mappers = DomUtils.findDomElements(project, Mapper.class);
        Set<String> ids = new HashSet<>();
        String id = psiClass.getQualifiedName() + "." + psiMethod.getName();
        ids.add(id);
        final Query<PsiClass> search = ClassInheritorsSearch.search(psiClass);
        final Collection<PsiClass> allChildren = search.findAll();

        for (PsiClass psiElement : allChildren) {
            String childId = psiElement.getQualifiedName() + "." + psiMethod.getName();
            ids.add(childId);
        }
        mappers.stream()
                .flatMap(mapper -> {
                    return mapper.getDaoElements().stream();
                }).
                filter(idDom -> {
                    Optional<Mapper> optional = Optional.ofNullable(DomUtil.getParentOfType(idDom, Mapper.class, true));
                    String namespace = "";
                    if (optional.isPresent()) {
                        namespace = optional.get().getNamespace().getStringValue();
                    }
                    String idSignature = (namespace +"." + idDom.getId());
                    return ids.contains(idSignature);
                })
                .forEach(processor::process);
    }

    /**
     * 处理字段跳转逻辑
     *
     * @param field
     * @param processor
     */
    public void processField(@NotNull PsiField field, @NotNull Processor<Mapper> processor) {
        LOG.debug("3:\t" + field.getText().replaceAll("\n", ""));
        if (!isType(field, String.class)) {
            return;
        }
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
            String fieldValue = parseExpression(initializer);
            for (Mapper mapper : DomUtils.findDomElements(field.getProject(), Mapper.class)) {
                final var stringValue = mapper.getNamespace().getStringValue();
                if (stringValue != null && (stringValue.equals(fieldValue) || stringValue.equals(fieldValue + "Mapper"))) {
                    processor.process(mapper);
                }
            }
        }

    }

    /**
     * 处理方法调用跳转逻辑
     *
     * @param methodCall
     * @param processor
     */
    public void processMethodCall(@NotNull PsiMethodCallExpression methodCall, @NotNull Processor<DomElement> processor) {
        LOG.debug("4:\t" + methodCall.getText().replaceAll("\n", ""));
        PsiMethod method = methodCall.resolveMethod();
        if (method == null || !isSqlSessionMethod(method)) {
            return;
        }
        PsiClass psiClass = method.getContainingClass();
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length == 0) {
            return;
        } else {
            // 解析第一个参数的实际值
            String mappedStatementId = parseExpression(args[0]);
            Set<String> ids = new HashSet<>();
            ids.add(mappedStatementId);
            Collection<Mapper> mappers = DomUtils.findDomElements(methodCall.getProject(), Mapper.class);
            mappers.stream()
                    .flatMap(mapper -> mapper.getDaoElements().stream()).
                    filter(
                            idDom -> {
                                Optional<Mapper> optional = Optional.ofNullable(DomUtil.getParentOfType(idDom, Mapper.class, true));
                                String namespace = "";
                                if (optional.isPresent()) {
                                    namespace = optional.get().getNamespace().getStringValue();
                                }
                                String idSignature = (namespace +"." + idDom.getId());
                                return ids.contains(idSignature);
                            }
                    )
                    .forEach(processor::process);
        }

    }

    /**
     * 计算表达式的值
     *
     * @param expression
     * @return
     */
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

    /**
     * 处理二元表达式
     *
     * @param exp
     * @param parts
     */
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

    /**
     * 判断字段是否是指定类型的字段l
     *
     * @param type
     * @param targetClass
     * @return
     */
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
}
