package de.verdox.pv_miner_extensions.inverter.smartfox;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteQueryStrategy;

import java.util.Map;
import java.util.logging.Level;

@Deprecated
public class SmartFoxQueryStrategy implements PVSiteQueryStrategy<SmartFoxEntity> {

    private final SmartFoxValuesQueryClient smartFoxValuesQueryClient = new SmartFoxValuesQueryClient();

    @Override
    public PVSiteDataDTO query(EntityQueryService entityQueryService, SmartFoxEntity entity) throws Exception {
        var variables = smartFoxValuesQueryClient.readSmartFoxValues(entity.getIpv4Host());

        var pvPower = parseDouble(SmartFoxValuesDataType.POWER_PRODUCTION, variables);
        var gridPower = parseDouble(SmartFoxValuesDataType.TO_GRID, variables);
        var batteryPower = parseDouble(SmartFoxValuesDataType.BATTERY_1_POWER, variables);
        var batterySoC = parseFloat(SmartFoxValuesDataType.BATTERY_SOC, variables) / 100;

        return createData(builder -> builder
                .pvPower(pvPower)
                .gridPower(gridPower)
                .batteryPower(batteryPower)
                .batterySoC(batterySoC)
                .totalMinerPowerKw(approximateMiningPowerDrawKw(entityQueryService, entity))
        );
    }

    @Override
    public void ping(SmartFoxEntity entity) throws Throwable {
        smartFoxValuesQueryClient.readSmartFoxValues(entity.getIpv4Host());
    }

    private float parseFloat(SmartFoxValuesDataType smartFoxValuesDataType, Map<SmartFoxValuesDataType, String> variables) {
        String valueToParse = variables.getOrDefault(smartFoxValuesDataType, "0");
        valueToParse = valueToParse.replaceAll("[^0-9,.-]", "");
        valueToParse = valueToParse.replace(",", ".");
        try {
            return Float.parseFloat(valueToParse);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "An error occured while trying to parse the float value " + valueToParse + " (" + smartFoxValuesDataType.name() + ")", e);
            return 0;
        }
    }

    private double parseDouble(SmartFoxValuesDataType smartFoxValuesDataType, Map<SmartFoxValuesDataType, String> variables) {
        String valueToParse = variables.getOrDefault(smartFoxValuesDataType, "0");
        valueToParse = valueToParse.replaceAll("[^0-9,.-]", "");
        valueToParse = valueToParse.replace(",", ".");
        try {
            return Double.parseDouble(valueToParse);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "An error occured while trying to parse the double value " + valueToParse + " (" + smartFoxValuesDataType.name() + ")", e);
            return 0;
        }
    }
}
