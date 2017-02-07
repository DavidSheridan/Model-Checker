package mc.commands;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import mc.exceptions.CompilationException;
import mc.util.expr.Expression;
import mc.util.expr.ExpressionEvaluator;
import org.fusesource.jansi.Ansi;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class EvalCommand implements Command{
    ExpressionEvaluator eval = new ExpressionEvaluator();
    @Override
    public void run(String[] args) {
        try {
            Expression expression;
            Map<String,Integer> vars = Collections.emptyMap();
            try {
                expression = Expression.constructExpression(String.join(" ",args));
            } catch (Exception ex) {
                expression = Expression.constructExpression(String.join(" ", Arrays.copyOfRange(args,0,args.length-1)));
                vars = new Gson().fromJson(
                    "{"+args[args.length-1]+"}".replace("=",":"), new TypeToken<Map<String, Integer>>() {}.getType()
                );
            }
            System.out.println("Expression evaluated to: "+ eval.evaluateExpression(expression,vars));
        } catch (Exception ex) {
            if (ex instanceof JsonSyntaxException) {
                System.out.println(Ansi.ansi().render("@|red The variable map that was provided is invalid. |@"));
            } else {
                System.out.println(Ansi.ansi().render("@|red There was an error parsing that expression. |@"));
                if (ex instanceof CompilationException) {
                    LoggerFactory.getLogger(((CompilationException) ex).getClazz()).error(ex.getLocalizedMessage());
                } else {
                    LoggerFactory.getLogger(EvalCommand.class).error(ex.getLocalizedMessage());
                }
            }
        }
    }
}
