package name.abuchen.portfolio.ui.views.dashboard;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.swtchart.ISeries;

import name.abuchen.portfolio.model.ConfigurationSet;
import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.snapshot.Aggregation;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.util.LabelOnly;
import name.abuchen.portfolio.ui.util.SimpleAction;
import name.abuchen.portfolio.ui.util.chart.TimelineChart;
import name.abuchen.portfolio.ui.views.PerformanceChartView;
import name.abuchen.portfolio.ui.views.StatementOfAssetsHistoryView;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries;
import name.abuchen.portfolio.ui.views.dataseries.DataSeriesConfigurator;
import name.abuchen.portfolio.ui.views.dataseries.DataSeriesSerializer;
import name.abuchen.portfolio.ui.views.dataseries.DataSeriesSet;
import name.abuchen.portfolio.ui.views.dataseries.PerformanceChartSeriesBuilder;
import name.abuchen.portfolio.ui.views.dataseries.StatementOfAssetsSeriesBuilder;

public class ChartWidget extends WidgetDelegate
{
    private class ChartConfig implements WidgetConfig
    {
        private WidgetDelegate delegate;
        private ConfigurationSet configSet;
        private ConfigurationSet.Configuration config;

        public ChartConfig(WidgetDelegate delegate, DataSeries.UseCase useCase)
        {
            this.delegate = delegate;

            String configName = (useCase == DataSeries.UseCase.STATEMENT_OF_ASSETS ? StatementOfAssetsHistoryView.class
                            : PerformanceChartView.class).getSimpleName() + DataSeriesConfigurator.IDENTIFIER_POSTFIX;
            configSet = delegate.getClient().getSettings().getConfigurationSet(configName);
            String uuid = delegate.getWidget().getConfiguration().get(Dashboard.Config.CONFIG_UUID.name());
            config = configSet.lookup(uuid).orElseGet(() -> configSet.getConfigurations().findFirst()
                            .orElseGet(() -> new ConfigurationSet.Configuration(Messages.LabelNoName, null)));
        }

        @Override
        public void menuAboutToShow(IMenuManager manager)
        {
            manager.appendToGroup(DashboardView.INFO_MENU_GROUP_NAME,
                            new LabelOnly(config != null ? config.getName() : Messages.LabelNoName));

            MenuManager subMenu = new MenuManager(Messages.ClientEditorLabelChart);

            this.configSet.getConfigurations().forEach(c -> {
                SimpleAction action = new SimpleAction(c.getName(), a -> {
                    config = c;
                    delegate.getWidget().getConfiguration().put(Dashboard.Config.CONFIG_UUID.name(), c.getUUID());
                    delegate.getClient().markDirty();
                });
                action.setChecked(c.equals(config));
                subMenu.add(action);
            });

            manager.add(subMenu);
        }

        public String getData()
        {
            return config != null ? config.getData() : null;
        }

        @Override
        public String getLabel()
        {
            return Messages.ClientEditorLabelChart + ": " //$NON-NLS-1$
                            + (config != null ? config.getName() : Messages.LabelNoName);
        }
    }

    private class AggregationConfig implements WidgetConfig
    {
        private WidgetDelegate delegate;
        private Aggregation.Period aggregation;

        public AggregationConfig(WidgetDelegate delegate)
        {
            this.delegate = delegate;

            try
            {
                String code = delegate.getWidget().getConfiguration().get(Dashboard.Config.AGGREGATION.name());
                if (code != null)
                    this.aggregation = Aggregation.Period.valueOf(code);
            }
            catch (IllegalArgumentException ignore)
            {
                PortfolioPlugin.log(ignore);
            }
        }

        @Override
        public void menuAboutToShow(IMenuManager manager)
        {
            manager.appendToGroup(DashboardView.INFO_MENU_GROUP_NAME, new LabelOnly(
                            aggregation != null ? aggregation.toString() : Messages.LabelAggregationDaily));

            MenuManager subMenu = new MenuManager(Messages.LabelAggregation);

            Action action = new SimpleAction(Messages.LabelAggregationDaily, a -> {
                aggregation = null;
                delegate.getWidget().getConfiguration().remove(Dashboard.Config.AGGREGATION.name());
                delegate.getClient().markDirty();
            });
            action.setChecked(aggregation == null);
            subMenu.add(action);

            Arrays.stream(Aggregation.Period.values()).forEach(a -> {
                Action menu = new SimpleAction(a.toString(), x -> {
                    aggregation = a;
                    delegate.getWidget().getConfiguration().put(Dashboard.Config.AGGREGATION.name(), a.name());
                    delegate.getClient().markDirty();
                });
                menu.setChecked(aggregation == a);
                subMenu.add(menu);
            });

            manager.add(subMenu);
        }

        public Aggregation.Period getAggregation()
        {
            return aggregation;
        }

        @Override
        public String getLabel()
        {
            return Messages.LabelAggregation + ": " + //$NON-NLS-1$
                            (aggregation != null ? aggregation.toString() : Messages.LabelAggregationDaily);
        }
    }

    private DataSeries.UseCase useCase;
    private DataSeriesSet dataSeriesSet;

    private Label title;
    private TimelineChart chart;

    public ChartWidget(Widget widget, DashboardData dashboardData, DataSeries.UseCase useCase)
    {
        super(widget, dashboardData);

        this.useCase = useCase;
        this.dataSeriesSet = new DataSeriesSet(dashboardData.getClient(), useCase);

        addConfig(new ChartConfig(this, useCase));
        if (useCase == DataSeries.UseCase.PERFORMANCE)
            addConfig(new AggregationConfig(this));
        addConfig(new ReportingPeriodConfig(this));
    }

    @Override
    public Composite createControl(Composite parent, DashboardResources resources)
    {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(1).margins(5, 5).applyTo(container);
        container.setBackground(parent.getBackground());

        title = new Label(container, SWT.NONE);
        title.setText(getWidget().getLabel());
        GridDataFactory.fillDefaults().grab(true, false).applyTo(title);

        chart = new TimelineChart(container);
        chart.getTitle().setVisible(false);
        chart.getAxisSet().getYAxis(0).getTick().setVisible(false);
        if (useCase != DataSeries.UseCase.STATEMENT_OF_ASSETS)
            chart.getToolTip().setValueFormat(new DecimalFormat("0.##%")); //$NON-NLS-1$

        GC gc = new GC(container);
        gc.setFont(resources.getKpiFont());
        Point stringExtend = gc.stringExtent("X"); //$NON-NLS-1$
        gc.dispose();

        GridDataFactory.fillDefaults().hint(SWT.DEFAULT, stringExtend.y * 6).grab(true, false).applyTo(chart);

        container.layout();

        return container;
    }

    @Override
    Control getTitleControl()
    {
        return title;
    }

    @Override
    public void update()
    {
        title.setText(getWidget().getLabel());

        try
        {
            chart.suspendUpdate(true);

            for (ISeries s : chart.getSeriesSet().getSeries())
                chart.getSeriesSet().deleteSeries(s.getId());

            List<DataSeries> series = new DataSeriesSerializer().fromString(dataSeriesSet,
                            get(ChartConfig.class).getData());

            ReportingPeriod reportingPeriod = get(ReportingPeriodConfig.class).getReportingPeriod();

            switch (useCase)
            {
                case STATEMENT_OF_ASSETS:
                    buildAssetSeries(series, reportingPeriod);
                    break;
                case PERFORMANCE:
                    buildPerformanceSeries(series, reportingPeriod);
                    break;
                case RETURN_VOLATILITY:
                    throw new UnsupportedOperationException();
                default:
                    throw new IllegalArgumentException();
            }

            chart.adjustRange();
        }
        finally
        {
            chart.suspendUpdate(false);
        }
        chart.redraw();
    }

    private void buildAssetSeries(List<DataSeries> series, ReportingPeriod reportingPeriod)
    {
        StatementOfAssetsSeriesBuilder b1 = new StatementOfAssetsSeriesBuilder(chart,
                        getDashboardData().getDataSeriesCache());
        series.forEach(s -> b1.build(s, reportingPeriod));
    }

    private void buildPerformanceSeries(List<DataSeries> series, ReportingPeriod reportingPeriod)
    {
        PerformanceChartSeriesBuilder b2 = new PerformanceChartSeriesBuilder(chart,
                        getDashboardData().getDataSeriesCache());
        series.forEach(s -> b2.build(s, reportingPeriod, get(AggregationConfig.class).getAggregation()));
    }
}