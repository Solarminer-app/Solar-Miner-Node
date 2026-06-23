package de.verdox.pv_miner.miningpool;

import de.verdox.pv_miner.entity.AbstractAuditableEntity;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Entity
@Table(name = "mining_pools")
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
public abstract class MiningPoolEntity<T extends MiningPoolData> extends AbstractAuditableEntity implements QueryEntity<T> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String userNameOfAccount;

    @Transient
    public abstract String getUrlIdentifier();

    @Transient
    public abstract String getStratumV1Url();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parentEntity")
    private PVSiteEntity parentEntity;

    public PVSiteEntity getParentEntity() {
        return parentEntity;
    }

    public void setParentEntity(PVSiteEntity parentEntity) {
        this.parentEntity = parentEntity;
    }

    public void setUserNameOfAccount(String userNameOfAccount) {
        this.userNameOfAccount = userNameOfAccount;
    }

    public String getUserNameOfAccount() {
        return userNameOfAccount;
    }
}
