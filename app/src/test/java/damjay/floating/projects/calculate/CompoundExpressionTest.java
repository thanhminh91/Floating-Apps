package damjay.floating.projects.calculate;

import org.junit.Test;

public class CompoundExpressionTest {
    @Test
    public void testCalculate() {
        CompoundExpression expression = new CompoundExpression("5(6)");
        Expression result = expression.compute();
        // Assert that result == 30
    }
}
