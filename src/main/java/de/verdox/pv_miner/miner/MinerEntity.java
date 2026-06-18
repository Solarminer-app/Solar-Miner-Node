package de.verdox.pv_miner.miner;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.ControllableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerControllerConfigStorage;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.Money;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;


@Entity
@Table(name = "miners")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
@RegisterReflectionForBinding({MinerEntity.class})
public abstract class MinerEntity<MC extends Miner> extends AbstractAuditableEntity implements QueryEntity<MinerStats>, ControllableEntity<MC> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentEntity")
    private PVSiteEntity parentEntity;

    private String name;

    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "minerCostAmount")),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "minerCostCurrency"))
    })
    private Money minerCost;

    @Column(name = "purchase_date", nullable = false)
    @ColumnDefault("'2000-01-01'")
    private LocalDate purchaseDate = LocalDate.now();

    private String currentMiningPoolTarget;

    public void setCurrentMiningPoolTarget(String currentMiningPoolTarget) {
        this.currentMiningPoolTarget = currentMiningPoolTarget;
    }

    public String getCurrentMiningPoolTarget() {
        return currentMiningPoolTarget;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public Money getMinerCost() {
        return minerCost != null ? minerCost : new Money(0.0, CustomCurrency.getInstance("EUR"));
    }

    public long minPowerTarget = 800;
    public long maxPowerTarget = 2750;

    public long getMinPowerTarget() {
        return minPowerTarget;
    }

    public void setMinPowerTarget(long minPowerTarget) {
        this.minPowerTarget = minPowerTarget;
    }

    public long getMaxPowerTarget() {
        return maxPowerTarget;
    }

    public void setMaxPowerTarget(long maxPowerTarget) {
        this.maxPowerTarget = maxPowerTarget;
    }

    public void setMinerCost(Money minerCost) {
        this.minerCost = minerCost;
    }

    private String clusterName;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public abstract String getIP();

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public PVSiteEntity getParentEntity() {
        return parentEntity;
    }

    public void setParentEntity(PVSiteEntity parentEntity) {
        this.parentEntity = parentEntity;
    }

    public void tryStartMinerControllers(MinerControllerConfigStorage storage) throws IOException {

    }

    public void tryStopMinerControllers() {
    }

}
