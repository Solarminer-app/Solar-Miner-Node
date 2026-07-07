package de.verdox.pv_miner.frontend.pvsite.mining;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.*;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.*;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;

import java.util.*;

@Route("miner-details/:siteId")
public class MinerDetailView extends VerticalLayout implements BeforeEnterObserver {
    private final MinerRepository minerRepository;
    private UUID siteId;
    private List<UUID> minerIds = new ArrayList<>();

    public MinerDetailView(MinerRepository minerRepository) {
        this.minerRepository = minerRepository;
        setSizeFull();
        getStyle().set("background-color", "#16161a").set("color", "#ffffff");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        this.siteId = UUID.fromString(event.getRouteParameters().get("siteId").orElseThrow());

        Location location = event.getLocation();
        QueryParameters queryParameters = location.getQueryParameters();
        Map<String, List<String>> parametersMap = queryParameters.getParameters();

        if (parametersMap.containsKey("miners")) {
            String[] ids = parametersMap.get("miners").getFirst().split(",");
            this.minerIds = Arrays.stream(ids).map(UUID::fromString).toList();
        }

        buildUI();
    }

    private void buildUI() {
        removeAll();
        add(new H2("Detaillierte Miner-Analyse"));

        HorizontalLayout cardsLayout = new HorizontalLayout();
        cardsLayout.setWidthFull();

        for (UUID minerId : minerIds) {
            var minerOpt = minerRepository.findById(minerId);
            if (minerOpt.isEmpty()) continue;
            MinerEntity<?> miner = minerOpt.get();

            VerticalLayout minerCard = new VerticalLayout();
            minerCard.getStyle()
                    .set("background-color", "#1e1e22")
                    .set("border-radius", "8px")
                    .set("padding", "20px")
                    .set("border", "1px solid #222226");

            minerCard.add(new Span("Miner: " + miner.getName() + " (" + miner.getOS() + ")"));

            minerCard.add(createMinerEfficiencyChart(miner));

            cardsLayout.add(minerCard);
        }
        add(cardsLayout);
    }

    private Component createMinerEfficiencyChart(MinerEntity<?> miner) {
        Chart chart = new Chart(ChartType.SCATTER);
        Configuration conf = chart.getConfiguration();
        conf.setTitle("Effizienzkurve (J/TH vs. WattTarget)");

        XAxis xAxis = conf.getxAxis();
        xAxis.setTitle("Power Target (Watt)");

        YAxis yAxis = conf.getyAxis();
        yAxis.setTitle("Effizienz (J/TH)");

        DataSeries series = new DataSeries("Effizienz-Punkte");

        // Hier fragst du InfluxDB ab:
        // x-Wert = powerTargetWatts, y-Wert = efficiencyJTh (das Feld, das wir im vorherigen Schritt eingebaut haben!)
        // series.add(new DataSeriesItem(powerTarget, efficiency));

        conf.addSeries(series);
        chart.getStyle().set("max-height", "300px");
        return chart;
    }
}
