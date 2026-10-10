package cn.vonce.sql.uitls;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Java解析工具
 *
 * @author Jovi
 * @email imjovi@qq.com
 * @date 2024/7/15 12:05
 */
public class JavaParserUtil {

    private static final Logger logger = Logger.getLogger(JavaParserUtil.class.getName());

    public static String getCommentContent(String rawComment) {
        String[] texts = getCommentTexts(rawComment);
        if (texts.length == 1) {
            return texts[0];
        }
        texts = Arrays.stream(texts).filter(text -> text.charAt(0) != '@').toArray(String[]::new);
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i];
            content.append(text);
            if (i < texts.length - 1) {
                content.append(" ");
            }
        }
        return content.toString();
    }

    public static String[] getCommentTexts(String rawComment) {
        if (StringUtil.isEmpty(rawComment)) {
            return new String[]{""};
        }
        rawComment = rawComment.replaceAll("\\*", "").replaceAll("\\/", "").replaceAll("\\r", "").trim();
        String[] texts = rawComment.split("\\n");
        return Arrays.stream(texts).filter(text -> !StringUtil.isEmpty(text.trim())).map(text -> text.trim().replace("\"", "\\\"")).toArray(String[]::new);
    }

    public static String getFieldCommentContent(FieldDeclaration fieldDeclaration) {
        if (fieldDeclaration == null) {
            return "";
        }
        Optional<Comment> fieldComment = fieldDeclaration.getComment();
        if (fieldComment.isPresent()) {
            return getCommentContent(fieldComment.get().getContent());
        }
        return "";
    }

    public static String getFieldCommentContent(String fieldName, List<FieldDeclaration> fieldDeclarationList) {
        return getFieldCommentContent(getFieldDeclaration(fieldName, fieldDeclarationList));
    }

    public static FieldDeclaration getFieldDeclaration(String fieldName, List<FieldDeclaration> fieldDeclarationList) {
        if (StringUtil.isEmpty(fieldName) || fieldDeclarationList == null || fieldDeclarationList.isEmpty()) {
            return null;
        }
        for (FieldDeclaration fieldDeclaration : fieldDeclarationList) {
            NodeList<VariableDeclarator> variables = fieldDeclaration.getVariables();
            if (variables != null && !variables.isEmpty()) {
                if (variables.get(0).getNameAsString().equals(fieldName)) {
                    return fieldDeclaration;
                }
            }
        }
        return null;
    }

    public static List<FieldDeclaration> getAllFieldDeclaration(String sourceRoot, CompilationUnit compilationUnit, TypeDeclaration<?> typeDeclaration) {
        List<FieldDeclaration> fieldDeclarationList = new ArrayList<>(typeDeclaration.getFields());
        String superClassName = null;
        // 取该类型自身的父类（嵌套类必须取自身声明的父类，不能取文件中第一个类的父类）
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration) {
            NodeList<ClassOrInterfaceType> classOrInterfaceTypeNodeList = ((ClassOrInterfaceDeclaration) typeDeclaration).getExtendedTypes();
            if (classOrInterfaceTypeNodeList != null && !classOrInterfaceTypeNodeList.isEmpty()) {
                superClassName = classOrInterfaceTypeNodeList.get(0).getNameAsString();
            }
        }
        if (StringUtil.isNotEmpty(superClassName) && !"Object".equals(superClassName)) {
            Optional<PackageDeclaration> packageDeclaration = compilationUnit.getPackageDeclaration();
            if (!packageDeclaration.isPresent()) {
                return fieldDeclarationList;
            }
            String superClassPath = getSuperClassPath(sourceRoot, packageDeclaration.get().getNameAsString(), superClassName, compilationUnit.getImports());
            if (superClassPath == null) {
                return fieldDeclarationList;
            }
            try {
                // 解析父类源文件：优先当前模块，其次兄弟模块（多模块工程下父类/爷爷类可能在其它模块中）
                File superSourceFile = resolveSuperSourceFile(sourceRoot, superClassPath);
                if (superSourceFile != null) {
                    // 递归时下传父类源文件实际所在的 sourceRoot，
                    // 使更上层的继承链也能在正确的模块中继续解析
                    String superSourceRoot = superSourceFile.getAbsolutePath();
                    superSourceRoot = superSourceRoot.substring(0, superSourceRoot.length() - superClassPath.length());
                    if (!superSourceRoot.endsWith(File.separator)) {
                        superSourceRoot = superSourceRoot + File.separator;
                    }
                    JavaParser javaParser = new JavaParser();
                    ParseResult<CompilationUnit> result = javaParser.parse(superSourceFile);
                    if (result != null && result.isSuccessful() && result.getResult().isPresent()) {
                        CompilationUnit superCompilationUnit = result.getResult().get();
                        NodeList<TypeDeclaration<?>> types = superCompilationUnit.getTypes();
                        if (types != null && !types.isEmpty()) {
                            List<FieldDeclaration> superFieldDeclarations = getAllFieldDeclaration(superSourceRoot, superCompilationUnit, types.get(0));
                            if (superFieldDeclarations != null) {
                                fieldDeclarationList.addAll(superFieldDeclarations);
                            }
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                logger.warning("File not found for super class path: " + e.getMessage());
            }
        }
        return fieldDeclarationList;
    }

    /**
     * 解析父类源文件位置：优先在当前模块的 sourceRoot 下查找；
     * 找不到时按多模块布局向上取聚合工程根，遍历兄弟模块的 {@code src/main/java} 查找。
     *
     * <p>典型多模块布局：{@code 聚合根/模块A/src/main/java}、{@code 聚合根/模块B/src/main/java}。
     * 实体在模块 A，而父类/爷爷类在模块 B 时，仅靠当前 sourceRoot 无法命中，导致继承链断裂、注释丢失。
     * 找不到时返回 null（行为与旧实现一致：跳过 + 告警，不影响字段名收集）。</p>
     *
     * @param sourceRoot     当前模块源码根（{@code .../src/main/java/}）
     * @param superClassPath 父类源文件相对路径（含 {@code .java} 后缀，使用 {@link File#separator}）
     * @return 命中的源文件；未命中返回 null
     */
    public static File resolveSuperSourceFile(String sourceRoot, String superClassPath) {
        File direct = new File(sourceRoot, superClassPath);
        if (direct.exists()) {
            return direct;
        }
        // 向上取模块目录：sourceRoot 形如 <模块>/src/main/java/（允许末尾带或不带分隔符）
        String normalized = sourceRoot.endsWith(File.separator) ? sourceRoot.substring(0, sourceRoot.length() - 1) : sourceRoot;
        String suffix = "src" + File.separator + "main" + File.separator + "java";
        if (!normalized.endsWith(suffix)) {
            return null;
        }
        File moduleDir = new File(normalized.substring(0, normalized.length() - suffix.length()));
        File projectRoot = moduleDir.getParentFile();
        if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
            return null;
        }
        File[] children = projectRoot.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.getAbsolutePath().equals(moduleDir.getAbsolutePath())) {
                continue;
            }
            File candidate = new File(child, suffix + File.separator + superClassPath);
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    public static String getSuperClassPath(String sourceRoot, String packageName, String superClassName, List<ImportDeclaration> importDeclarationList) {
        if (superClassName != null) {
            if (importDeclarationList != null && !importDeclarationList.isEmpty()) {
                final List<String> importAsteriskList = new ArrayList<>();
                for (ImportDeclaration importDeclaration : importDeclarationList) {
                    String fullName = importDeclaration.getNameAsString();
                    //如果父类就存在于导包的路径中
                    if (!importDeclaration.isAsterisk() && fullName.endsWith("." + superClassName)) {
                        importAsteriskList.clear();
                        return fullName.replace(".", File.separator) + ".java";
                    }
                    //如果是导入全部的
                    if (importDeclaration.isAsterisk()) {
                        importAsteriskList.add(fullName);
                    }
                }
                //从导入的全体包名中查找
                for (String asterisk : importAsteriskList) {
                    String packPath = asterisk.replace(".", File.separator);
                    File directory = new File(sourceRoot + packPath);
                    if (directory.exists() && directory.isDirectory()) {
                        for (File file : Objects.requireNonNull(directory.listFiles())) {
                            if (file.exists() && file.isFile() && file.getName().equals(superClassName + ".java")) {
                                // 注意：必须带 ".java" 后缀，否则后续 parse 直接 FileNotFoundException，
                                // 导致继承链在此断裂（父类注释能拿到、爷爷类注释丢失）
                                return packPath + File.separator + superClassName + ".java";
                            }
                        }
                    }
                }
            }
            return packageName.replace(".", File.separator) + File.separator + superClassName + ".java";
        }
        return null;
    }

    public static Declaration getFieldDeclarationList(String sourceRoot, String javaFilePath) throws FileNotFoundException {
        return getFieldDeclarationList(sourceRoot, javaFilePath, null);
    }

    /**
     * 获取指定类型的字段声明及类声明
     *
     * @param sourceRoot      源码根目录
     * @param javaFilePath    实体类源码文件路径
     * @param simpleNamePath  目标类型由外到内的简单类名路径（如 {@code [Outer, Inner]}）；为空时取文件中的第一个类型
     */
    public static Declaration getFieldDeclarationList(String sourceRoot, String javaFilePath, List<String> simpleNamePath) throws FileNotFoundException {
        List<FieldDeclaration> fieldDeclarationList = new ArrayList<>();
        TypeDeclaration<?> typeDeclaration = null;
        //获取编译单元
        CompilationUnit compilationUnit = getCompilationUnit(javaFilePath);
        if (compilationUnit != null && compilationUnit.getTypes() != null && !compilationUnit.getTypes().isEmpty()) {
            if (simpleNamePath == null || simpleNamePath.isEmpty()) {
                typeDeclaration = compilationUnit.getTypes().get(0);
            } else {
                typeDeclaration = findTypeDeclaration(compilationUnit.getTypes(), simpleNamePath, 0);
            }
        }
        if (typeDeclaration != null) {
            fieldDeclarationList = JavaParserUtil.getAllFieldDeclaration(sourceRoot, compilationUnit, typeDeclaration);
        }
        Declaration declaration = new Declaration();
        declaration.typeDeclaration = typeDeclaration;
        declaration.fieldDeclarationList = fieldDeclarationList;
        return declaration;
    }

    /**
     * 按简单类名路径逐层查找类型声明（支持嵌套类）
     */
    private static TypeDeclaration<?> findTypeDeclaration(NodeList<TypeDeclaration<?>> candidates, List<String> simpleNamePath, int index) {
        if (candidates == null || index >= simpleNamePath.size()) {
            return null;
        }
        String targetName = simpleNamePath.get(index);
        for (TypeDeclaration<?> candidate : candidates) {
            if (targetName.equals(candidate.getNameAsString())) {
                if (index == simpleNamePath.size() - 1) {
                    return candidate;
                }
                return findTypeDeclaration(getNestedTypes(candidate), simpleNamePath, index + 1);
            }
        }
        return null;
    }

    /**
     * 获取类型中直接声明的嵌套类型
     */
    private static NodeList<TypeDeclaration<?>> getNestedTypes(TypeDeclaration<?> typeDeclaration) {
        NodeList<TypeDeclaration<?>> nestedTypes = new NodeList<>();
        for (BodyDeclaration<?> member : typeDeclaration.getMembers()) {
            if (member instanceof TypeDeclaration) {
                nestedTypes.add((TypeDeclaration<?>) member);
            }
        }
        return nestedTypes;
    }

    private static CompilationUnit getCompilationUnit(String javaFilePath) throws FileNotFoundException {
        JavaParser javaParser = new JavaParser();
        ParseResult<CompilationUnit> result = javaParser.parse(new File(javaFilePath));
        Optional<CompilationUnit> compilationUnitOptional = result.getResult();
        return compilationUnitOptional.orElse(null);
    }

    public static class Declaration {
        private TypeDeclaration<?> typeDeclaration;
        private List<FieldDeclaration> fieldDeclarationList;

        public TypeDeclaration<?> getTypeDeclaration() {
            return typeDeclaration;
        }

        public List<FieldDeclaration> getFieldDeclarationList() {
            return fieldDeclarationList;
        }
    }

}
