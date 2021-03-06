package mc.compiler.expander;

import com.microsoft.z3.Context;
import mc.compiler.Expander;
import mc.compiler.Lexer;
import mc.compiler.Parser;
import mc.compiler.TestBase;
import mc.compiler.ast.AbstractSyntaxTree;
import mc.compiler.ast.ProcessNode;
import mc.compiler.token.Token;
import mc.exceptions.CompilationException;
import mc.util.PrintQueue;
import mc.util.expr.Expression;

import java.util.List;

public class ExpanderTests extends TestBase {

	private final Lexer lexer = new Lexer();
	private final Parser parser = new Parser();
	private final Expander expander = new Expander();
    public ExpanderTests() throws InterruptedException {
    }

    ProcessNode constructProcessNode(String code) throws InterruptedException {
        try{
            List<Token> tokens = lexer.tokenise(code);
            AbstractSyntaxTree ast = parser.parse(tokens,context);
            ast = expander.expand(ast,new PrintQueue(),context);
            return ast.getProcesses().get(0);
        }catch(CompilationException e){
            e.printStackTrace();
        }

        return null;
	}

    List<ProcessNode> constructProcessList(String code) throws InterruptedException {
        try{
            List<Token> tokens = lexer.tokenise(code);
            AbstractSyntaxTree ast = parser.parse(tokens,context);
            return expander.expand(ast,new PrintQueue(),context).getProcesses();
        }catch(CompilationException e){
            e.printStackTrace();
        }

        return null;
    }
}
