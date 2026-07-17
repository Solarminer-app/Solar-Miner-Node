package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "mining_pools")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class MiningPoolEntity<T extends MiningPoolData> extends AbstractAuditableEntity implements QueryEntity<T> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter
    @Getter
    private UUID id;

    @Getter
    @Setter
    public String userNameOfAccount;

    @Transient
    public abstract String getUrlIdentifier();

    @Transient
    public abstract String getStratumV1Url();

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parentEntity")
    private PVSiteEntity parentEntity;

}
