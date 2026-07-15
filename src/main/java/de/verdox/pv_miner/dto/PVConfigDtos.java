package de.verdox.pv_miner.dto;

import java.util.List;
import java.util.Map;

public final class PVConfigDtos {
    private PVConfigDtos() {
    }

    public record FieldDefinitionDto(String field, String unit) {
    }

    public record TemplateDto(String id, String name, List<FieldDefinitionDto> fields) {
    }

    public record CreateProfileRequest(String name, String templateId) {
    }

    public record RestCatalogDto(
            List<TemplateDto> templates,
            List<String> localProfiles,
            List<String> communityProfiles,
            List<String> httpMethods,
            List<String> responseTypes,
            List<String> parameterTypes
    ) {
    }

    public record RestProfileDto(String name, String templateId, List<RestFieldDto> fields) {
    }

    public record RestFieldDto(
            String field,
            String unit,
            String urlExtension,
            String httpMethod,
            String responseType,
            String dataPath,
            double scaleFactor,
            String formula,
            String parameterType
    ) {
    }

    public record RestTestRequest(String baseUrl, String apiToken, List<RestFieldDto> fields) {
    }

    public record ModbusCatalogDto(
            List<TemplateDto> templates,
            List<String> localProfiles,
            List<String> communityProfiles,
            List<String> parameterTypes,
            List<OperationTypeDto> operationTypes,
            List<String> byteOrders
    ) {
    }

    public record OperationTypeDto(String value, String label) {
    }

    public record ModbusProfileDto(
            String name,
            String templateId,
            ModbusFingerprintDto fingerprint,
            List<ModbusFieldDto> fields
    ) {
    }

    public record ModbusFingerprintDto(
            Integer address,
            Integer size,
            String parameterType,
            String operationType,
            String byteOrder,
            String expectedValue
    ) {
    }

    public record ModbusFieldDto(
            String field,
            String unit,
            int startAddress,
            int size,
            double scaleFactor,
            String formula,
            String parameterType,
            String operationType,
            String byteOrder
    ) {
    }

    public record ModbusTestRequest(
            String host,
            int port,
            int slaveId,
            ModbusFingerprintDto fingerprint,
            List<ModbusFieldDto> fields
    ) {
    }

    public record ConnectionTestDto(
            boolean connected,
            boolean fingerprintMatches,
            String message,
            Map<String, FieldTestDto> fields
    ) {
    }

    public record FieldTestDto(Double value, String textValue, String errorCode) {
        public static FieldTestDto number(double value) {
            return new FieldTestDto(value, null, null);
        }

        public static FieldTestDto text(String value) {
            return new FieldTestDto(null, value, null);
        }

        public static FieldTestDto error(String errorCode) {
            return new FieldTestDto(null, null, errorCode);
        }
    }
}
