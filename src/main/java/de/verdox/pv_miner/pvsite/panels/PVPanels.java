package de.verdox.pv_miner.pvsite.panels;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class PVPanels {
    @Setter
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pv_site_id", nullable = false)
    private PVSiteEntity parentSite;

    private String groupName;

    private double latitudeDeg;

    private double longitudeDeg;

    private int panelHeight;

    private double panelAzimuthDegree;

    private double panelSlopeDeg;

    private double powerPerPanelInWatts;

    private int amountOfPanels;

    public double getMaxPowerInKw() {
        return amountOfPanels * powerPerPanelInWatts / 1000;
    }
}
