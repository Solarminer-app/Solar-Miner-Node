package de.verdox.pv_miner.frontend.components;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.charts.Chart;
import com.vaadin.flow.component.charts.model.*;
import com.vaadin.flow.component.charts.model.style.SolidColor;
import com.vaadin.flow.data.provider.DataProvider;
import de.verdox.pv_miner.entity.QueryEntity;
import de.verdox.pv_miner.influx.QueryResult;
import de.verdox.pv_miner.statistic.live.EntityStatistic;
import de.verdox.pv_miner.frontend.FrontendService;
import reactor.core.Disposable;

import java.util.*;

public class InfluxChart extends Chart {
    private final Set<EntityStatisticSeries<?>> statisticSeries = new HashSet<>();
    private final Configuration configuration;
    private final RangeSelector rangeSelector;

    public InfluxChart() {
        configuration = getConfiguration();

        Time time = new Time();
        time.setTimezoneOffset(-60);
        configuration.setTime(time);

        XAxis xAxis = configuration.getxAxis();
        xAxis.setType(AxisType.DATETIME);
        xAxis.setTickPixelInterval(1);

        configuration.getScrollbar().setEnabled(false);

        rangeSelector = new RangeSelector();
        rangeSelector.setEnabled(true);

        configuration.setRangeSelector(rangeSelector);

        configuration.getChart().setZoomType(Dimension.X);
        configuration.getChart().setAnimation(false);
        setTimeline(false);

        Tooltip tooltip = new Tooltip();
        tooltip.setEnabled(true);
        configuration.setTooltip(tooltip);
    }

    public RangeSelector getRangeSelector() {
        return rangeSelector;
    }

    public void applyDarkTheme() {
        getStyle()
                .set("background-color", "#141416")
                .set("border", "1px solid #222226")
                .set("border-radius", "4px")
                .set("padding", "10px");

        configuration.getChart().setBackgroundColor(new SolidColor(0, 0, 0, 0));

        XAxis xAxis = configuration.getxAxis();
        xAxis.getLabels().getStyle().setColor(new SolidColor("#8a8a93"));
        xAxis.setLineColor(new SolidColor("#222226"));
        xAxis.setTickColor(new SolidColor("#222226"));

        YAxis yAxis = configuration.getyAxis();
        yAxis.getTitle().getStyle().setColor(new SolidColor("#8a8a93"));
        yAxis.getLabels().getStyle().setColor(new SolidColor("#8a8a93"));
        yAxis.setGridLineColor(new SolidColor("#222226"));

        Tooltip tooltip = configuration.getTooltip();
        tooltip.setBackgroundColor(new SolidColor("#141416"));
        tooltip.getStyle().setColor(new SolidColor("#ffffff"));
        tooltip.setBorderColor(new SolidColor("#222226"));

        Legend legend = configuration.getLegend();
        legend.getItemStyle().setColor(new SolidColor("#8a8a93"));
        legend.getItemHoverStyle().setColor(new SolidColor("#ffffff"));

        Navigator navigator = configuration.getNavigator();
        navigator.setOutlineColor(new SolidColor("#222226"));
        navigator.setMaskFill(new SolidColor(255, 255, 255, 0.1));

        XAxis navXAxis = navigator.getXAxis();
        navXAxis.getLabels().getStyle().setColor(new SolidColor("#8a8a93"));
        navXAxis.setGridLineColor(new SolidColor("#222226"));

        drawChart();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        for (EntityStatisticSeries<?> statisticSeries : statisticSeries) {
            statisticSeries.start();
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);

        for (EntityStatisticSeries<?> StatisticSeries : statisticSeries) {
            StatisticSeries.close();
        }
    }

    public class EntityStatisticSeries<T> extends DataProviderSeries<EntityStatisticSeries.StatisticPoint<T>> {
        private transient final EntityStatistic<?, ?, T> statistic;
        private transient Disposable update;

        public EntityStatisticSeries(String name, EntityStatistic<?, ?, T> statistic) {
            super(DataProvider.fromCallbacks(
                    query -> statistic.getValues().stream()
                            .map(entry -> new StatisticPoint<>(entry.getKey(), entry.getValue())),
                    query -> statistic.getValues().size()
            ));

            this.statistic = statistic;

            setX(StatisticPoint::x);
            setY(StatisticPoint::y);
            setName(name);
        }

        public void start() {
            close();
            update = statistic.subscribe(aLong -> FrontendService.scheduleUpdate(getUI(), ui -> updateSeries()));
        }

        public void close() {
            if (update != null) {
                update.dispose();
                update = null;
            }
        }


        public record StatisticPoint<V>(Long x, V y) {
        }
    }

    public <D, B extends QueryEntity<Q>, Q extends QueryResult> void createStatisticSeries(String name, EntityStatistic<B, Q, D> statistic) {
        EntityStatisticSeries<D> series = new EntityStatisticSeries<>(name, statistic);
        getConfiguration().addSeries(series);
        statisticSeries.add(series);
        series.start();
    }
}
