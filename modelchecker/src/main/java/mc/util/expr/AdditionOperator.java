package mc.util.expr;

public class AdditionOperator extends BothOperator {

	public AdditionOperator(Expression lhs, Expression rhs){
		super(lhs, rhs);
	}

	public int evaluate(){
		return getLeftHandSide().evaluate() + getRightHandSide().evaluate();
	}
}