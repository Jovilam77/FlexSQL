import cn.vonce.sql.uitls.JavaParserUtil;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * @author Jovi《imjovi@qq.com》
 * @version 1.0《2025/8/25 17:32》
 */
public class Test {

    private static List<FieldDeclaration> fieldDeclarationList = null;

    public static void main(String[] args) throws FileNotFoundException {
        String sourceRoot = "E:\\IdeaProjects\\FlexSQL\\flexsql-java\\src\\test\\java\\";
        JavaParserUtil.Declaration declaration = JavaParserUtil.getFieldDeclarationList(sourceRoot, "E:\\IdeaProjects\\FlexSQL\\flexsql-java\\src\\test\\java\\PayOrderDetails.java");
        fieldDeclarationList = declaration.getFieldDeclarationList();
       String name =  JavaParserUtil.getFieldCommentContent("orderId", fieldDeclarationList);
       System.out.println(name);
    }

}
