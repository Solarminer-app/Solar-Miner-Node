package de.verdox.pv_miner_extensions.pools.nicehash;

import de.verdox.pv_miner.miningpool.MiningPoolData;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import java.util.List;

@Entity
public class NiceHashPoolEntity extends MiningPoolEntity<NiceHashPoolEntity.NicehashWorkerStats> {

    private String apiKey;
    private String secret;
    private String orgId;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    @Transient
    @Override
    public String getUrlIdentifier() {
        return "nicehash.com";
    }

    @Override
    public String getStratumV1Url() {
        return "stratum+tcp://sha256.eu.nicehash.com:3334";
    }

    public record NicehashWorkerStats(int payoutThreshold, List<UnpaidAmount> workers, List<Payout> paidAmounts) implements MiningPoolData {

        @Override
        public boolean containsWorkerWithName(String workerName) {
            return workers.stream().anyMatch(unpaidAmount -> unpaidAmount.workerName().equals(workerName));
        }

        @Override
        public double calculateSatoshiRewardToday(String workerName) {
            return 0;
        }
    }

    public record UnpaidAmount(String workerName, int unpaidSatoshis) {}

    public record Payout(long timestamp, int paidSatoshis) {}
}
