package cn.vonce.sql.uitls;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * 回归测试：实体类继承链（父类 / 爷爷类）字段注释解析。
 *
 * <p>{@link JavaParserUtil#getAllFieldDeclaration} 按「解析实体源文件 → 沿 extends 链递归解析父类源文件」
 * 收集字段注释。历史缺陷：当父类通过<b>通配符 import</b>（{@code import cn.g.*;}）引入爷爷类时，
 * {@code getSuperClassPath} 返回的路径漏掉 {@code .java} 后缀，导致
 * {@code javaParser.parse} 抛 {@code FileNotFoundException}、继承链在此断裂——
 * 表现为「父类字段注释能拿到，爷爷类字段注释拿不到」（字段名不受影响，
 * 因为 {@code SqlConstantProcessor.collectFields} 走编译期 TypeElement 镜像，天然遍历全继承链）。</p>
 *
 * <p>本测试覆盖四种导入形态，均要求三层（Entity / Parent / Grand）字段注释完整。</p>
 *
 * @author Jovi
 */
public class JavaParserUtilSuperChainTest {

    private static void write(File file, String content) throws Exception {
        file.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            w.print(content);
        }
    }

    /** 生成一个带字段 Javadoc 的简单实体源码。 */
    private static String fieldSrc(String cls, String ext, String... fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("public class ").append(cls);
        if (ext != null) {
            sb.append(" extends ").append(ext);
        }
        sb.append(" {\n");
        for (String f : fields) {
            sb.append("    /** ").append(f).append("备注 */\n");
            sb.append("    private String ").append(f).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** 校验指定字段（含跨模块继承链上的字段）的注释均能解析到。 */
    private static void assertChain(File srcRoot, String entityPath, String... expectedFields) throws Exception {
        JavaParserUtil.Declaration d = JavaParserUtil.getFieldDeclarationList(
                srcRoot.getAbsolutePath() + File.separator,
                srcRoot.getAbsolutePath() + File.separator + entityPath, null);
        for (String f : expectedFields) {
            String comment = JavaParserUtil.getFieldCommentContent(f, d.getFieldDeclarationList());
            Assert.assertEquals("字段 " + f + " 注释丢失（sourceRoot=" + srcRoot.getName() + "）",
                    f + "备注", comment);
        }
    }

    @Test
    public void samePackageChain() throws Exception {
        // Entity / Parent / Grand 同包，无任何 import
        File src = new File("target/superchain/samePackage");
        write(new File(src, "cn/x/Grand.java"), "package cn.x;\n" + fieldSrc("Grand", null, "g1"));
        write(new File(src, "cn/x/Parent.java"), "package cn.x;\n" + fieldSrc("Parent", "Grand", "p1"));
        write(new File(src, "cn/x/Entity.java"), "package cn.x;\n" + fieldSrc("Entity", "Parent", "e1"));
        assertChain(src, "cn/x/Entity.java", "e1", "p1", "g1");
    }

    @Test
    public void explicitImportChain() throws Exception {
        // Entity 显式 import Parent；Parent 与 Grand 同包（无需 import）
        File src = new File("target/superchain/explicit");
        write(new File(src, "cn/y/Grand.java"), "package cn.y;\n" + fieldSrc("Grand", null, "g1"));
        write(new File(src, "cn/y/Parent.java"), "package cn.y;\n" + fieldSrc("Parent", "Grand", "p1"));
        write(new File(src, "cn/z/Entity.java"), "package cn.z;\nimport cn.y.Parent;\n" + fieldSrc("Entity", "Parent", "e1"));
        assertChain(src, "cn/z/Entity.java", "e1", "p1", "g1");
    }

    @Test
    public void explicitImportAtEveryLevel() throws Exception {
        // 三层全部跨包，父类显式 import 爷爷类（回归基线：该形态此前即正常）
        File src = new File("target/superchain/explicitEveryLevel");
        write(new File(src, "cn/g/Grand.java"), "package cn.g;\n" + fieldSrc("Grand", null, "g1"));
        write(new File(src, "cn/y/Parent.java"), "package cn.y;\nimport cn.g.Grand;\n" + fieldSrc("Parent", "Grand", "p1"));
        write(new File(src, "cn/z/Entity.java"), "package cn.z;\nimport cn.y.Parent;\n" + fieldSrc("Entity", "Parent", "e1"));
        assertChain(src, "cn/z/Entity.java", "e1", "p1", "g1");
    }

    @Test
    public void asteriskImportBreaksGrandParent() throws Exception {
        // 父类用通配符 import 爷爷类 —— 缺陷场景：路径漏 ".java" 导致继承链断裂、爷爷类注释丢失
        File src = new File("target/superchain/asterisk");
        write(new File(src, "cn/g/Grand.java"), "package cn.g;\n" + fieldSrc("Grand", null, "g1"));
        write(new File(src, "cn/y/Parent.java"), "package cn.y;\nimport cn.g.*;\n" + fieldSrc("Parent", "Grand", "p1"));
        write(new File(src, "cn/z/Entity.java"), "package cn.z;\nimport cn.y.Parent;\n" + fieldSrc("Entity", "Parent", "e1"));
        assertChain(src, "cn/z/Entity.java", "e1", "p1", "g1");
    }

    // ==================== 跨模块继承链（方案 A：兄弟模块源码查找） ====================

    @Test
    public void parentInSiblingModule() throws Exception {
        // 多模块布局：实体在 app 模块，父类在 base 模块（兄弟目录）
        File root = new File("target/superchain/multi1");
        File appSrc = new File(root, "app/src/main/java");
        File baseSrc = new File(root, "base/src/main/java");
        write(new File(baseSrc, "cn/y/Parent.java"), "package cn.y;\n" + fieldSrc("Parent", null, "p1"));
        write(new File(appSrc, "cn/z/Entity.java"), "package cn.z;\nimport cn.y.Parent;\n" + fieldSrc("Entity", "Parent", "e1"));
        assertChain(appSrc, "cn/z/Entity.java", "e1", "p1");
    }

    @Test
    public void wholeSuperChainInSiblingModule() throws Exception {
        // 更进一步：父类 + 爷爷类整体在 base 模块，Entity 在 app 模块
        File root = new File("target/superchain/multi2");
        File appSrc = new File(root, "app/src/main/java");
        File baseSrc = new File(root, "base/src/main/java");
        write(new File(baseSrc, "cn/g/Grand.java"), "package cn.g;\n" + fieldSrc("Grand", null, "g1"));
        write(new File(baseSrc, "cn/y/Parent.java"), "package cn.y;\nimport cn.g.Grand;\n" + fieldSrc("Parent", "Grand", "p1"));
        write(new File(appSrc, "cn/z/Entity.java"), "package cn.z;\nimport cn.y.Parent;\n" + fieldSrc("Entity", "Parent", "e1"));
        assertChain(appSrc, "cn/z/Entity.java", "e1", "p1", "g1");
    }
}
