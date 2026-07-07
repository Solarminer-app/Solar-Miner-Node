package de.verdox.pv_miner.miner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.ControllableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.UUID;


@Entity
@Table(name = "miners")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
@RegisterReflectionForBinding({MinerEntity.class})
public abstract class MinerEntity<MC extends MinerEntityController> extends AbstractAuditableEntity implements QueryEntity<MinerStats>, ControllableEntity<MC> {
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentEntity")
    private PVSiteEntity parentEntity;

    @Setter
    @Getter
    private String name;

    @Setter
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "minerCostAmount")),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "minerCostCurrency"))
    })
    private Money minerCost;

    @Setter
    @Getter
    @Column(name = "purchase_date", nullable = false)
    @ColumnDefault("'2000-01-01'")
    private LocalDate purchaseDate = LocalDate.now();

    @Setter
    @Getter
    private String currentMiningPoolTarget;

    @Setter
    @Getter
    public long minPowerTarget = 800;
    @Setter
    @Getter
    public long maxPowerTarget = 2750;

    @Setter
    @Getter
    private String clusterName;

    @Transient
    @JsonIgnore
    public abstract String getIP();

    @Transient
    @JsonIgnore
    public abstract MiningOS getOS();

    @Setter
    @Getter
    @Column(name = "power_step_size_watts")
    private Integer powerStepSizeWatts;

    @Setter
    @Getter
    @Column(name = "min_run_time_minutes")
    private Integer minRunTimeMinutes;

    @Setter
    @Getter
    @Column(name = "power_change_lock_time_minutes")
    private Integer powerChangeLockTimeMinutes;

    @Setter
    @Getter
    @Column(name = "min_idle_time_minutes")
    private Integer minIdleTimeMinutes;

    @Transient
    @JsonIgnore
    public abstract MinerApiClient.MinerDetails getDetails();

    public Money getMinerCost() {
        return minerCost != null ? minerCost : new Money(0.0, CustomCurrency.getInstance("EUR"));
    }
}
