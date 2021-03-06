package mc.compiler.interpreters;

import com.microsoft.z3.Context;
import mc.compiler.Compiler;
import mc.compiler.ast.ASTNode;
import mc.compiler.ast.ProcessNode;
import mc.exceptions.CompilationException;
import mc.process_models.ProcessModel;

import java.util.Map;

/**
 * Created by sheriddavi on 27/01/17.
 */
public interface ProcessModelInterpreter {

    ProcessModel interpret(ProcessNode processNode, Map<String, ProcessModel> processMap, Compiler.LocalCompiler localCompiler, Context context) throws CompilationException, InterruptedException;

    ProcessModel interpret(ASTNode astNode, String identifier, Map<String, ProcessModel> processMap, Context context) throws CompilationException, InterruptedException;
}
