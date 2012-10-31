package ru.megaplan.jira.plugins.mpsrate.chart;

import com.atlassian.jira.charts.Chart;
import com.atlassian.jira.charts.jfreechart.ChartHelper;
import com.atlassian.jira.charts.jfreechart.util.ChartDefaults;
import com.atlassian.jira.charts.jfreechart.util.ChartUtil;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.TickUnitSource;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.general.Series;
import org.jfree.data.time.*;
import org.jfree.data.xy.XYDataset;
import ru.megaplan.jira.plugins.mpsrate.rest.MpsRateGadgetResource;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 8/5/12
 * Time: 3:01 PM
 * To change this template use File | Settings | File Templates.
 */
public class MpsRateChart {

    private final static Logger log = Logger.getLogger(MpsRateChart.class);

    private final TimeZoneManager timeZoneManager;

    public MpsRateChart(TimeZoneManager timeZoneManager) {
        this.timeZoneManager = timeZoneManager;
    }

    public Chart generateChart(Map<Integer, List<MpsRateGadgetResource.XMLRate>> rates,
                               Map<Integer, ? extends Number> oldRatesSums, final com.atlassian.jira.charts.ChartFactory.PeriodName periodName,
                               int width,
                               int height, Date start, Date end, Map<Integer, String> typesMapping, boolean isCumulative) throws IOException {
        XYDataset data = null;
        data = getXYDatasetFromRates(rates, oldRatesSums, typesMapping, periodName, start, end, isCumulative);
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Динамика оценок","За промежуток времени","Оцененные тикеты", data, true, true, true);
        setPrettyView(chart);
        ChartHelper chartHelper = new ChartHelper(chart);
        chartHelper.generate(width, height);
        Map<String, Object> params = Maps.newHashMap();
        params.put("chart", chartHelper.getLocation());
        params.put("imagemap", chartHelper.getImageMap());
        params.put("imagemapName", chartHelper.getImageMapName());
        params.put("imageWidth", width);
        params.put("imageHeight", height);
        params.put("colorList", getColorList(chart));
        return new Chart(chartHelper.getLocation(), chartHelper.getImageMap(), chartHelper.getImageMapName(), params);
    }

    private List<String> getColorList(JFreeChart chart) {
        List<String> result = new ArrayList<String>();
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        for (int i = 0; i < plot.getSeriesCount(); ++i) {
            Paint p = renderer.getSeriesPaint(i);
            Color c = (Color) p;
            String rgb;
            if (c != null) {
                rgb = Integer.toHexString(c.getRGB());
            }
            else {
                rgb = Integer.toHexString(Color.BLACK.getRGB());
            }
            rgb = rgb.substring(2, rgb.length());
            result.add(rgb);
        }
        return result;
    }

    private Map<String, List<MpsRateGadgetResource.XMLRate>> getRatesWithNames(Map<Integer, List<MpsRateGadgetResource.XMLRate>> rates, Map<Integer, String> typesMapping) {
        Map<String, List<MpsRateGadgetResource.XMLRate>> result = new LinkedHashMap<String, List<MpsRateGadgetResource.XMLRate>>();
        for (Map.Entry<Integer, List<MpsRateGadgetResource.XMLRate>> e : rates.entrySet()) {
            String rateName = null;
            if (typesMapping == null)
                rateName = e.getKey().toString();
            else {
                rateName = typesMapping.get(e.getKey());
            }
            if (rateName == null) rateName = e.getKey().toString();
            result.put(rateName, e.getValue());

        }
        return result;
    }


    private void setPrettyView(JFreeChart chart) {
        XYPlot plot = chart.getXYPlot();
        setTimeSeriesChartDefaults(chart, plot);
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        TickUnitSource units = NumberAxis.createIntegerTickUnits();
        yAxis.setStandardTickUnits(units);
        plot.setRangeAxis(yAxis);
    }

    private List<Color> generateGradient(int range) {
        List<Color> colors = new ArrayList<Color>();
        for (int i = 0; i < range; ++i) {
            int r = (255*i)/range;
            int g = (255*(range-i))/range;
            int b = 0;
            int alpha = 100;
            Color c = new Color(r,g,b,alpha);
            colors.add(c);
        }
        return colors;
    }

    private XYDataset getXYDatasetFromRates(Map<Integer, List<MpsRateGadgetResource.XMLRate>> rates,
                                            Map<Integer, ? extends Number> oldRatesSums, Map<Integer, String> typesMapping, com.atlassian.jira.charts.ChartFactory.PeriodName periodName, Date start, Date end, boolean isCumulative) {
        Class timePeriodClass = ChartUtil.getTimePeriodClass(periodName);
        TimeZone timeZone = timeZoneManager.getLoggedInUserTimeZone();
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        for (Map.Entry<Integer, List<MpsRateGadgetResource.XMLRate>> e : rates.entrySet()) {
            RegularTimePeriod currentTimePeriod = null;
            double rateSum = 0;
            Integer rating = e.getKey();
            String typeName = null;
            if (typesMapping != null) typeName = typesMapping.get(rating);
            TimeSeries timeSeries = new TimeSeries(typeName==null?rating:typeName);
            for (MpsRateGadgetResource.XMLRate rate : e.getValue()) {
                RegularTimePeriod timePeriod = RegularTimePeriod.createInstance(timePeriodClass, new Date(rate.getWhen()), timeZone);
                if (currentTimePeriod == null) currentTimePeriod = timePeriod;
                if (!currentTimePeriod.equals(timePeriod)) {
                    timeSeries.add(currentTimePeriod, rateSum);
                    if (!isCumulative) rateSum = 0;
                    currentTimePeriod = timePeriod;
                }
                rateSum++;
            }
            timeSeries.add(currentTimePeriod, rateSum);
            normalizeTimeSeries(timeSeries, timePeriodClass, start, end, timeZone, isCumulative);
            dataset.addSeries(timeSeries);
        }
        if (oldRatesSums != null) {
            for (Map.Entry<Integer, ? extends Number> e : oldRatesSums.entrySet()) {
                Integer rate = e.getKey();
                String seriesStringName = null;
                if (typesMapping != null)
                    seriesStringName = typesMapping.get(rate);
                Comparable seriesKey = seriesStringName==null ? rate : seriesStringName;
                TimeSeries series = dataset.getSeries(seriesKey);
                if (series == null) {
                    TimeSeries timeSeries = new TimeSeries(seriesKey);
                    timeSeries.add(RegularTimePeriod.createInstance(timePeriodClass, new Date(start.getTime()), timeZone), e.getValue());
                    normalizeTimeSeries(timeSeries, timePeriodClass, start, end, timeZone, true);
                    dataset.addSeries(timeSeries);
                } else {
                    for (int i = 0; i < series.getItemCount(); ++i) {
                        TimeSeriesDataItem timeSeriesDataItem = series.getDataItem(i);
                        timeSeriesDataItem.setValue(timeSeriesDataItem.getValue().doubleValue()+e.getValue().doubleValue());
                    }
                }
            }
        }
        return dataset;
    }

    private void normalizeTimeSeries(TimeSeries timeSeries, Class timePeriodClass, Date start, Date end, TimeZone timeZone, boolean cumulative) {
        RegularTimePeriod startPeriod = RegularTimePeriod.createInstance(timePeriodClass, start, timeZone);
        RegularTimePeriod endPeriod = RegularTimePeriod.createInstance(timePeriodClass, end, timeZone);
        RegularTimePeriod previousPeriod = null;
        while(!startPeriod.equals(endPeriod)) {
            if (timeSeries.getDataItem(startPeriod) == null) {
                if (cumulative) {
                    if (previousPeriod == null) {
                        timeSeries.add(startPeriod, 0);
                    } else {
                        timeSeries.add(startPeriod, timeSeries.getDataItem(previousPeriod).getValue());
                    }
                } else {
                    timeSeries.add(startPeriod, 0);
                }
            }
            previousPeriod = startPeriod;
            startPeriod = startPeriod.next();
        }
        if (timeSeries.getDataItem(startPeriod) == null) {
            if (cumulative) {
                if (previousPeriod == null) {
                    timeSeries.add(startPeriod, 0);
                } else {
                    timeSeries.add(startPeriod, timeSeries.getDataItem(previousPeriod).getValue());
                }
            } else {
                timeSeries.add(startPeriod, 0);
            }
        }
    }


    private static void setTimeSeriesChartDefaults(JFreeChart chart, Plot genericPlot)
    {
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);

        ChartUtil.setupPlot(chart.getPlot());

        ChartUtil.setupTextTitle(chart.getTitle());
        ChartUtil.setupLegendTitle(chart.getLegend());

        XYPlot plot = (XYPlot) genericPlot;

        // renderer
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setBaseItemLabelFont(ChartDefaults.defaultFont);
        renderer.setBaseItemLabelPaint(ChartDefaults.axisLabelColor);
        for (int j = 0; j < ChartDefaults.darkColors.length; j++)
        {
            Color dc = ChartDefaults.darkColors[j];
            Color dca = new Color(dc.getRed(), dc.getGreen(), dc.getBlue(), 160);
            renderer.setSeriesPaint(j, dca);
            renderer.setSeriesStroke(j, ChartDefaults.defaultStroke);
        }
        renderer.setBaseShapesVisible(false);
        renderer.setBaseStroke(ChartDefaults.defaultStroke);
    }



}
