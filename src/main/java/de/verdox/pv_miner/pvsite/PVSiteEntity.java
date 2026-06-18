package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;

import de.verdox.pv_miner.util.Money;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "pv_sites")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class PVSiteEntity extends AbstractAuditableEntity implements QueryEntity<PVSiteDataDTO> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private int batteryCapacityWh;

    @Column(name = "setup_date", nullable = false)
    @ColumnDefault("'2000-01-01'")
    private LocalDate setupDate = LocalDate.now();

    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "pvCostAmount")),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "pvCostCurrency"))
    })
    private Money pvCost;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pv_site_feed_in_tariffs", joinColumns = @JoinColumn(name = "pv_site_id"))
    @OrderBy("validFrom DESC")
    private List<HistoricalPrice> feedInTariffHistory = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pv_site_electricity_prices", joinColumns = @JoinColumn(name = "pv_site_id"))
    @OrderBy("validFrom DESC")
    private List<HistoricalPrice> electricityPriceHistory = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pv_site_btc_sales", joinColumns = @JoinColumn(name = "pv_site_id"))
    @OrderBy("saleDate DESC")
    private List<BitcoinSale> bitcoinSales = new ArrayList<>();

    public List<BitcoinSale> getBitcoinSales() {
        return bitcoinSales;
    }

    public void setBitcoinSales(List<BitcoinSale> bitcoinSales) {
        this.bitcoinSales = bitcoinSales;
    }

    @OneToMany(mappedBy = "parentEntity", fetch = FetchType.EAGER)
    private Set<MinerEntity<?>> miners = new LinkedHashSet<>();

    @OneToMany(mappedBy = "parentEntity", fetch = FetchType.EAGER)
    private Set<PVPanels> pvPanels = new LinkedHashSet<>();

    @OneToMany(mappedBy = "parentEntity", fetch = FetchType.EAGER)
    private Set<MiningPoolEntity<?>> connectedMiningPools = new LinkedHashSet<>();

    // --- Helper für aktuelle Preise ---
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
                .orElse(history.get(history.size() - 1).getPrice()); // Fallback zum ältesten
    }

    // --- Getter & Setter ---
    public List<HistoricalPrice> getFeedInTariffHistory() { return feedInTariffHistory; }
    public void setFeedInTariffHistory(List<HistoricalPrice> feedInTariffHistory) { this.feedInTariffHistory = feedInTariffHistory; }

    public List<HistoricalPrice> getElectricityPriceHistory() { return electricityPriceHistory; }
    public void setElectricityPriceHistory(List<HistoricalPrice> electricityPriceHistory) { this.electricityPriceHistory = electricityPriceHistory; }

    public LocalDate getSetupDate() { return setupDate; }
    public void setSetupDate(LocalDate setupDate) { this.setupDate = setupDate; }

    public double getKwp() {
        return pvPanels.stream().mapToDouble(PVPanels::getMaxPowerInKw).sum();
    }

    public Money getPvCost() {
        return pvCost != null ? pvCost : new Money(0.0, CustomCurrency.getInstance("EUR"));
    }

    public void setPvCost(Money pvCost) { this.pvCost = pvCost; }

    public int getBatteryCapacityWh() { return batteryCapacityWh; }
    public void setBatteryCapacityWh(int batteryCapacityWh) { this.batteryCapacityWh = batteryCapacityWh; }

    public Set<MinerEntity<?>> getMiners() { return miners; }
    public void setMiners(Set<MinerEntity<?>> getMiners) { this.miners = getMiners; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Set<MiningPoolEntity<?>> getConnectedMiningPools() { return connectedMiningPools; }
    public void setConnectedMiningPools(Set<MiningPoolEntity<?>> connectedMiningPools) { this.connectedMiningPools = connectedMiningPools; }

    public Set<PVPanels> getPvPanels() { return pvPanels; }
    public void setPvPanels(Set<PVPanels> pvPanels) { this.pvPanels = pvPanels; }
}