package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.*;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import com.vaadin.flow.component.html.Div;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ControllerDashboardChart extends Div {
    private final Chart automationChart = new Chart(ChartType.LINE);
    private final MinerClusterService clusterService;
    private final DataSeries automationPowerSeries;
    private final DataSeries automationAllocatedSeries;
    private final DataSeries automationFlagsSeries;

    public ControllerDashboardChart(MinerClusterService clusterService) {
        this.clusterService = clusterService;
        Configuration autoConf = automationChart.getConfiguration();
        autoConf.getChart().setAnimation(false);
        autoConf.setTitle("Mining-Controller");

        XAxis autoX = autoConf.getxAxis();
        autoX.setType(AxisType.DATETIME);

        applyDarkThemeToChart();

        YAxis autoY = autoConf.getyAxis();
        autoY.setMin(0);
        autoY.setTitle("Controller Power (W)");

        automationPowerSeries = new DataSeries("Target Power");
        PlotOptionsLine targetOptions = new PlotOptionsLine();
        targetOptions.setStep(StepType.LEFT);
        automationPowerSeries.setPlotOptions(targetOptions);

        automationAllocatedSeries = new DataSeries("Allocated Power");
        PlotOptionsLine allocatedOptions = new PlotOptionsLine();
        allocatedOptions.setDashStyle(DashStyle.SHORTDOT);
        allocatedOptions.setStep(StepType.LEFT);
        automationAllocatedSeries.setPlotOptions(allocatedOptions);

        automationFlagsSeries = new DataSeries("Events");
        automationFlagsSeries.setPlotOptions(new PlotOptionsFlags());

        autoConf.addSeries(automationPowerSeries);
        autoConf.addSeries(automationAllocatedSeries);
        autoConf.addSeries(automationFlagsSeries);
        automationChart.setWidth("98.5%");
        automationChart.getStyle().set("margin-top", "15px");
        add(automationChart);
    }

    public void update(PVSiteEntity pvSiteEntity, String clusterName) {
        if (clusterName == null || clusterName.isBlank()) return;

        MinerClusterService.ClusterInstance cluster = clusterService.getCluster(pvSiteEntity.getId(), clusterName);
        if (cluster == null || !cluster.isRunning()) return;

        // Hole die Daten vom Backend
        List<MinerClusterService.ClusterInstance.ClusterStateSnapshot> history = cluster.getHistory();

        List<DataSeriesItem> powerItems = new ArrayList<>();
        List<DataSeriesItem> allocatedItems = new ArrayList<>();
        List<DataSeriesItem> flagItems = new ArrayList<>();

        for (var snapshot : history) {
            long time = snapshot.timestamp().toEpochMilli();

            DataSeriesItem powerItem = new DataSeriesItem(time, snapshot.targetPowerWatts());
            powerItem.setName(snapshot.activeModeName());
            powerItems.add(powerItem);

            DataSeriesItem allocatedItem = new DataSeriesItem(time, snapshot.allocatedPowerWatts());
            allocatedItem.setName("Active Power");
            allocatedItems.add(allocatedItem);

            if (snapshot.eventDescription() != null && !snapshot.eventDescription().isBlank()) {
                FlagItem flag = createFlagForSnapshot(snapshot, time);
                flagItems.add(flag);
            }
        }

        if (!history.isEmpty()) {
            var lastSnapshot = history.get(history.size() - 1);
            long now = Instant.now().toEpochMilli();

            DataSeriesItem currentPower = new DataSeriesItem(now, lastSnapshot.targetPowerWatts());
            currentPower.setName(lastSnapshot.activeModeName());
            powerItems.add(currentPower);

            DataSeriesItem currentAlloc = new DataSeriesItem(now, lastSnapshot.allocatedPowerWatts());
            currentAlloc.setName("Active Allocation");
            allocatedItems.add(currentAlloc);
        }

        automationPowerSeries.setData(powerItems);
        automationAllocatedSeries.setData(allocatedItems);
        automationFlagsSeries.setData(flagItems);

        automationPowerSeries.updateSeries();
        automationAllocatedSeries.updateSeries();
        automationFlagsSeries.updateSeries();
    }

    private @NonNull FlagItem createFlagForSnapshot(MinerClusterService.ClusterInstance.ClusterStateSnapshot snapshot, long time) {
        FlagItem flag = new FlagItem(time, "!");
        flag.setX(time);
        String desc = snapshot.eventDescription().toLowerCase();

        // Checkt nun exakt auf die Aktionen des ClusterControllers (Pause, Start, Regelung)
        if (desc.contains("▶ start") || desc.contains("resume")) {
            flag.setTitle("▶ Start");
            flag.setColor(new SolidColor("#2ecc71"));
        } else if (desc.contains("⏸ stop") || desc.contains("pause")) {
            flag.setTitle("⏸ Stop");
            flag.setColor(new SolidColor("#e74c3c"));
        } else if (desc.contains("⚡ regelung")) {
            flag.setTitle("⚡ Scale");
            flag.setColor(new SolidColor("#3498db"));
        } else if (desc.contains("⚠️ state-lock")) {
            flag.setTitle("⚠️ Throttle");
            flag.setColor(new SolidColor("#f39c12"));
        } else {
            flag.setTitle("ℹ️ Event");
            flag.setColor(new SolidColor("#95a5a6"));
        }

        flag.setText(snapshot.eventDescription() + "<br><b>Modus:</b> " + snapshot.activeModeName());
        return flag;
    }

    private void applyDarkThemeToChart() {
        automationChart.getStyle()
                .set("background-color", "#141416")
                .set("border", "1px solid #222226")
                .set("border-radius", "4px")
                .set("padding", "10px");

        Configuration conf = automationChart.getConfiguration();
        conf.getChart().setBackgroundColor(new SolidColor(0, 0, 0, 0));

        XAxis xAxis = conf.getxAxis();
        xAxis.getLabels().getStyle().setColor(new SolidColor("#8a8a93"));
        xAxis.setLineColor(new SolidColor("#222226"));
        xAxis.setTickColor(new SolidColor("#222226"));

        YAxis yAxis = conf.getyAxis();
        yAxis.getTitle().getStyle().setColor(new SolidColor("#8a8a93"));
        yAxis.getLabels().getStyle().setColor(new SolidColor("#8a8a93"));
        yAxis.setGridLineColor(new SolidColor("#222226"));

        Tooltip tooltip = conf.getTooltip();
        tooltip.setBackgroundColor(new SolidColor("#141416"));
        tooltip.getStyle().setColor(new SolidColor("#ffffff"));
        tooltip.setBorderColor(new SolidColor("#222226"));

        Legend legend = conf.getLegend();
        legend.getItemStyle().setColor(new SolidColor("#8a8a93"));
        legend.getItemHoverStyle().setColor(new SolidColor("#ffffff"));
    }
}