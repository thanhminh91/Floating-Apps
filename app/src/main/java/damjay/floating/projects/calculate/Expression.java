package damjay.floating.projects.calculate;

public class Expression {
    private String exact;
    private String type;

    protected Expression() {
        
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
    
    public static Expression createExact(String input, String type) {
        Expression expr = new Expression();
        expr.setExact(input, type);
        return expr;
    }

    private void setExact(String exact, String type) {
        this.exact = exact;
        this.type = type;
    }
    
    public String getExact() {
        return exact;
    }

    @Override
    public String toString() {
        return "type=" + type + ", value=" + exact;
    }

}
