package de.verdox.pv_miner.pvsite.battery;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "batteries")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class BatteryEntity extends AbstractAuditableEntity {
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inverter_id", nullable = false)
    private InverterEntity parentInverter;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    @Column(name = "nominal_capacity_wh")
    private int nominalCapacityWh;

    @Getter
    @Setter
    @Column(name = "max_charge_power_w")
    private int maxChargePowerW;

    @Getter
    @Setter
    @Column(name = "max_discharge_power_w")
    private int maxDischargePowerW;
}
