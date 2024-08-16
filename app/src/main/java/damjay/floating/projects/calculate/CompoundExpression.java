package damjay.floating.projects.calculate;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Objects;

public class CompoundExpression extends Expression {
    private final ArrayList<Expression> expressions;

    public static final char[] OPERATORS = {'^', '\u00f7', '\u00d7', '+', '-'};
    public static final String[] OPERATOR_TYPE = {ExpressionType.PowerOperator, ExpressionType.DivideOperator, ExpressionType.MultiplyOperator, ExpressionType.PlusOperator, ExpressionType.MinusOperator};

    public static final int POWER_OPERATOR = 0;
    public static final int DIVIDE_OPERATOR = 1;
    public static final int MULTIPLY_OPERATOR = 2;
    public static final int PLUS_OPERATOR = 3;
    public static final int MINUS_OPERATOR = 4;

    public CompoundExpression(String input) {
        expressions = makeExpressions(input);
        setType(ExpressionType.CompoundExpression);
    }

    private ArrayList<Expression> makeExpressions(String input) {
        // input = input.replaceAll(" ", "");
        ArrayList<Expression> expressions = new ArrayList<>();
        for (int i = 0; i < input.length();) {
            // A temporary string to store a numbers
            String temp = "";
            char character = input.charAt(i);

            // Check if it is a negative number. Also accept explicit positive signs
            if (character == '+' || character == '-') {
                if (input.length() > i + 1) {
                    char nextChar = input.charAt(i + 1);
                    if ((nextChar >= '0' && nextChar <= '9') || nextChar == '.' || nextChar == 'E' || nextChar == '(') {
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
            // Check if an exponential sign starts the number
            if (i < input.length() && input.charAt(i) == 'E') {
                // Invalid syntax: 'E' starts the number, or 'E' follows a '+' or '-'
                return null;
            }
            while (i < input.length()) {
                char currentChar = input.charAt(i);
                if ((currentChar >= '0' && currentChar <= '9') || currentChar == '.' || currentChar == 'E') {
                    // Two decimal points or exponential signs must not be present in the integer
                    if ((currentChar == '.' && temp.contains(".")) || (currentChar == 'E' && temp.contains("E"))) {
                        return null;
                    }
                    temp += currentChar;
                    if (currentChar == 'E' && ++i < input.length()) {
                        if (input.charAt(i) == '-' || input.charAt(i) == '+') {
                            temp += input.charAt(i++);
                        }
                        // The next character must be a digit
                        if (i >= input.length() || input.charAt(i) < '0' || input.charAt(i) > '9') return null;
                        continue;
                    }
                    i++;
                } else {
                    break;
                }
            }
            // Check if number capture was successful
            if (!temp.isEmpty()) {
                expressions.add(Expression.createExact(temp, temp.contains(".") || temp.contains("E") ? ExpressionType.Decimal : ExpressionType.Integer));
            }
            if (i >= input.length()) break;

            // Check for operators
            character = input.charAt(i);

            if (character == '(') {
                int otherPair = findOtherPair(i + 1, input);
                if (otherPair <= i + 1) {
                    // Empty bracket
                    return null;
                }
                // If this is a bracket, insert multiplication sign
                expressions.add(Expression.createExact(OPERATORS[MULTIPLY_OPERATOR] + "", OPERATOR_TYPE[MULTIPLY_OPERATOR]));
                expressions.add(new CompoundExpression(input.substring(i + 1, otherPair)));
                // Move the current index to the new position after the last bracket
                i = otherPair + 1;
                // If we are at the last character of the expression, break.
                if (i >= input.length())
                    break;
            }

            character = input.charAt(i);
            int index = getIndex(character);
            if (index >= 0) {
                if (!(!temp.isEmpty() || (i > 0 && input.charAt(i - 1) == ')'))) {
                    // Invalid syntax, no digit or expression preceded the operator
                    return null;
                }
                expressions.add(Expression.createExact(character + "", OPERATOR_TYPE[index]));
                i++;
                if (input.length() == i) {
                    // Invalid syntax, operator is the last element in the string input
                    return null;
                }
            } else {
                // Not an operator. What could this be?
                System.out.println("Invalid character: '" + character + "'");
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

    private int getIndex(char character) {
        for (int i = 0; i < CompoundExpression.OPERATORS.length; i++) {
            if (character == CompoundExpression.OPERATORS[i]) return i;
        }
        return -1;
    }

    public ArrayList<Expression> getExpressions() {
        return expressions;
    }

    private Expression finalCompute() {

        if (expressions == null) return null;
        // Evaluate divide and multiply and raised to a power
        for (int i = 0; i < 3; i++) {
            int index = getIndex(OPERATOR_TYPE[i]);
            while (index >= 0) {
                // Pick the two neighbours to the operator and compute
                /*
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
        int plusIndex = getIndex(OPERATOR_TYPE[PLUS_OPERATOR]);
        int minusIndex = getIndex(OPERATOR_TYPE[MINUS_OPERATOR]);
        int operatorType = plusIndex == -1 ? MINUS_OPERATOR : minusIndex == -1 ? PLUS_OPERATOR : plusIndex > minusIndex ? MINUS_OPERATOR : PLUS_OPERATOR;
        int index = operatorType == PLUS_OPERATOR ? plusIndex : minusIndex;

        while (index >= 0) {
            // Pick the two neighbours to the operator and compute
            /*
             * When we remove the one on the left of the operator, the operator shifts to the left
             * When we remove the one on the right (now at index), the operator is still at the same place
             * Then we remove the operator itself which is at the left
             */
            Expression left = expressions.remove(index - 1);
            Expression right = expressions.remove(index);
            expressions.remove(index - 1);

            // Now insert the computed value
            expressions.add(index - 1, evaluate(left, right, OPERATOR_TYPE[operatorType]));

            // Check if the operator is still present
            plusIndex = getIndex(OPERATOR_TYPE[PLUS_OPERATOR]);
            minusIndex = getIndex(OPERATOR_TYPE[MINUS_OPERATOR]);
            operatorType = plusIndex == -1 ? MINUS_OPERATOR : minusIndex == -1 ? PLUS_OPERATOR : plusIndex > minusIndex ? MINUS_OPERATOR : PLUS_OPERATOR;
            index = operatorType == PLUS_OPERATOR ? plusIndex : minusIndex;
        }
        // If an expression is to the right of a compound expression i.e (3+5)6
        if (expressions.size() > 1) return null;
        return !expressions.isEmpty() ? expressions.get(0) : null;
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
                if (!Objects.equals(left.getType(), right.getType()) || Objects.equals(left.getType(), ExpressionType.Decimal)) {
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
            case ExpressionType.PowerOperator:
                left.setType(ExpressionType.Decimal);
                right.setType(ExpressionType.Decimal);
                double powerLeftValue = Double.parseDouble(left.getExact());
                double powerRightValue = Double.parseDouble(right.getExact());
                return getExpression(Math.pow(powerLeftValue, powerRightValue));
            case ExpressionType.PlusOperator:
                if (!Objects.equals(left.getType(), right.getType()) || Objects.equals(left.getType(), ExpressionType.Decimal)) {
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
                if (!Objects.equals(left.getType(), right.getType()) || Objects.equals(left.getType(), ExpressionType.Decimal)) {
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
        BigDecimal decimal = new BigDecimal(doubleValue).setScale(12, RoundingMode.HALF_UP);
        double result = decimal.doubleValue();
        if (result - Math.floor(result) == 0 || result - Math.ceil(result) == 0)
            return Expression.createExact(String.valueOf((long) result), ExpressionType.Integer);
        return Expression.createExact(String.valueOf(result), ExpressionType.Decimal);
    }

    private int getIndex(String type) {
        for (int i = 0; i < expressions.size(); i++) {
            if (Objects.equals(expressions.get(i).getType(), type)) {
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
        for (Expression expression : expressions) {
            if (expression instanceof CompoundExpression) {
                builder.append(expression);
            } else {
                builder.append(expression.getExact());
            }
        }
        return builder.toString();
    }

}
