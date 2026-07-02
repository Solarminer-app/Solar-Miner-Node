package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.influx.QueryResult;

import java.util.Objects;

public final class PVSiteDataDTO implements QueryResult {

    public static PVSiteDataDTO createDefault() {
        return new PVSiteDataDTO();
    }

    static Builder builder() {
        return new Builder();
    }

    private final double pvPower;
    private final double loadPowerKw;
    private final double totalMinerPowerKw;
    private final double importPowerKw;
    private final double exportPowerKw;
    private final double batteryChargePower;
    private final double batteryDischargePower;
    private final float batterySoC;

    PVSiteDataDTO(
            double pvPower,
            double loadPowerKw,
            double totalMinerPowerKw,
            double importPowerKw,
            double exportPowerKw,
            double batteryChargePower,
            double batteryDischargePower,
            float batterySoC
    ) {
        this.pvPower = pvPower;
        this.loadPowerKw = loadPowerKw;
        this.totalMinerPowerKw = totalMinerPowerKw;
        this.importPowerKw = importPowerKw;
        this.exportPowerKw = exportPowerKw;
        this.batteryChargePower = batteryChargePower;
        this.batteryDischargePower = batteryDischargePower;
        this.batterySoC = batterySoC;
    }

    private PVSiteDataDTO() {
        this(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public double getPVPowerInKw() {
        return pvPower;
    }

    public double getTotalMinerPowerKw() {
        return totalMinerPowerKw;
    }

    public double getImportInKw() {
        return importPowerKw;
    }

    public double getExportInKw() {
        return exportPowerKw;
    }

    public double getBatteryChargePower() {
        return batteryChargePower;
    }

    public double getBatteryDischargePower() {
        return batteryDischargePower;
    }

    public double getBatteryPower() {
        if (batteryChargePower > 0) {
            return batteryChargePower;
        }
        if (batteryDischargePower > 0) {
            return -batteryDischargePower;
        }
        return 0;
    }

    /**
     * @return the battery state of charge [0;100]
     */
    public float getBatteryStateOfCharge() {
        return batterySoC;
    }

    /**
     * Errechnet die aktuelle Hauslast basierend auf den reinen Leistungswerten.
     */
    public double getLoadsPowerInKw() {
        return loadPowerKw;
    }

    @Override
    public String toString() {
        return "PVSiteDataDTO{" +
                "pvPower=" + pvPower +
                ", importPowerKw=" + importPowerKw +
                ", exportPowerKw=" + exportPowerKw +
                ", batteryChargePower=" + batteryChargePower +
                ", batteryDischargePower=" + batteryDischargePower +
                ", batterySoC=" + batterySoC +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PVSiteDataDTO that = (PVSiteDataDTO) o;
        return Double.compare(pvPower, that.pvPower) == 0 &&
                Double.compare(importPowerKw, that.importPowerKw) == 0 &&
                Double.compare(exportPowerKw, that.exportPowerKw) == 0 &&
                Double.compare(batteryChargePower, that.batteryChargePower) == 0 &&
                Double.compare(batteryDischargePower, that.batteryDischargePower) == 0 &&
                Float.compare(batterySoC, that.batterySoC) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pvPower, importPowerKw, exportPowerKw, batteryChargePower, batteryDischargePower, batterySoC);
    }

    public static final class Builder {
        private double pvPower;
        private double loadPowerKw;
        private double importPowerKw;
        private double exportPowerKw;
        private double batteryChargePower;
        private double batteryDischargePower;
        private double totalMinerPowerKw;
        private float batterySoC;

        private Builder() {
        }

        public Builder pvPower(double pvPower) {
            this.pvPower = pvPower;
            return this;
        }

        public Builder loadPowerKw(double loadPowerKw) {
            this.loadPowerKw = loadPowerKw;
            return this;
        }

        public Builder totalMinerPowerKw(double totalMinerPowerKw) {
            this.totalMinerPowerKw = totalMinerPowerKw;
            return this;
        }

        public Builder importPowerKw(double importPowerKw) {
            this.importPowerKw = importPowerKw;
            return this;
        }

        public Builder exportPowerKw(double exportPowerKw) {
            this.exportPowerKw = exportPowerKw;
            return this;
        }

        public Builder batteryChargePower(double batteryChargePower) {
            this.batteryChargePower = batteryChargePower;
            return this;
        }

        public Builder batteryDischargePower(double batteryDischargePower) {
            this.batteryDischargePower = batteryDischargePower;
            return this;
        }

        public Builder batterySoC(float batterySoC) {
            this.batterySoC = batterySoC;
            return this;
        }

        /**
         * Convenience-Methode für die alte gridPower-Logik
         */
        public Builder gridPower(double gridPower) {
            if (gridPower > 0.05) {
                this.importPowerKw = gridPower;
                this.exportPowerKw = 0;
            } else if (gridPower < -0.05) {
                this.importPowerKw = 0;
                this.exportPowerKw = Math.abs(gridPower);
            } else {
                this.importPowerKw = 0;
                this.exportPowerKw = 0;
            }
            return this;
        }

        /**
         * Convenience-Methode für die alte batteryPower-Logik
         */
        public Builder batteryPower(double batteryPower) {
            if (batteryPower > 0) {
                this.batteryChargePower = batteryPower;
                this.batteryDischargePower = 0;
            } else {
                this.batteryChargePower = 0;
                this.batteryDischargePower = Math.abs(batteryPower);
            }
            return this;
        }

        public PVSiteDataDTO build() {
            if (this.loadPowerKw == 0) {
                double totalGeneration = pvPower + importPowerKw + batteryDischargePower;
                double totalStorageOrExport = exportPowerKw + batteryChargePower;

                loadPowerKw = totalGeneration - totalStorageOrExport;
            }

            return new PVSiteDataDTO(
                    pvPower,
                    loadPowerKw,
                    totalMinerPowerKw,
                    importPowerKw,
                    exportPowerKw,
                    batteryChargePower,
                    batteryDischargePower,
                    batterySoC
            );
        }

        @Override
        public String toString() {
            return "Builder{" +
                    "pvPower=" + pvPower +
                    ", loadPowerKw=" + loadPowerKw +
                    ", importPowerKw=" + importPowerKw +
                    ", exportPowerKw=" + exportPowerKw +
                    ", batteryChargePower=" + batteryChargePower +
                    ", batteryDischargePower=" + batteryDischargePower +
                    ", batterySoC=" + batterySoC +
                    '}';
        }
    }
}