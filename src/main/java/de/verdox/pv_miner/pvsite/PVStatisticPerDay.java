package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.dailystatistic.DailyStatistic;

public class PVStatisticPerDay extends DailyStatistic {
    private double importKwh;
    private double exportKwh;
    private double productionKwh;
    private double consumptionKwh;
    private double consumptionKwhMining;

    public double getImportKwh() {
        return importKwh;
    }

    public void setImportKwh(double importKwh) {
        this.importKwh = importKwh;
    }

    public double getExportKwh() {
        return exportKwh;
    }

    public void setExportKwh(double exportKwh) {
        this.exportKwh = exportKwh;
    }

    public double getProductionKwh() {
        return productionKwh;
    }

    public void setProductionKwh(double productionKwh) {
        this.productionKwh = productionKwh;
    }

    public double getConsumptionKwh() {
        return consumptionKwh;
    }

    public void setConsumptionKwh(double consumptionKwh) {
        this.consumptionKwh = consumptionKwh;
    }

    public double getConsumptionKwhMining() {
        return consumptionKwhMining;
    }

    public PVStatisticPerDay setConsumptionKwhMining(double consumptionKwhMining) {
        this.consumptionKwhMining = consumptionKwhMining;
        return this;
    }

    @Override
    public String toString() {
        return "PVStatisticPerDay{" +
                "importKwh=" + importKwh +
                ", exportKwh=" + exportKwh +
                ", productionKwh=" + productionKwh +
                ", consumptionKwh=" + consumptionKwh +
                ", consumptionKwhMining=" + consumptionKwhMining +
                '}';
    }
}
