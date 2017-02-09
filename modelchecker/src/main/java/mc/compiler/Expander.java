package mc.compiler;

import mc.compiler.ast.*;
import mc.compiler.iterator.IndexIterator;
import mc.exceptions.CompilationException;
import mc.util.expr.*;
import mc.webserver.LogMessage;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Expander {

    private final Pattern VAR_PATTERN = Pattern.compile("\\$[a-z][a-zA-Z0-9_]*");

    private Map<String, Expression> globalVariableMap;
    private ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private Map<String,List<String>> identMap = new HashMap<>();
    private Set<String> hiddenVariables;
    public AbstractSyntaxTree expand(AbstractSyntaxTree ast) throws CompilationException {
        globalVariableMap = ast.getVariableMap();

        List<ProcessNode> processes = ast.getProcesses();
        for(int i = 0; i < processes.size(); i++){
            ProcessNode process = processes.get(i);
            new LogMessage("Expanding:",process).send();
            if (process.hasVariableSet())
                hiddenVariables = process.getVariables().getVariables();
            else hiddenVariables = Collections.emptySet();
            Map<String, Object> variableMap = new HashMap<String, Object>();
            ASTNode root = expand(process.getProcess(), variableMap);
            process.setProcess(root);
            List<LocalProcessNode> localProcesses = expandLocalProcesses(process.getLocalProcesses(), variableMap);
            process.setLocalProcesses(localProcesses);
        }

        List<OperationNode> operations = ast.getOperations();
        for(int i = 0; i < operations.size(); i++){
            OperationNode operation = operations.get(i);
            Map<String, Object> variableMap = new HashMap<String, Object>();
            ASTNode process1 = expand(operation.getFirstProcess(), variableMap);
            ASTNode process2 = expand(operation.getSecondProcess(), variableMap);

            operation.setFirstProcess(process1);
            operation.setSecondProcess(process2);
        }

        return ast;
    }

    private List<LocalProcessNode> expandLocalProcesses(List<LocalProcessNode> localProcesses, Map<String, Object> variableMap) throws CompilationException {
        List<LocalProcessNode> newLocalProcesses = new ArrayList<LocalProcessNode>();

        for(int i = 0; i < localProcesses.size(); i++){
            LocalProcessNode localProcess = localProcesses.get(i);
            if(localProcess.getRanges() == null){
                ASTNode root = expand(localProcess.getProcess(), variableMap);
                localProcess.setProcess(root);
                newLocalProcesses.add(localProcess);
            }
            else{
                newLocalProcesses.addAll(expandLocalProcesses(localProcess, variableMap, localProcess.getRanges().getRanges(), 0));
            }
        }

        return newLocalProcesses;
    }

    private List<LocalProcessNode> expandLocalProcesses(LocalProcessNode localProcess, Map<String, Object> variableMap, List<IndexNode> ranges, int index) throws CompilationException {
        List<LocalProcessNode> newLocalProcesses = new ArrayList<LocalProcessNode>();
        if (index == 0) {
            identMap.put(localProcess.getIdentifier(),new ArrayList<>());
            for (IndexNode node: ranges) {
                identMap.get(localProcess.getIdentifier()).add(node.getVariable());
            }
        }
        if(index < ranges.size()){
            IndexNode range = ranges.get(index);
            IndexIterator iterator = IndexIterator.construct(expand(range));
            String variable = range.getVariable();
            localProcess.setIdentifier(localProcess.getIdentifier() + "[" + variable + "]");

            while(iterator.hasNext()){
                variableMap.put(variable, iterator.next());
                newLocalProcesses.addAll(expandLocalProcesses((LocalProcessNode) localProcess.copy(), variableMap, ranges, index + 1));
            }
        }
        else{
            LocalProcessNode clone = (LocalProcessNode)localProcess.copy();
            ASTNode root = expand(clone.getProcess(), variableMap);
            clone.setIdentifier(processVariables(clone.getIdentifier(), variableMap));
            clone.setProcess(root);
            newLocalProcesses.add(clone);
        }

        return newLocalProcesses;
    }

    private ASTNode expand(ASTNode astNode, Map<String, Object> variableMap) throws CompilationException {
        if(astNode instanceof ProcessRootNode){
            astNode = expand((ProcessRootNode)astNode, variableMap);
        }
        else if(astNode instanceof ActionLabelNode){
            astNode = expand((ActionLabelNode)astNode, variableMap);
        }
        else if(astNode instanceof IndexNode){
            astNode = expand((IndexNode)astNode, variableMap);
        }
        else if(astNode instanceof SequenceNode){
            astNode = expand((SequenceNode)astNode, variableMap);
        }
        else if(astNode instanceof ChoiceNode){
            astNode = expand((ChoiceNode)astNode, variableMap);
        }
        else if(astNode instanceof CompositeNode){
            astNode = expand((CompositeNode)astNode, variableMap);
        }
        else if(astNode instanceof IfStatementNode){
            astNode = expand((IfStatementNode)astNode, variableMap);
        }
        else if(astNode instanceof FunctionNode){
            astNode = expand((FunctionNode)astNode, variableMap);
        }
        else if(astNode instanceof IdentifierNode){
            astNode = expand((IdentifierNode)astNode, variableMap);
        }
        else if(astNode instanceof ForAllStatementNode){
            astNode = expand((ForAllStatementNode)astNode, variableMap);
        }
        astNode.getMetaData().put("variables",new HashMap<>(variableMap));
        return astNode;
    }

    private ASTNode expand(ProcessRootNode astNode, Map<String, Object> variableMap) throws CompilationException {
        if(astNode.hasLabel()) {
            astNode.setLabel(processVariables(astNode.getLabel(), variableMap));
        }
        ASTNode process = expand(astNode.getProcess(), variableMap);
        astNode.setProcess(process);

        if(astNode.hasRelabelSet()){
            astNode.setRelabelSet(expand(astNode.getRelabelSet()));
        }

        if(astNode.hasHiding()){
            HidingNode hiding = astNode.getHiding();
            hiding.setSet(expand(hiding.getSet()));
            astNode.setHiding(hiding);
        }

        return astNode;
    }

    private ActionLabelNode expand(ActionLabelNode astNode, Map<String, Object> variableMap) throws CompilationException {
        String action = processVariables(astNode.getAction(), variableMap);
        astNode.setAction(action);
        return astNode;
    }

    private ASTNode expand(IndexNode astNode, Map<String, Object> variableMap) throws CompilationException {
        IndexIterator iterator = IndexIterator.construct(expand(astNode));
        Stack<ASTNode> iterations = new Stack<ASTNode>();
        while(iterator.hasNext()){
            Object element = iterator.next();
            variableMap.put(astNode.getVariable(), element);
            iterations.push(expand(astNode.getProcess().copy(), variableMap));
        }

        ASTNode node = iterations.pop();
        while(!iterations.isEmpty()){
            ASTNode nextNode = iterations.pop();
            node = new ChoiceNode(nextNode, node, astNode.getLocation());
        }

        return node;
    }

    private ASTNode expand(IndexNode astNode) throws CompilationException {
        // expand out nested indices
        ASTNode range = astNode;
        while(range instanceof IndexNode){
            range = ((IndexNode)range).getRange();
        }

        //if the range is a set then it may need expanding
        if(range instanceof SetNode){
            range = expand((SetNode)range);
        }

        return range;
    }

    private SequenceNode expand(SequenceNode astNode, Map<String, Object> variableMap) throws CompilationException {
        //add a guard to every sequenceNode. This will only contain next data.
        Guard guard = new Guard();
        if (astNode.getTo() instanceof IdentifierNode) {
            //Parse the next values from this IdentifierNode
            guard.parseNext(((IdentifierNode) astNode.getTo()).getIdentifier(), globalVariableMap, identMap);
            //There were next values, so assign to the metadata
            if (guard.hasData())
                astNode.getMetaData().put("guard",guard);
        }
        ActionLabelNode from = expand(astNode.getFrom(), variableMap);
        ASTNode to = expand(astNode.getTo(), variableMap);
        astNode.setFrom(from);
        astNode.setTo(to);
        return astNode;
    }

    private ASTNode expand(ChoiceNode astNode, Map<String, Object> variableMap) throws CompilationException {
        ASTNode process1 = expand(astNode.getFirstProcess(), variableMap);
        ASTNode process2 = expand(astNode.getSecondProcess(), variableMap);

        // check if either one of the branches is empty
        if(process1 instanceof EmptyNode || process1 instanceof TerminalNode){
            return process2;
        }
        else if(process2 instanceof EmptyNode || process2 instanceof TerminalNode){
            return process1;
        }

        astNode.setFirstProcess(process1);
        astNode.setSecondProcess(process2);
        return astNode;
    }

    private ASTNode expand(CompositeNode astNode, Map<String, Object> variableMap) throws CompilationException {
        ASTNode process1 = expand(astNode.getFirstProcess(), variableMap);
        ASTNode process2 = expand(astNode.getSecondProcess(), variableMap);

        // check if either one of the branches is empty
        if(process1 instanceof EmptyNode || process1 instanceof TerminalNode){
            return process2;
        }
        else if(process2 instanceof EmptyNode || process2 instanceof TerminalNode){
            return process1;
        }

        astNode.setFirstProcess(process1);
        astNode.setSecondProcess(process2);
        return astNode;
    }

    private ASTNode expand(IfStatementNode astNode, Map<String, Object> variableMap) throws CompilationException {
        Guard guard = new Guard();
        guard.setGuard(astNode.getCondition());
        guard.setVariables(new ExpressionPrinter().getVariables(astNode.getCondition(),variableMap));
        guard.setHiddenVariables(hiddenVariables);
        boolean condition = evaluateCondition(astNode.getCondition(), variableMap);
        if(condition){
            ASTNode expand = expand(astNode.getTrueBranch(), variableMap);
            if (expand.getMetaData().containsKey("guard"))
                guard.mergeWith((Guard) expand.getMetaData().get("guard"));
            expand.getMetaData().put("guard",guard);
            return expand;
        }
        else if(astNode.hasFalseBranch()){
            ASTNode expand = expand(astNode.getFalseBranch(), variableMap);
            if (expand.getMetaData().containsKey("guard"))
                guard.mergeWith((Guard) expand.getMetaData().get("guard"));
            expand.getMetaData().put("guard",guard);
            return expand;
        }

        return new EmptyNode();
    }

    private FunctionNode expand(FunctionNode astNode, Map<String, Object> variableMap) throws CompilationException {
        ASTNode process = expand(astNode.getProcess(), variableMap);
        astNode.setProcess(process);
        return astNode;
    }

    private IdentifierNode expand(IdentifierNode astNode, Map<String, Object> variableMap) throws CompilationException {
        String identifier = processVariables(astNode.getIdentifier(), variableMap);
        astNode.setIdentifer(identifier);
        return astNode;
    }

    private ASTNode expand(ForAllStatementNode astNode, Map<String, Object> variableMap) throws CompilationException {
        Stack<ASTNode> nodes = expand(astNode.getProcess(), variableMap, astNode.getRanges().getRanges(), 0);

        ASTNode node = nodes.pop();
        while(!nodes.isEmpty()){
            ASTNode nextNode = nodes.pop();
            node = new CompositeNode(nextNode, node, astNode.getLocation());
        }

        return node;
    }

    private Stack<ASTNode> expand(ASTNode process, Map<String, Object> variableMap, List<IndexNode> ranges, int index) throws CompilationException {
        Stack<ASTNode> nodes = new Stack<ASTNode>();

        if(index < ranges.size()){
            IndexNode node = ranges.get(index);
            IndexIterator iterator = IndexIterator.construct(expand(node));
            String variable = node.getVariable();

            while(iterator.hasNext()){
                variableMap.put(variable, iterator.next());
                nodes.addAll(expand(process, variableMap, ranges, index + 1));
            }
        }
        else{
            process = expand(process.copy(), variableMap);
            nodes.add(process);
        }

        return nodes;
    }

    private RelabelNode expand(RelabelNode relabel) throws CompilationException {
        List<RelabelElementNode> relabels = new ArrayList<RelabelElementNode>();

        for(RelabelElementNode element : relabel.getRelabels()){
            if(!element.hasRanges()){
                relabels.add(element);
            }
            else{
                Map<String, Object> variableMap = new HashMap<String, Object>();
                relabels.addAll(expand(element, variableMap, element.getRanges().getRanges(), 0));
            }
        }

        return new RelabelNode(relabels, relabel.getLocation());
    }

    private List<RelabelElementNode> expand(RelabelElementNode element, Map<String, Object> variableMap, List<IndexNode> ranges, int index) throws CompilationException {
        List<RelabelElementNode> elements = new ArrayList<RelabelElementNode>();

        if(index < ranges.size()){
            IndexNode node = ranges.get(index);
            IndexIterator iterator = IndexIterator.construct(expand(node));
            String variable = node.getVariable();

            while(iterator.hasNext()){
                variableMap.put(variable, iterator.next());
                elements.addAll(expand(element, variableMap, ranges, index + 1));
            }
        }
        else{
            String newLabel = processVariables(element.getNewLabel(), variableMap);
            String oldLabel = processVariables(element.getOldLabel(), variableMap);
            elements.add(new RelabelElementNode(newLabel, oldLabel, element.getLocation()));
        }

        return elements;
    }

    private SetNode expand(SetNode set) throws CompilationException {
        // check if any ranges were defined for this set
        Map<Integer, RangesNode> rangeMap = set.getRangeMap();
        if(rangeMap.isEmpty()){
            return set;
        }

        List<String> actions = set.getSet();
        List<String> newActions = new ArrayList<String>();
        for(int i = 0; i < actions.size(); i++){
            if(rangeMap.containsKey(i)){
                Map<String, Object> variableMap = new HashMap<String, Object>();
                newActions.addAll(expand(actions.get(i), variableMap, rangeMap.get(i).getRanges(), 0));
            }
            else{
                newActions.add(actions.get(i));
            }
        }

        return new SetNode(newActions, set.getLocation());
    }

    private List<String> expand(String action, Map<String, Object> variableMap, List<IndexNode> ranges, int index) throws CompilationException {
        List<String> actions = new ArrayList<String>();
        if(index < ranges.size()){
            IndexNode node = ranges.get(index);
            IndexIterator iterator = IndexIterator.construct(expand(node));
            String variable = node.getVariable();

            while(iterator.hasNext()){
                variableMap.put(variable, iterator.next());
                actions.addAll(expand(action, variableMap, ranges, index + 1));
            }
        }
        else{
            actions.add(processVariables(action, variableMap));
        }

        return actions;
    }

    private int evaluateExpression(Expression expression, Map<String, Object> variableMap) throws CompilationException {
        // remove all strings from the variableMap
        Map<String, Integer> variables = new HashMap<>();
        for(String key : variableMap.keySet()){
            Object value = variableMap.get(key);
            if(value instanceof Integer){
                variables.put(key, (Integer)value);
            }
        }
        Expression ex = ExpressionSimplifier.simplify(expression, variables);
        if (ex instanceof BooleanOperand) return ((BooleanOperand) ex).getValue()?1:0;
        if (ex instanceof IntegerOperand) return ((IntegerOperand) ex).getValue();
        throw new CompilationException(getClass(),"There was an undefined variable in that statement.");
    }

    private boolean evaluateCondition(Expression condition, Map<String, Object> variableMap) throws CompilationException {
        Map<String, Integer> variables = new HashMap<String, Integer>();
        for(String key : variableMap.keySet()){
            Object value = variableMap.get(key);
            if(value instanceof Integer){
                variables.put(key, (Integer)value);
            }
        }

        Expression ex = ExpressionSimplifier.simplify(condition,variables);
        if (ex instanceof BooleanOperand)
            return ((BooleanOperand) ex).getValue();
        throw new CompilationException(getClass(),"Guard expressions should simplify to boolean values.");
    }

    private String processVariables(String string, Map<String, Object> variableMap) throws CompilationException {
        Map<String, Integer> integerMap = constructIntegerMap(variableMap);
        while(true){
            Matcher matcher = VAR_PATTERN.matcher(string);
            if(matcher.find()){
                String variable = matcher.group();
                // check if the variable is a global variable
                if(globalVariableMap.containsKey(variable)){
                    Expression expression = globalVariableMap.get(variable);
                    int result = evaluator.evaluateExpression(expression, integerMap);
                    string = string.replaceAll(Pattern.quote(variable)+"\\b","" + result);
                }
                else if(integerMap.containsKey(variable)){
                    string = string.replaceAll(Pattern.quote(variable)+"\\b","" + integerMap.get(variable));
                }
                else if(variableMap.containsKey(variable)){
                    string = string.replaceAll(Pattern.quote("[" + variable + "]"), "" + variableMap.get(variable));
                }
            }
            else{
                break;
            }
        }

        return string;
    }

    private Map<String, Integer> constructIntegerMap(Map<String, Object> variableMap){
        Map<String, Integer> integerMap = new HashMap<String, Integer>();
        for(String key : variableMap.keySet()){
            if(variableMap.get(key) instanceof Integer){
                integerMap.put(key, (Integer)variableMap.get(key));
            }
        }

        return integerMap;
    }
}
