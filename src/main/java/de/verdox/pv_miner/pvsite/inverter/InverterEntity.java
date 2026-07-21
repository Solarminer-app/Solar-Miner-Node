package de.verdox.pv_miner.pvsite.inverter;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "inverters")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class InverterEntity extends AbstractAuditableEntity implements QueryEntity<InverterDataDTO>  {

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
}
