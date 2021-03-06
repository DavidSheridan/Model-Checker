package mc.compiler;

import mc.compiler.expander.ExpanderTestSuite;
import mc.compiler.lexer.LexerTestSuite;
import mc.compiler.parser.ParserTestSuite;
import mc.compiler.reference_replacer.ReferenceReplacerTestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)

@Suite.SuiteClasses({
        LexerTestSuite.class,
	    ParserTestSuite.class,
	    ExpanderTestSuite.class,
        ReferenceReplacerTestSuite.class
})
public class CompilerTestSuite {

}
