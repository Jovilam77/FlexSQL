package cn.vonce.sql.java.processor;

import cn.vonce.sql.processor.SqlConstantProcessor;
import cn.vonce.sql.uitls.JavaParserUtil;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * 生成表字段常量处理器
 *
 * @author Jovi
 * @version 1.0
 * @email imjovi@qq.com
 * @date 2020/2/26 14:21
 */
@SupportedAnnotationTypes({"cn.vonce.sql.annotation.SqlTable"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class JavaSqlConstantProcessor extends SqlConstantProcessor {

    private Messager messager;
    // 使用ThreadLocal存储字段声明，避免多线程问题
    private final ThreadLocal<List<FieldDeclaration>> fieldDeclarationList = ThreadLocal.withInitial(() -> null);
    private String sourceRootCache;  // 缓存源码根目录路径
    private boolean isWindows;       // 缓存系统类型判断结果

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        // 初始化系统类型判断，只需执行一次
        isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        try {
            return super.process(annotations, env);
        } finally {
            // 清理线程本地变量，避免内存泄漏
            fieldDeclarationList.remove();
        }
    }

    @Override
    public String getTableRemarks(Element element) {
        try {
            // 获取源码根目录（缓存结果，避免重复计算）
            String sourceRoot = getSourceRoot();
            if (sourceRoot == null) {
                return "";
            }

            // 构建Java文件路径
            Path javaFilePath = buildJavaFilePath(element, sourceRoot);
            if (!Files.exists(javaFilePath)) {
                return "";
            }

            // 解析Java文件获取声明信息
            JavaParserUtil.Declaration declaration = JavaParserUtil.getFieldDeclarationList(
                    sourceRoot, javaFilePath.toString());

            TypeDeclaration<?> typeDeclaration = declaration.getTypeDeclaration();
            fieldDeclarationList.set(declaration.getFieldDeclarationList());

            // 提取类注释
            if (typeDeclaration != null && typeDeclaration.getComment().isPresent()) {
                return JavaParserUtil.getCommentContent(
                        typeDeclaration.getComment().get().getContent());
            }
        } catch (Exception e) {
            // 使用父类的messager输出错误信息
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "获取表注释失败: " + e.getMessage());
        }
        return "";
    }

    @Override
    public String getFieldRemarks(String sqlFieldName) {
        try {
            List<FieldDeclaration> fields = fieldDeclarationList.get();
            if (fields != null) {
                return JavaParserUtil.getFieldCommentContent(sqlFieldName, fields);
            }
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "获取字段注释失败: " + e.getMessage());
        }
        return "";
    }

    /**
     * 获取源码根目录并缓存
     */
    private String getSourceRoot() {
        if (sourceRootCache != null) {
            return sourceRootCache;
        }

        try {
            // 尝试通过类加载器获取路径
            String path = getClass().getClassLoader().getResource("").getFile();
            path = java.net.URLDecoder.decode(path, "UTF-8"); // 正确解码URL

            File classDir = new File(path);
            if (classDir.exists()) {
                String targetClasses = isWindows ? "\\target\\classes\\" : "/target/classes/";
                int index = path.indexOf(targetClasses);
                if (index != -1) {
                    sourceRootCache = path.substring(0, index);
                }
            }

            // 如果未找到，使用当前工作目录
            if (sourceRootCache == null) {
                sourceRootCache = new File("").getAbsolutePath();
            }

            // 构建源码根目录路径
            Path srcRootPath = Paths.get(sourceRootCache, "src", "main", "java");
            if (Files.exists(srcRootPath)) {
                sourceRootCache = srcRootPath.toString();
                return sourceRootCache;
            }
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "获取源码根目录失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 构建Java文件的完整路径
     */
    private Path buildJavaFilePath(Element element, String sourceRoot) {
        PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        String packagePath = packageElement.getQualifiedName()
                .toString()
                .replace(".", File.separator);

        return Paths.get(sourceRoot, packagePath, element.getSimpleName() + ".java");
    }
}