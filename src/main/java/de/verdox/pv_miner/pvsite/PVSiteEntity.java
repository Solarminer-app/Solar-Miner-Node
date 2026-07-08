package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner.pvsite.panels.PVPanels;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;

import de.verdox.pv_miner.util.Money;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "pv_sites")
@Inheritance(strategy = InheritanceType.JOINED)
public class PVSiteEntity extends AbstractAuditableEntity implements QueryEntity<PVSiteDataDTO> {

    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Getter
    @Column(name = "timezone_id")
    private String timezoneId;

    public java.time.ZoneId getZoneId() {
        return timezoneId != null ? java.time.ZoneId.of(timezoneId) : java.time.ZoneId.systemDefault();
    }

    @Setter
    @Getter
    private String name;

    @Setter
    @Getter
    private int batteryCapacityWh;

    @Setter
    @Getter
    @Column(name = "setup_date", nullable = false)
    @ColumnDefault("'2000-01-01'")
    private LocalDate setupDate = LocalDate.now();

    @Setter
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "pvCostAmount")),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "pvCostCurrency"))
    })
    private Money pvCost;

    @Setter
    @Getter
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pv_site_feed_in_tariffs", joinColumns = @JoinColumn(name = "pv_site_id"))
    @OrderBy("validFrom DESC")
    private List<HistoricalPrice> feedInTariffHistory = new ArrayList<>();

    @Setter
    @Getter
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pv_site_electricity_prices", joinColumns = @JoinColumn(name = "pv_site_id"))
    @OrderBy("validFrom DESC")
    private List<HistoricalPrice> electricityPriceHistory = new ArrayList<>();

    @Setter
    @Getter
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pv_site_btc_sales", joinColumns = @JoinColumn(name = "pv_site_id"))
    @OrderBy("saleDate DESC")
    private List<BitcoinSale> bitcoinSales = new ArrayList<>();

    @Setter
    @Getter
    @OneToMany(mappedBy = "parentEntity", fetch = FetchType.EAGER)
    private Set<MinerEntity<?>> miners = new LinkedHashSet<>();

    @Setter
    @Getter
    @OneToMany(mappedBy = "parentEntity", fetch = FetchType.EAGER)
    private Set<MiningPoolEntity<?>> connectedMiningPools = new LinkedHashSet<>();

    @Setter
    @Getter
    @OneToMany(mappedBy = "parentSite", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PVPanels> pvPanels = new LinkedHashSet<>();

    @Setter
    @Getter
    @OneToMany(mappedBy = "parentSite", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BatteryEntity> batteries = new LinkedHashSet<>();

    @Setter
    @Getter
    @OneToMany(mappedBy = "parentSite", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InverterEntity> inverters = new LinkedHashSet<>();

    @Setter
    @Getter
    @OneToMany(mappedBy = "parentSite", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SmartMeterEntity> smartMeters = new LinkedHashSet<>();

    public Money getCurrentFeedInTariff() {
        return getCurrentPriceFromHistory(feedInTariffHistory);
    }

    public Money getCurrentElectricityPrice() {
        return getCurrentPriceFromHistory(electricityPriceHistory);
    }

    private Money getCurrentPriceFromHistory(List<HistoricalPrice> history) {
        if (history == null || history.isEmpty()) {
            return new Money(0.0, CustomCurrency.getInstance("EUR"));
        }
        LocalDate today = LocalDate.now();
        return history.stream()
                .filter(hp -> !hp.getValidFrom().isAfter(today))
                .findFirst()
                .map(HistoricalPrice::getPrice)
                .orElse(history.getLast().getPrice());
    }

    public double getKwp() {
        return inverters.stream().flatMap(inverterEntity -> getPvPanels().stream()).mapToDouble(PVPanels::getMaxPowerInKw).sum();
    }

    public Money getPvCost() {
        return pvCost != null ? pvCost : new Money(0.0, CustomCurrency.getInstance("EUR"));
    }

}