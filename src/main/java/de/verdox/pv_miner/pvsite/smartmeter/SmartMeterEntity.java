package de.verdox.pv_miner.pvsite.smartmeter;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "smart_meters")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class SmartMeterEntity extends AbstractAuditableEntity implements QueryEntity<SmartMeterDataDTO> {

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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pv_site_id", nullable = false)
    private PVSiteEntity parentSite;

    @Getter
    @Setter
    @Column(name = "is_grid_meter", nullable = false)
    private boolean isGridMeter = true;

}
