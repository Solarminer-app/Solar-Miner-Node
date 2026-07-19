package de.verdox.pv_miner.dto;

import java.util.List;

public record ClusterConfigDto(
        String name,
        boolean existing,
        List<OperatingModeDto> modes,
        ClusterDslOptionsDto options
) {
    public record OperatingModeDto(
            String name,
            ConditionDto startCondition,
            ConditionDto stopCondition,
            List<ControllerActionDto> actions,
            long minRunTimeMinutes,
            long minIdleTimeMinutes,
            long powerChangeLockTimeMinutes
    ) {
    }

    public record ConditionDto(
            String type,
            String operator,
            List<ConditionDto> subConditions,
            String variable,
            String aggregation,
            Integer windowValue,
            String windowUnit,
            String comparator,
            Double threshold
    ) {
    }

    public record ControllerActionDto(
            String actionType,
            String targetType,
            String strategy,
            ValueExpressionDto valueExpression,
            int stepSizeWatts
    ) {
    }

    public record ValueExpressionDto(
            String type,
            Double constantWatts,
            String variable,
            String aggregation,
            Integer windowValue,
            String windowUnit,
            Double multiplier,
            Double offset,
            Double percentage
    ) {
    }

    public record ClusterDslOptionsDto(
            List<String> variables,
            List<String> comparators,
            List<String> logicalOperators,
            List<String> actionTypes,
            List<String> targetTypes,
            List<String> distributionStrategies,
            List<String> aggregations,
            List<String> timeUnits,
            List<String> expressionTypes,
            List<SimulationPresetDto> simulationPresets
    ) {
    }

    public record SimulationPresetDto(String id, String labelKey, String descriptionKey) {
    }
}
