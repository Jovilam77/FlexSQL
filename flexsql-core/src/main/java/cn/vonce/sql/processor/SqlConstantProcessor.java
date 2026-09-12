package cn.vonce.sql.processor;

import cn.vonce.sql.annotation.SqlColumn;
import cn.vonce.sql.annotation.SqlTable;
import cn.vonce.sql.uitls.StringUtil;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.util.*;

/**
 * 生成表字段常量处理器
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/2/26 14:21
 */
public abstract class SqlConstantProcessor extends AbstractProcessor {
    private Messager messager; //用于输出信息
    private Filer filer; //用于生成文件
    public static final String PREFIX = "$";
    private static final String OBJECT_CLASS_NAME = "java.lang.Object";

    // 初始化操作
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        // 避免在最后一轮处理中执行操作
        if (env.processingOver()) {
            return true;
        }

        for (TypeElement typeElement : annotations) {
            // 一次性获取所有带注解的元素
            Set<? extends Element> annotatedElements = env.getElementsAnnotatedWith(typeElement);
            for (Element element : annotatedElements) {
                // 只处理类元素
                if (element.getKind() != ElementKind.CLASS) {
                    continue;
                }
                // 单个实体失败只影响自身，不应中断其余实体的处理
                try {
                    TypeElement typeEl = (TypeElement) element;
                    SqlTable sqlTable = typeEl.getAnnotation(SqlTable.class);

                    // 如果不需要生成常量，直接跳过
                    if (sqlTable != null && !sqlTable.constant()) {
                        continue;
                    }

                    // 获取包名（嵌套类会向上查找所在包）
                    PackageElement packageElement = getPackageElement(element);
                    if (packageElement == null) {
                        messager.printMessage(Diagnostic.Kind.WARNING,
                                "无法为没有包声明的类生成字段常量: " + element.getSimpleName(), element);
                        continue;
                    }
                    String packageName = packageElement.getQualifiedName().toString();
                    packageName = StringUtil.isEmpty(packageName) ? "sql" : packageName + ".sql";

                    // 获取类名（常量类始终生成在 <包>.sql 下的顶层类，嵌套类按层级拍平）
                    String className = getConstantClassName(typeEl);

                    // 收集所有字段（包括父类）
                    List<Element> fieldElements = collectFields(typeEl);

                    // 生成代码
                    generateJavaFile(packageName, className, element, fieldElements, sqlTable);
                } catch (Exception e) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "生成字段常量失败[" + element + "]: " + e, element);
                }
            }
        }
        return true;
    }

    /**
     * 获取元素所在的包，嵌套类会逐层向上查找；无包声明（如局部类）时返回 null
     */
    protected PackageElement getPackageElement(Element element) {
        Element enclosing = element;
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        return (PackageElement) enclosing;
    }

    /**
     * 获取元素所在的最外层类型，顶层类返回其自身
     */
    protected TypeElement getTopLevelType(TypeElement typeElement) {
        TypeElement topLevelType = typeElement;
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            if (enclosing instanceof TypeElement) {
                topLevelType = (TypeElement) enclosing;
            }
            enclosing = enclosing.getEnclosingElement();
        }
        return topLevelType;
    }

    /**
     * 获取常量类的类名：
     * <p>顶层类 → {@code User$}；嵌套类 → {@code Outer_Inner$}。
     * 常量类固定生成在 {@code <实体类所在包>.sql} 包下且必须是顶层类，
     * 因此嵌套类需要把嵌套层级用下划线拍平，避免与顶层类同名冲突。</p>
     */
    protected String getConstantClassName(TypeElement typeElement) {
        return String.join("_", getSimpleNamePath(typeElement)) + PREFIX;
    }

    /**
     * 获取类型自外向内各层的简单类名路径，如 {@code Outer.Inner → [Outer, Inner]}；顶层类返回单元素列表
     */
    protected List<String> getSimpleNamePath(TypeElement typeElement) {
        List<String> simpleNames = new ArrayList<>();
        TypeElement current = typeElement;
        while (current != null) {
            simpleNames.add(current.getSimpleName().toString());
            Element enclosing = current.getEnclosingElement();
            if (!(enclosing instanceof TypeElement)) {
                break;
            }
            current = (TypeElement) enclosing;
        }
        Collections.reverse(simpleNames);
        return simpleNames;
    }

    /**
     * 收集类及其父类的所有非静态字段
     */
    private List<Element> collectFields(TypeElement typeElement) {
        List<Element> fields = new ArrayList<>();
        TypeElement currentClass = typeElement;

        // 遍历类及其父类
        while (currentClass != null) {
            // 处理当前类的字段
            for (Element enclosedElement : currentClass.getEnclosedElements()) {
                // 只添加非静态字段
                if (enclosedElement.getKind() == ElementKind.FIELD &&
                        !enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                    fields.add(enclosedElement);
                }
            }

            // 获取父类
            DeclaredType superClassType = (DeclaredType) currentClass.getSuperclass();
            if (superClassType.getKind() == TypeKind.NONE ||
                    OBJECT_CLASS_NAME.equals(superClassType.toString())) {
                break;
            }

            currentClass = (TypeElement) superClassType.asElement();
        }

        return fields;
    }

    /**
     * 生成Java文件
     */
    private void generateJavaFile(String packageName, String className, Element element,
                                  List<Element> fields, SqlTable sqlTable) throws IOException {
        JavaFileObject fileObject = filer.createSourceFile(packageName + "." + className, element);
        try (Writer writer = fileObject.openWriter()) { // 使用try-with-resources自动关闭资源
            writer.write(buildCode(element, fields, sqlTable, packageName, className));
        }
    }

    public abstract String getTableRemarks(Element element);

    public abstract String getFieldRemarks(String sqlFieldName);

    private String buildCode(Element element, List<Element> fieldElements, SqlTable sqlTable,
                             String packageName, String className) {
        StringBuilder code = new StringBuilder(2048); // 预分配足够的容量
        try {
            String schema = "", tableAlias = "";
            String tableRemarks = getTableRemarks(element);
            String tableName = element.getSimpleName().toString();

            if (sqlTable != null) {
                schema = sqlTable.schema();
                tableName = sqlTable.value();
                tableAlias = sqlTable.alias();
            }

            if (StringUtil.isEmpty(tableAlias)) {
                tableAlias = tableName;
            }

            // 构建类结构
            code.append("/** The code is generated by the FlexSQL. Do not modify!*/\n\n")
                    .append("package ").append(packageName).append(";\n\n")
                    .append("import cn.vonce.sql.bean.Column;\n\n")
                    .append("public class ").append(className).append(" {\n\n")
                    .append("\tpublic static final String _schema = \"").append(escape(schema)).append("\";\n")
                    .append("\tpublic static final String _tableName = \"").append(escape(tableName)).append("\";\n")
                    .append("\tpublic static final String _tableAlias = \"").append(escape(tableAlias)).append("\";\n")
                    .append("\tpublic static final String _remarks = \"").append(escape(tableRemarks)).append("\";\n")
                    .append("\tpublic static final String _all = \"").append(escape(tableAlias)).append(".*\";\n")
                    .append("\tpublic static final String _count = \"COUNT(*)\";\n");

            // 处理字段
            boolean mapUsToCc = sqlTable != null && sqlTable.mapUsToCc();
            for (Element fieldElement : fieldElements) {
                SqlColumn sqlColumn = fieldElement.getAnnotation(SqlColumn.class);

                // 跳过忽略的字段
                if (sqlColumn != null && sqlColumn.ignore()) {
                    continue;
                }

                String fieldName = fieldElement.getSimpleName().toString();
                String sqlFieldName = fieldName;

                // 处理列名映射
                if (sqlColumn != null && StringUtil.isNotEmpty(sqlColumn.value())) {
                    sqlFieldName = sqlColumn.value();
                } else if (mapUsToCc) {
                    sqlFieldName = StringUtil.humpToUnderline(fieldName);
                }

                String sqlFieldRemarks = getFieldRemarks(fieldName);

                // 添加字段常量
                code.append("\tpublic static final String ")
                        .append(sqlFieldName)
                        .append(" = \"")
                        .append(escape(sqlFieldName))
                        .append("\";\n");

                // 添加Column对象
                code.append("\tpublic static final Column ")
                        .append(sqlFieldName)
                        .append("$ = new Column(true, _tableAlias, ")
                        .append(sqlFieldName)
                        .append(", \"\", \"")
                        .append(escape(sqlFieldRemarks))
                        .append("\");\n");
            }

            code.append("\n}");
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.WARNING, "生成代码错误: " + e.getMessage());
        }
        return code.toString();
    }

    /**
     * 转义字符串中的特殊字符
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"").replace("\n", "\\n");
    }

}
