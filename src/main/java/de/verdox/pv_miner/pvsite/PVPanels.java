package de.verdox.pv_miner.pvsite;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
public class PVPanels {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentEntity")
    private PVSiteEntity parentEntity;

    public PVSiteEntity getParentEntity() {
        return parentEntity;
    }

    public void setParentEntity(PVSiteEntity parentEntity) {
        this.parentEntity = parentEntity;
    }

    private String groupName;
    private double latitudeDeg;
    private double longitudeDeg;
    private int panelHeight;
    private double panelAzimuthDegree;
    private double panelSlopeDeg;
    private double powerPerPanelInWatts;
    private int amountOfPanels;

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public double getMaxPowerInKw() {
        return amountOfPanels * powerPerPanelInWatts / 1000;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public double getLatitudeDeg() {
        return latitudeDeg;
    }

    public void setLatitudeDeg(double latitudeDeg) {
        this.latitudeDeg = latitudeDeg;
    }

    public double getLongitudeDeg() {
        return longitudeDeg;
    }

    public void setLongitudeDeg(double longitudeDeg) {
        this.longitudeDeg = longitudeDeg;
    }

    public int getPanelHeight() {
        return panelHeight;
    }

    public void setPanelHeight(int panelHeight) {
        this.panelHeight = panelHeight;
    }

    public double getPanelAzimuthDegree() {
        return panelAzimuthDegree;
    }

    public void setPanelAzimuthDegree(double panelAzimuthDegree) {
        this.panelAzimuthDegree = panelAzimuthDegree;
    }

    public double getPanelSlopeDeg() {
        return panelSlopeDeg;
    }

    public void setPanelSlopeDeg(double panelSlopeDeg) {
        this.panelSlopeDeg = panelSlopeDeg;
    }

    public double getPowerPerPanelInWatts() {
        return powerPerPanelInWatts;
    }

    public void setPowerPerPanelInWatts(double powerPerPanelInWatts) {
        this.powerPerPanelInWatts = powerPerPanelInWatts;
    }

    public int getAmountOfPanels() {
        return amountOfPanels;
    }

    public void setAmountOfPanels(int amountOfPanels) {
        this.amountOfPanels = amountOfPanels;
    }

}
