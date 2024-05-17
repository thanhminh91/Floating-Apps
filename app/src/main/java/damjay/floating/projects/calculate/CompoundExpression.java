package damjay.floating.projects.calculate;

import java.util.ArrayList;
import java.math.BigDecimal;

public class CompoundExpression extends Expression {
    private ArrayList<Expression> expressions;

    public static final char[] OPERATORS = {'\u00f7', '*', '+', '-'};
    public static final String[] OPERATOR_TYPE = {ExpressionType.DivideOperator, ExpressionType.MultiplyOperator, ExpressionType.PlusOperator, ExpressionType.MinusOperator};

    public CompoundExpression(String input) {
        expressions = makeExpressions(input);
        setType(ExpressionType.CompoundExpression);
    }

    private ArrayList<Expression> makeExpressions(String input) {
        ArrayList<Expression> expressions = new ArrayList<>();
        for (int i = 0; i < input.length();) {
            // A temporary string to store a numbers
            String temp = "";
            char character = input.charAt(i);

            // Check if it is a negative number. Also accept explicit positive signs
            if (character == '+' || character == '-') {
                if (input.length() > i + 1) {
                    char nextChar = input.charAt(i + 1);
                    if ((nextChar >= '0' && nextChar <= '9') || nextChar == '.' || nextChar == '(') {
                        // Emphasizing positive or negative number
                        temp += character;
                        i++;
                    } else {
                        // Invalid syntax, it has invalid characters after the operator
                        return null;
                    }
                } else {
                    // Invalid syntax, it ends with a plus or minus sign
                    return null;
                }
            }

            // Check for brackets
            if (character == '(') {
                int otherPair = findOtherPair(i + 1, input);
                // Bracket must not be empty
                if (otherPair <= i + 1) return null;
                expressions.add(new CompoundExpression(input.substring(i + 1, otherPair)));
                i = otherPair + 1;
            }

            // Try to capture a number
            while (i < input.length()) {
                char currentChar = input.charAt(i);
                if ((currentChar >= '0' && currentChar <= '9') || currentChar == '.') {
                    // Two decimal points must not be present in the integer
                    if (currentChar == '.' && temp.contains(".")) {
                        return null;
                    }
                    temp += currentChar;
                    i++;
                } else {
                    break;
                }
            }
            // Check if number capture was successful
            if (temp.length() > 0) {
                expressions.add(Expression.createExact(temp, temp.contains(".") ? ExpressionType.Decimal : ExpressionType.Integer));
            }
            if (i >= input.length()) break;

            // Check for operators
            character = input.charAt(i);
            int index = getIndex(character, OPERATORS);
            if (index >= 0) {
                if (!(temp.length() > 0 || (i > 0 && input.charAt(i - 1) == ')'))) {
                    // Invalid syntax, no digit or expression preceded the operator
                    return null;
                }
                expressions.add(Expression.createExact(character + "", OPERATOR_TYPE[index]));
                i++;
                if (input.length() == i) {
                    // Invalid syntax, operator is the last element in the string input
                    return null;
                }
            } else if (character == '(') {
                int otherPair = findOtherPair(i + 1, input);
                if (otherPair <= i + 1) {
                    // Empty bracket
                    return null;
                }
                // If this is a bracket, insert multiplication sign
                expressions.add(Expression.createExact(OPERATORS[1] + "", OPERATOR_TYPE[1]));
                expressions.add(new CompoundExpression(input.substring(i + 1, otherPair)));
                if (otherPair < input.length()) {
                    i = otherPair + 1;
                }
            } else {
                // Not an operator. What could this be?
                return null;
            }
        }
        return expressions;
    }

    private int findOtherPair(int start, String input) {
        int numUnpairedBrackets = 0;
        int otherPair = input.length();
        for (int j = start; j < input.length(); j++) {
            if (input.charAt(j) == '(') numUnpairedBrackets++;
            else if (input.charAt(j) == ')') {
                if (numUnpairedBrackets > 0) {
                    numUnpairedBrackets--;
                } else {
                    // Found the exact pair
                    otherPair = j;
                    break;
                }
            }
        }
        return otherPair;
    }

    private int getIndex(char character, char[] array) {
        for (int i = 0; i < array.length; i++) {
            if (character == array[i]) return i;
        }
        return -1;
    }

    public ArrayList<Expression> getExpressions() {
        return expressions;
    }

    private Expression finalCompute() {

        if (expressions == null) return null;
        // Evaluate divide and multiply
        for (int i = 0; i < 2; i++) {
            int index = getIndex(OPERATOR_TYPE[i]);
            while (index >= 0) {
                // Pick the two neighbours to the operator and compute
                /**
                 * When we remove the one on the left of the operator, the operator shifts to the left
                 * When we remove the one on the right (now at index), the operator is still at the same place
                 * Then we remove the operator itself which is at the left
                 */
                Expression left = expressions.remove(index - 1);
                Expression right = expressions.remove(index);
                expressions.remove(index - 1);

                expressions.add(index - 1, evaluate(left, right, OPERATOR_TYPE[i]));

                // Check if the operator is still present
                index = getIndex(OPERATOR_TYPE[i]);
            }
        }
        // Evaluate addition and subtraction
        int plusIndex = getIndex(OPERATOR_TYPE[2]);
        int minusIndex = getIndex(OPERATOR_TYPE[3]);
        int operatorType = plusIndex == -1 ? 3 : minusIndex == -1 ? 2 : plusIndex > minusIndex ? 3 : 2;
        int index = operatorType == 2 ? plusIndex : minusIndex;

        while (index >= 0) {
            // Pick the two neighbours to the operator and compute
            /**
             * When we remove the one on the left of the operator, the operator shifts to the left
             * When we remove the one on the right (now at index), the operator is still at the same place
             * Then we remove the operator itself which is at the left
             */
            Expression left = expressions.remove(index - 1);
            Expression right = expressions.remove(index);
            expressions.remove(index - 1);

            expressions.add(index - 1, evaluate(left, right, OPERATOR_TYPE[operatorType]));

            // Check if the operator is still present
            plusIndex = getIndex(OPERATOR_TYPE[2]);
            minusIndex = getIndex(OPERATOR_TYPE[3]);
            operatorType = plusIndex == -1 ? 3 : minusIndex == -1 ? 2 : plusIndex > minusIndex ? 3 : 2;
            index = operatorType == 2 ? plusIndex : minusIndex;
        }
        // If an expression is to the right of a compound expression i.e (3+5)6
        if (expressions.size() > 1) return null;
        return expressions.size() > 0 ? expressions.get(0) : null;
    }

    private Expression evaluate(Expression left, Expression right, String operatorType) {
        // TODO: Write this in a better way
        switch (operatorType) {
            case ExpressionType.DivideOperator:
                left.setType(ExpressionType.Decimal);
                right.setType(ExpressionType.Decimal);
                double divideLeftValue = Double.parseDouble(left.getExact());
                double divideRightValue = Double.parseDouble(right.getExact());
                return getExpression(divideLeftValue / divideRightValue);
            case ExpressionType.MultiplyOperator:
                if (left.getType() != right.getType() || left.getType() == ExpressionType.Decimal) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    double leftValue = Double.parseDouble(left.getExact());
                    double rightValue = Double.parseDouble(right.getExact());
                    return getExpression(leftValue * rightValue);
                } else {
                    long leftValue = Long.parseLong(left.getExact());
                    long rightValue = Long.parseLong(right.getExact());
                    return Expression.createExact(String.valueOf(leftValue * rightValue), ExpressionType.Integer);
                }
            case ExpressionType.PlusOperator:
                if (left.getType() != right.getType() || left.getType() == ExpressionType.Decimal) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    double leftValue = Double.parseDouble(left.getExact());
                    double rightValue = Double.parseDouble(right.getExact());
                    return getExpression(leftValue + rightValue);
                } else {
                    long leftValue = Long.parseLong(left.getExact());
                    long rightValue = Long.parseLong(right.getExact());
                    return Expression.createExact(String.valueOf(leftValue + rightValue), ExpressionType.Integer);
                }
            case ExpressionType.MinusOperator:
                if (left.getType() != right.getType() || left.getType() == ExpressionType.Decimal) {
                    left.setType(ExpressionType.Decimal);
                    right.setType(ExpressionType.Decimal);
                    double leftValue = Double.parseDouble(left.getExact());
                    double rightValue = Double.parseDouble(right.getExact());
                    return getExpression(leftValue - rightValue);
                } else {
                    long leftValue = Long.parseLong(left.getExact());
                    long rightValue = Long.parseLong(right.getExact());
                    return Expression.createExact(String.valueOf(leftValue - rightValue), ExpressionType.Integer);
                }
        }
        return null;
    }

    private Expression getExpression(double doubleValue) {
        BigDecimal decimal = new BigDecimal(doubleValue).setScale(12, BigDecimal.ROUND_HALF_UP);
        double result = decimal.doubleValue();
        if (result - Math.floor(result) == 0 || result - Math.ceil(result) == 0)
            return Expression.createExact(String.valueOf((long) result), ExpressionType.Integer);
        return Expression.createExact(String.valueOf(result), ExpressionType.Decimal);
    }

    private int getIndex(String type) {
        for (int i = 0; i < expressions.size(); i++) {
            if (expressions.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    public Expression compute() {
        if (expressions == null) return null;
        for (int i = 0; i < expressions.size(); i++) {
            Expression expression = expressions.get(i);
            if (expression == null) return null;

            if (expression instanceof CompoundExpression) {
                Expression computed = ((CompoundExpression) expression).compute();
                if (computed == null) return null;
                expressions.set(i, computed);
            }
        }
        return finalCompute();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < expressions.size(); i++) {
            Expression expression = expressions.get(i);
            if (expression instanceof CompoundExpression) {
                builder.append(expression.toString());
            } else {
                builder.append(expression.getExact());
            }
        }
        return builder.toString();
    }

}
