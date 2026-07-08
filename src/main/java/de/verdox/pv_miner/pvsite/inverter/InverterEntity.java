package de.verdox.pv_miner.pvsite.inverter;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.pvsite.PVPanels;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "inverters")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class InverterEntity extends AbstractAuditableEntity {

    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pv_site_id", nullable = false)
    private PVSiteEntity parentSite;

    @Getter
    @Setter
    @Column(name = "max_ac_output_power_w")
    private int maxAcOutputPowerW;

    @Getter
    @Setter
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Getter
    @Setter
    @OneToMany(mappedBy = "parentInverter", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private Set<PVPanels> connectedPVPanels = new LinkedHashSet<>();

    @Getter
    @Setter
    @OneToMany(mappedBy = "parentInverter", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private Set<BatteryEntity> connectedBatteries = new LinkedHashSet<>();
}
