package de.verdox.pv_miner.formula;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormulaEngine {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$([a-zA-Z0-9_]+)");

    public static double evaluate(double x, String formula, VariableProvider variableProvider) {
        if (formula == null || formula.isBlank()) {
            return x;
        }

        String expression = formula.replace(" ", "").replace("x", String.valueOf(x));

        try {
            Matcher matcher = VARIABLE_PATTERN.matcher(expression);
            StringBuilder sb = new StringBuilder();

            while (matcher.find()) {
                String varName = matcher.group(1);

                double varValue = variableProvider.getValueFor(varName);

                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(varValue)));
            }
            matcher.appendTail(sb);
            expression = sb.toString();

            return parseAndEvaluate(expression);

        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving cross-reference or evaluating formula '" + formula + "'", e);
        }
    }

    private static double parseAndEvaluate(String expression) {
        char[] tokens = expression.toCharArray();
        Stack<Double> values = new Stack<>();
        Stack<Character> operators = new Stack<>();

        for (int i = 0; i < tokens.length; i++) {
            if ((tokens[i] >= '0' && tokens[i] <= '9') || tokens[i] == '.' ||
                    (tokens[i] == '-' && (i == 0 || tokens[i - 1] == '('))) {

                StringBuilder sb = new StringBuilder();
                if (tokens[i] == '-') {
                    sb.append('-');
                    i++;
                }
                while (i < tokens.length && ((tokens[i] >= '0' && tokens[i] <= '9') || tokens[i] == '.')) {
                    sb.append(tokens[i++]);
                }
                i--;
                values.push(Double.parseDouble(sb.toString()));
            } else if (tokens[i] == '(') {
                operators.push(tokens[i]);
            } else if (tokens[i] == ')') {
                while (operators.peek() != '(') {
                    values.push(applyOperator(operators.pop(), values.pop(), values.pop()));
                }
                operators.pop();
            } else if (tokens[i] == '+' || tokens[i] == '-' || tokens[i] == '*' || tokens[i] == '/' || tokens[i] == '%') {
                while (!operators.empty() && hasPrecedence(tokens[i], operators.peek())) {
                    values.push(applyOperator(operators.pop(), values.pop(), values.pop()));
                }
                operators.push(tokens[i]);
            }
        }

        while (!operators.empty()) {
            values.push(applyOperator(operators.pop(), values.pop(), values.pop()));
        }

        return values.pop();
    }

    private static boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        return (op1 != '*' && op1 != '/' && op1 != '%') || (op2 != '+' && op2 != '-');
    }

    private static double applyOperator(char op, double b, double a) {
        return switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> {
                if (b == 0) throw new UnsupportedOperationException("Division by zero");
                yield a / b;
            }
            case '%' -> a % b;
            default -> 0;
        };
    }
}