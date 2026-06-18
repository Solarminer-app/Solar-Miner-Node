package de.verdox.pv_miner.formula;

@FunctionalInterface
public interface VariableProvider {
    double getValueFor(String variableName) throws Exception;
}