package de.verdox.solarminer.formula;

@FunctionalInterface
public interface VariableProvider {
    double getValueFor(String variableName) throws Exception;
}