package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.GcmActivity;
import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.Iob;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.Profile;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.eveningoutpost.dexdrip.Models.UserError;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import lecho.lib.hellocharts.formatter.LineChartValueFormatter;
import lecho.lib.hellocharts.formatter.SimpleLineChartValueFormatter;
import lecho.lib.hellocharts.listener.LineChartOnValueSelectListener;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.Chart;

/**
 * Created by stephenblack on 11/15/14.
 */
public class BgGraphBuilder {
    public static final int FUZZER = (1000 * 30 * 5); // 2.5 mins?
    public static final int TREATMENT_COLOR_GREEN = Color.parseColor("#77aa00");
    public static final int TREATMENT_COLOR_DARK_GREEN = Color.parseColor("#334400");
    public static final int PREDICTIVE_COLOR_PURPLE = Color.parseColor("#7700aa");
    private final static String TAG = "jamorham graph test";
    final int pointSize;
    final int axisTextSize;
    final int previewAxisTextSize;
    final int hoursPreviewStep;
    private final int numValues = (60 / 5) * 24;
    public double end_time = (new Date().getTime() + (60000 * 10)) / FUZZER;
    public double predictive_end_time;
    public double start_time = end_time - ((60000 * 60 * 24)) / FUZZER;
    private final List<BgReading> bgReadings = BgReading.latestForGraph(numValues, (start_time * FUZZER));
    private final List<Calibration> calibrations = Calibration.latestForGraph(numValues, (start_time * FUZZER));
    private final List<Treatments> treatments = Treatments.latestForGraph(numValues, (start_time * FUZZER));
    private final List<Iob> iobinfo = Treatments.ioBForGraph(numValues, (start_time * FUZZER));
    public Context context;
    public SharedPreferences prefs;
    public double highMark;
    public double lowMark;
    public double defaultMinY;
    public double defaultMaxY;
    public boolean doMgdl;
    public Viewport viewport;
    private int predictivehours = 0;
    private static double avg1value = 0;
    private static double avg2value = 0;
    private static int avg1counter = 0;
    private static double avg1startfuzzed = 0;
    private static int avg2counter = 0;
    private double endHour;
    private List<PointValue> inRangeValues = new ArrayList<PointValue>();
    private List<PointValue> highValues = new ArrayList<PointValue>();
    private List<PointValue> lowValues = new ArrayList<PointValue>();
    private List<PointValue> rawInterpretedValues = new ArrayList<PointValue>();
    private List<PointValue> calibrationValues = new ArrayList<PointValue>();
    private List<PointValue> treatmentValues = new ArrayList<PointValue>();
    private List<PointValue> iobValues = new ArrayList<PointValue>();
    private List<PointValue> cobValues = new ArrayList<PointValue>();
    private List<PointValue> predictedBgValues = new ArrayList<PointValue>();
    private List<PointValue> activityValues = new ArrayList<PointValue>();
    private List<PointValue> annotationValues = new ArrayList<PointValue>();

    public BgGraphBuilder(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.highMark = Double.parseDouble(prefs.getString("highValue", "170"));
        this.lowMark = Double.parseDouble(prefs.getString("lowValue", "70"));
        this.doMgdl = (prefs.getString("units", "mgdl").equals("mgdl"));
        defaultMinY = unitized(40);
        defaultMaxY = unitized(250);
        pointSize = isXLargeTablet(context) ? 5 : 3;
        axisTextSize = isXLargeTablet(context) ? 20 : Axis.DEFAULT_TEXT_SIZE_SP;
        previewAxisTextSize = isXLargeTablet(context) ? 12 : 5;
        hoursPreviewStep = isXLargeTablet(context) ? 2 : 1;
    }

    private double bgScale() {
        if (doMgdl)
            return Constants.MMOLL_TO_MGDL;
        else
            return 1;
    }
    private static Object cloneObject(Object obj) {
        try {
            Object clone = obj.getClass().newInstance();
            for (Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                field.set(clone, field.get(obj));
            }
            return clone;
        } catch (Exception e) {
            return null;
        }
    }

    static public boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public static double mmolConvert(double mgdl) {
        return mgdl * Constants.MGDL_TO_MMOLL;
    }

    public LineChartData lineData() {
        LineChartData lineData = new LineChartData(defaultLines());
        lineData.setAxisYLeft(yAxis());
        lineData.setAxisXBottom(xAxis());
        return lineData;
    }

    public LineChartData previewLineData() {
        LineChartData previewLineData = new LineChartData(lineData());
        previewLineData.setAxisYLeft(yAxis());
        previewLineData.setAxisXBottom(previewXAxis());

        for (Line lline : previewLineData.getLines()) {
            if ((lline.hasLabels() && (lline.getPointRadius() > 0))) {

                lline.setPointRadius(3); // preserve size for treatments
                lline.setPointColor(Color.parseColor("#FFFFFF"));
            } else if (lline.getPointRadius() > 0) {
                lline.setPointRadius(1);
            }
            lline.setHasLabels(false);
        }
        // needs more adjustments - foreach
        return previewLineData;
    }

    public List<Line> defaultLines() {
        List<Line> lines = new ArrayList<Line>();
        try {

            addBgReadingValues();

            Line[] calib = calibrationValuesLine();
            Line[] treatments = treatmentValuesLine();

            for (Line subLine : autoSplitLine(treatments[2], 10)) {
                lines.add(subLine); // iob line
            }

            predictive_end_time = (new Date().getTime() + (60000 * 10) + (1000 * 60 * 60 * predictivehours)) / FUZZER; // used first in ideal/highline

            if (prefs.getBoolean("show_full_average_line", false)) {
                if (avg2value > 0) lines.add(avg2Line());
            }
            if (prefs.getBoolean("show_recent_average_line", true)) {
                if (avg1value > 0) lines.add(avg1Line());
            }
            if (prefs.getBoolean("show_target_line", false)) {
                lines.add(idealLine());
            }

            lines.add(treatments[3]); // activity
            lines.add(treatments[5]); // predictive
            lines.add(treatments[6]); // cob


            lines.add(minShowLine());
            lines.add(maxShowLine());
            lines.add(highLine());
            lines.add(predictiveHighLine());
            lines.add(lowLine());
            lines.add(predictiveLowLine());

            if (prefs.getBoolean("show_filtered_curve", true)) {
                // use autosplit here too
                ArrayList<Line> rawlines = rawInterpretedLines();

                for (Line thisline : rawlines) {
                    lines.add(thisline);
                }
            }

            lines.add(inRangeValuesLine());
            lines.add(lowValuesLine());
            lines.add(highValuesLine());

            lines.add(calib[0]); // white circle of calib in background
            lines.add(treatments[0]); // white circle of treatment in background

            lines.add(calib[1]); // red dot of calib in foreground
            lines.add(treatments[1]); // blue dot in centre // has annotation
            lines.add(treatments[4]); // annotations
        } catch (Exception e) {
            Log.e(TAG, "Error in bgbuilder defaultlines: " + e.toString());
        }
        return lines;
    }

    public Line highValuesLine() {
        Line highValuesLine = new Line(highValues);
        highValuesLine.setColor(ChartUtils.COLOR_ORANGE);
        highValuesLine.setHasLines(false);
        highValuesLine.setPointRadius(pointSize);
        highValuesLine.setHasPoints(true);
        return highValuesLine;
    }

    public Line lowValuesLine() {
        Line lowValuesLine = new Line(lowValues);
        lowValuesLine.setColor(Color.parseColor("#C30909"));
        lowValuesLine.setHasLines(false);
        lowValuesLine.setPointRadius(pointSize);
        lowValuesLine.setHasPoints(true);
        return lowValuesLine;
    }

    public Line inRangeValuesLine() {
        Line inRangeValuesLine = new Line(inRangeValues);
        inRangeValuesLine.setColor(ChartUtils.COLOR_BLUE);
        inRangeValuesLine.setHasLines(false);
        inRangeValuesLine.setPointRadius(pointSize);
        inRangeValuesLine.setHasPoints(true);
        return inRangeValuesLine;
    }

    public void debugPrintPoints(List<PointValue> mypoints) {
        for (PointValue thispoint : mypoints) {
            UserError.Log.i(TAG, "Debug Points: " + thispoint.toString());
        }
    }

    // auto split a line - jump thresh in minutes
    public ArrayList<Line> autoSplitLine(Line macroline, float jumpthresh) {
       // Log.d(TAG, "Enter autoSplit Line");
        ArrayList<Line> linearray = new ArrayList<Line>();
        float lastx = -999999;

        List<PointValue> macropoints = macroline.getValues();
        List<PointValue> thesepoints = new ArrayList<PointValue>();

        if (macropoints.size() > 0) {

            float endmarker = macropoints.get(macropoints.size() - 1).getX();
            for (PointValue thispoint : macropoints) {

                // a jump too far for a line? make it a new one
                if (((lastx != -999999) && (Math.abs(thispoint.getX() - lastx) > jumpthresh))
                        || thispoint.getX() == endmarker) {

                    if (thispoint.getX() == endmarker) {
                        thesepoints.add(thispoint);
                    }
                    Line line = (Line) cloneObject(macroline); // aieeee
                    line.setValues(thesepoints);
                    linearray.add(line);
                    thesepoints = new ArrayList<PointValue>();
                }

                lastx = thispoint.getX();
                thesepoints.add(thispoint); // grow current line list
            }
        }
     //   Log.d(TAG, "Exit autoSplit Line");
        return linearray;
    }

    public ArrayList<Line> rawInterpretedLines() {
        ArrayList<Line> linearray = new ArrayList<Line>();
        float lastx = -999999;
        float jumpthresh = 15; // in minutes
        List<PointValue> thesepoints = new ArrayList<PointValue>();

        if (rawInterpretedValues.size() > 0) {

            float endmarker = rawInterpretedValues.get(rawInterpretedValues.size() - 1).getX();

            for (PointValue thispoint : rawInterpretedValues) {
                // a jump too far for a line? make it a new one
                if (((lastx != -999999) && (Math.abs(thispoint.getX() - lastx) > jumpthresh))
                        || thispoint.getX() == endmarker) {
                    Line line = new Line(thesepoints);
                    line.setHasPoints(true);
                    line.setPointRadius(2);
                    line.setStrokeWidth(1);
                    line.setColor(Color.parseColor("#a0a0a0"));
                    line.setCubic(true);
                    line.setHasLines(true);
                    linearray.add(line);
                    thesepoints = new ArrayList<PointValue>();
                }

                lastx = thispoint.getX();
                thesepoints.add(thispoint); // grow current line list
            }
        } else {
            UserError.Log.i(TAG, "Raw points size is zero");
        }

        if ((Home.is_follower) && (rawInterpretedValues.size() < 3)) {
            GcmActivity.requestBGsync();
        }
        //UserError.Log.i(TAG, "Returning linearray: " + Integer.toString(linearray.size()));
        return linearray;
    }

    public Line[] calibrationValuesLine() {
        Line[] lines = new Line[2];
        lines[0] = new Line(calibrationValues);
        lines[0].setColor(Color.parseColor("#FFFFFF"));
        lines[0].setHasLines(false);
        lines[0].setPointRadius(pointSize * 3 / 2);
        lines[0].setHasPoints(true);
        lines[1] = new Line(calibrationValues);
        lines[1].setColor(ChartUtils.COLOR_RED);
        lines[1].setHasLines(false);
        lines[1].setPointRadius(pointSize * 3 / 4);
        lines[1].setHasPoints(true);
        return lines;
    }

    public Line[] treatmentValuesLine() {
        Line[] lines = new Line[8];
        try {

            lines[0] = new Line(treatmentValues);
            lines[0].setColor(Color.parseColor("#FFFFFF"));
            lines[0].setHasLines(false);
            lines[0].setPointRadius(pointSize * 5 / 2);
            lines[0].setHasPoints(true);

            lines[1] = new Line(treatmentValues);
            lines[1].setColor(TREATMENT_COLOR_GREEN);
            lines[1].setHasLines(false);
            lines[1].setPointRadius(pointSize * 5 / 4);
            lines[1].setHasPoints(true);
            lines[1].setShape(ValueShape.DIAMOND);
            lines[1].setHasLabels(true);

            LineChartValueFormatter formatter = new SimpleLineChartValueFormatter(1);
            lines[1].setFormatter(formatter);

            // insulin on board
            lines[2] = new Line(iobValues);
            lines[2].setColor(TREATMENT_COLOR_GREEN);
            // need splitter for cubics
            lines[2].setHasLines(true);
            lines[2].setCubic(false);
            lines[2].setFilled(true);
            lines[2].setAreaTransparency(35);
            lines[2].setFilled(true);
            lines[2].setPointRadius(1);
            lines[2].setHasPoints(true);
            // lines[2].setShape(ValueShape.DIAMOND);
            // lines[2].setHasLabels(true);

            // iactivity on board
            lines[3] = new Line(activityValues);
            lines[3].setColor(TREATMENT_COLOR_DARK_GREEN);
            lines[3].setHasLines(false);
            lines[3].setCubic(false);
            lines[3].setFilled(false);

            lines[3].setFilled(false);
            lines[3].setPointRadius(1);
            lines[3].setHasPoints(true);

            // annotations
            lines[4] = new Line(annotationValues);
            lines[4].setColor(TREATMENT_COLOR_GREEN);
            lines[4].setHasLines(false);
            lines[4].setCubic(false);
            lines[4].setFilled(false);
            lines[4].setPointRadius(0);
            lines[4].setHasPoints(true);
            lines[4].setHasLabels(true);

            lines[5] = new Line(predictedBgValues);
            lines[5].setColor(ChartUtils.COLOR_VIOLET);
            lines[5].setHasLines(false);
            lines[5].setCubic(false);
            lines[5].setStrokeWidth(1);
            lines[5].setFilled(false);
            lines[5].setPointRadius(2);
            lines[5].setHasPoints(true);
            lines[5].setHasLabels(false);

            lines[6] = new Line(cobValues);
            lines[6].setColor(PREDICTIVE_COLOR_PURPLE); // change this to cob color name
            lines[6].setHasLines(false);
            lines[6].setCubic(false);
            lines[6].setFilled(false);
            lines[6].setPointRadius(1);
            lines[6].setHasPoints(true);
            lines[6].setHasLabels(false);
        } catch (Exception e) {
            Log.d(TAG, "Exception making treatment lines: " + e.toString());
        }
        return lines;
    }

    private void addBgReadingValues() {
       //UserError.Log.i(TAG, "ADD BG READINGS START");
        rawInterpretedValues.clear();
        iobValues.clear();
        activityValues.clear();
        cobValues.clear();
        predictedBgValues.clear();
        annotationValues.clear();
        treatmentValues.clear();
        highValues.clear();
        lowValues.clear();
        inRangeValues.clear();
        calibrationValues.clear();
        final double bgScale = bgScale();

        final double avg1start = JoH.ts()-(1000*60*60*8); // 8 hours
        avg1startfuzzed=avg1start / FUZZER;
        avg1value = 0;
        avg1counter = 0;
        avg2value = 0;
        avg2counter = 0;

        for (BgReading bgReading : bgReadings) {
            // jamorham special
            if (bgReading.filtered_calculated_value > 0) {
                rawInterpretedValues.add(new PointValue((float) ((bgReading.timestamp - 500000) / FUZZER), (float) unitized(bgReading.filtered_calculated_value)));
            }
            if (bgReading.raw_calculated > 0 && prefs.getBoolean("interpret_raw", false)) {

                rawInterpretedValues.add(new PointValue((float) (bgReading.timestamp / FUZZER), (float) unitized(bgReading.raw_calculated)));
            } else if (bgReading.calculated_value >= 400) {
                highValues.add(new PointValue((float) (bgReading.timestamp / FUZZER), (float) unitized(400)));
            } else if (unitized(bgReading.calculated_value) >= highMark) {
                highValues.add(new PointValue((float) (bgReading.timestamp / FUZZER), (float) unitized(bgReading.calculated_value)));
            } else if (unitized(bgReading.calculated_value) >= lowMark) {
                inRangeValues.add(new PointValue((float) (bgReading.timestamp / FUZZER), (float) unitized(bgReading.calculated_value)));
            } else if (bgReading.calculated_value >= 40) {
                lowValues.add(new PointValue((float) (bgReading.timestamp / FUZZER), (float) unitized(bgReading.calculated_value)));
            } else if (bgReading.calculated_value > 13) {
                lowValues.add(new PointValue((float) (bgReading.timestamp / FUZZER), (float) unitized(40)));
            }

            avg2counter++;
            avg2value +=bgReading.calculated_value;
            if (bgReading.timestamp > avg1start)
            {
                 avg1counter++;
                 avg1value +=bgReading.calculated_value;
            }
        }
        if (avg1counter>0) { avg1value = avg1value / avg1counter; };
        if (avg2counter>0) { avg2value = avg2value / avg2counter; };

        //Log.i(TAG,"Average1 value: "+unitized(avg1value));
        //Log.i(TAG,"Average2 value: "+unitized(avg2value));

        try {
            for (Calibration calibration : calibrations) {
                calibrationValues.add(new PointValue((float) (calibration.timestamp / FUZZER), (float) unitized(calibration.bg)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception doing calibration values in bggraphbuilder: " + e.toString());
        }
        try {
            // display treatment blobs and annotations
            for (Treatments treatment : treatments) {

                double height = 6 * bgScale;
                if (treatment.insulin > 0)
                    height = treatment.insulin; // some scaling needed I think
                if (height > highMark) height = highMark;
                if (height < lowMark) height = lowMark;

                PointValue pv = new PointValue((float) (treatment.timestamp / FUZZER), (float) height);
                String mylabel = "";
                if (treatment.insulin > 0) {
                    if (mylabel.length() > 0)
                        mylabel = mylabel + System.getProperty("line.separator");
                    mylabel = mylabel + (Double.toString(treatment.insulin) + "u").replace(".0u", "u");
                }
                if (treatment.carbs > 0) {
                    if (mylabel.length() > 0)
                        mylabel = mylabel + System.getProperty("line.separator");
                    mylabel = mylabel + (Double.toString(treatment.carbs) + "g").replace(".0g", "g");
                }
                pv.setLabel(mylabel); // standard label
                if (treatmentValues.size() > 0) { // not sure if this >1 is right really - needs a review
                    PointValue lastpv = treatmentValues.get(treatmentValues.size() - 1);
                    if (Math.abs(lastpv.getX() - pv.getX()) < ((10 * 60 * 1000) / FUZZER)) {
                        // merge label with previous - Intelligent parsing and additions go here
                        Log.d(TAG, "Merge treatment difference: " + Float.toString(lastpv.getX() - pv.getX()));
                        String lastlabel = String.valueOf(lastpv.getLabelAsChars());
                        if (lastlabel.length() > 0) {
                            lastpv.setLabel(lastlabel + "+" + mylabel);
                            pv.setLabel("");
                        }
                    }
                }
                treatmentValues.add(pv); // hover
                Log.d(TAG, "Treatment total record: " + Double.toString(height) + " " + " timestamp: " + Long.toString(treatment.timestamp));
            }

        } catch (Exception e) {

            Log.e(TAG, "Exception doing treatment values in bggraphbuilder: " + e.toString());
        }
        try {

            final double iobscale = 1 * bgScale;
            final double cobscale = 0.2 * bgScale;
            // we need to check we actually have sufficient data for this
            double predictedbg = 0;
            BgReading mylastbg = bgReadings.get(0);
            double lasttimestamp = 0;

            // this can be optimised to oncreate and onchange
            Profile.reloadPreferences(prefs);

            try {
                if (mylastbg != null) {
                    if (doMgdl) {
                        predictedbg = mylastbg.calculated_value;
                    } else {
                        predictedbg = mylastbg.calculated_value_mmol();
                    }
                    Log.d(TAG, "Starting prediction with bg of: " + JoH.qs(predictedbg));
                    lasttimestamp = mylastbg.timestamp / FUZZER;
                } else {
                    Log.d(TAG, "COULD NOT GET LAST BG READING FOR PREDICTION!!!");
                }
            } catch (Exception e) {
                // could not get a bg reading
            }
            long fuzzed_timestamp = (long) end_time; // initial value in case there are no iob records

            if (iobinfo != null) {
                for (Iob iob : iobinfo) {

                    double activity = iob.activity;
                    if ((iob.iob > 0) || (iob.cob > 0)) {
                        fuzzed_timestamp = iob.timestamp / FUZZER;
                        if (iob.iob > Profile.minimum_shown_iob) {
                            double height = iob.iob * iobscale;
                            if (height > highMark) height = highMark;
                            PointValue pv = new PointValue((float) fuzzed_timestamp, (float) height);
                            iobValues.add(pv);
                            double activityheight = iob.jActivity * 3; // currently scaled by profile
                            if (activityheight > highMark) activityheight = highMark;
                            PointValue av = new PointValue((float) fuzzed_timestamp, (float) activityheight);
                            activityValues.add(av);
                        }

                        if (iob.cob > 0) {
                            double height = iob.cob * cobscale;
                            if (height > highMark) height = highMark;
                            PointValue pv = new PointValue((float) fuzzed_timestamp, (float) height);
                            Log.d(TAG, "Cob total record: " + JoH.qs(height) + " " + JoH.qs(iob.cob) + " " + Float.toString(pv.getY()) + " @ timestamp: " + Long.toString(iob.timestamp));
                            cobValues.add(pv); // warning should not be hardcoded
                        }

                        // do we actually need to calculate this within the loop - can we use only the last datum?
                        if (fuzzed_timestamp > (lasttimestamp)) {
                            Log.d(TAG, "Processing prediction: before: " + JoH.qs(predictedbg) + " activity: " + JoH.qs(activity) + " jcarbimpact: " + JoH.qs(iob.jCarbImpact));
                            predictedbg -= iob.jActivity; // lower bg by current insulin activity
                            predictedbg += iob.jCarbImpact;
                            // we should pull in actual graph upper and lower limits here
                            if ((predictedbg < highMark) && (predictedbg > 0)) {
                                PointValue zv = new PointValue((float) fuzzed_timestamp, (float) predictedbg);
                                predictedBgValues.add(zv);
                            }
                        }
                        if (fuzzed_timestamp > end_time) {
                            predictivehours = (int) (((fuzzed_timestamp - end_time) * FUZZER) / (1000 * 60 * 60)) + 1; // round up to nearest future hour - timestamps in minutes here
                        } else {
                            if ((fuzzed_timestamp == end_time - 4) && (iob.iob > 0)) {
                                // show current iob
                                double position = 12.4 * bgScale; // this is for mmol - needs generic for mg/dl
                                if (Math.abs(predictedbg - position) < (2 * bgScale)) {
                                    position = 7.0 * bgScale;
                                }

                                PointValue iv = new PointValue((float) fuzzed_timestamp, (float) position);
                                DecimalFormat df = new DecimalFormat("#");
                                df.setMaximumFractionDigits(2);
                                df.setMinimumIntegerDigits(1);
                                iv.setLabel("IoB: " + df.format(iob.iob));
                                annotationValues.add(iv); // needs to be different value list so we can make annotation nicer
                            }
                        }

                    }
                }
                Log.d(TAG, "Size of iob: " + Integer.toString(iobinfo.size()) + " Predictive hours: " + Integer.toString(predictivehours)
                        + " Predicted end game change: " + JoH.qs(predictedbg - mylastbg.calculated_value_mmol())
                        + " Start bg: " + JoH.qs(mylastbg.calculated_value_mmol()) + " Predicted: " + JoH.qs(predictedbg));
                // calculate bolus or carb adjustment - these should have granularity for injection / pump and thresholds
            } else {
                Log.d(TAG, "iobinfo was null");
            }

            double[] evaluation;
            if (doMgdl)
            {
                // These routines need to understand how the profile is defined to use native instead of scaled
                Profile.scale_factor = Constants.MMOLL_TO_MGDL;
                evaluation = Profile.evaluateEndGameMmol(predictedbg, lasttimestamp * FUZZER, end_time * FUZZER);
            } else {
                Profile.scale_factor = 1;
                evaluation = Profile.evaluateEndGameMmol(predictedbg, lasttimestamp * FUZZER, end_time * FUZZER);

            }
                Log.d(TAG, "Predictive Bolus Wizard suggestion: Current prediction: " + JoH.qs(predictedbg) + " / carbs: " + JoH.qs(evaluation[0]) + " insulin: " + JoH.qs(evaluation[1]));
            if (evaluation[0] > Profile.minimum_carb_recommendation) {
                PointValue iv = new PointValue((float) fuzzed_timestamp, (float) (10 * bgScale));
                iv.setLabel("Eat Carbs: " + JoH.qs(evaluation[0], 0));
                annotationValues.add(iv); // needs to be different value list so we can make annotation nicer
            }
            if (evaluation[1] > Profile.minimum_insulin_recommendation) {
                PointValue iv = new PointValue((float) fuzzed_timestamp, (float) (11 * bgScale));
                iv.setLabel("Take Insulin: " + JoH.qs(evaluation[1], 1));
                annotationValues.add(iv); // needs to be different value list so we can make annotation nicer
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception doing iob values in bggraphbuilder: " + e.toString());
        }
    }

    public Line avg1Line() {
        List<PointValue> myLineValues = new ArrayList<PointValue>();
        myLineValues.add(new PointValue((float) avg1startfuzzed, (float) unitized(avg1value)));
        myLineValues.add(new PointValue((float) end_time, (float) unitized(avg1value)));
        Line myLine = new Line(myLineValues);
        myLine.setHasPoints(false);
        myLine.setStrokeWidth(1);
        myLine.setColor(Color.parseColor("#558800"));
        myLine.setPathEffect(new DashPathEffect(new float[]{10.0f, 10.0f},0));
        myLine.setAreaTransparency(50);
        return myLine;
    }
    public Line avg2Line() {
        List<PointValue> myLineValues = new ArrayList<PointValue>();
        myLineValues.add(new PointValue((float) start_time, (float) unitized(avg2value)));
        myLineValues.add(new PointValue((float) end_time, (float) unitized(avg2value)));
        Line myLine = new Line(myLineValues);
        myLine.setHasPoints(false);
        myLine.setStrokeWidth(1);
        myLine.setColor(Color.parseColor("#c56f9d"));
        myLine.setPathEffect(new DashPathEffect(new float[]{30.0f, 10.0f},0));
        myLine.setAreaTransparency(50);
        return myLine;
    }

    public Line idealLine() {
        // if profile has more than 1 target bg value then we need to iterate those and plot them for completeness
        List<PointValue> myLineValues = new ArrayList<PointValue>();
        myLineValues.add(new PointValue((float) start_time, (float)  Profile.getTargetRangeInUnits(start_time)));
        myLineValues.add(new PointValue((float) predictive_end_time, (float) Profile.getTargetRangeInUnits(predictive_end_time)));
        Line myLine = new Line(myLineValues);
        myLine.setHasPoints(false);
        myLine.setStrokeWidth(1);
        myLine.setColor(Color.parseColor("#a4a409"));
        myLine.setPathEffect(new DashPathEffect(new float[]{5f, 5f}, 0));
        myLine.setAreaTransparency(50);
        return myLine;
    }

    public Line highLine() {
        List<PointValue> highLineValues = new ArrayList<PointValue>();
        highLineValues.add(new PointValue((float) start_time, (float) highMark));
        highLineValues.add(new PointValue((float) end_time, (float) highMark));
        Line highLine = new Line(highLineValues);
        highLine.setHasPoints(false);
        highLine.setStrokeWidth(1);
        highLine.setColor(ChartUtils.COLOR_ORANGE);
        return highLine;
    }

    public Line predictiveHighLine() {
        List<PointValue> predictiveHighLineValues = new ArrayList<PointValue>();
        predictiveHighLineValues.add(new PointValue((float) end_time, (float) highMark));
        predictiveHighLineValues.add(new PointValue((float) predictive_end_time, (float) highMark));
        Line highLine = new Line(predictiveHighLineValues);
        highLine.setHasPoints(false);
        highLine.setStrokeWidth(1);
        highLine.setColor(ChartUtils.darkenColor(ChartUtils.darkenColor(ChartUtils.darkenColor(ChartUtils.COLOR_ORANGE))));
        return highLine;
    }

    public Line lowLine() {
        List<PointValue> lowLineValues = new ArrayList<PointValue>();
        lowLineValues.add(new PointValue((float) start_time, (float) lowMark));
        lowLineValues.add(new PointValue((float) end_time, (float) lowMark));
        Line lowLine = new Line(lowLineValues);
        lowLine.setHasPoints(false);
        lowLine.setAreaTransparency(50);
        lowLine.setColor(Color.parseColor("#C30909"));
        lowLine.setStrokeWidth(1);
        lowLine.setFilled(true);
        return lowLine;
    }

    public Line predictiveLowLine() {
        List<PointValue> lowLineValues = new ArrayList<PointValue>();
        lowLineValues.add(new PointValue((float) end_time, (float) lowMark));
        lowLineValues.add(new PointValue((float) predictive_end_time, (float) lowMark));
        Line lowLine = new Line(lowLineValues);
        lowLine.setHasPoints(false);
        lowLine.setAreaTransparency(40);
        lowLine.setColor(ChartUtils.darkenColor(ChartUtils.darkenColor(ChartUtils.darkenColor(Color.parseColor("#C30909")))));
        lowLine.setStrokeWidth(1);
        lowLine.setFilled(true);
        return lowLine;
    }

    public Line maxShowLine() {
        List<PointValue> maxShowValues = new ArrayList<PointValue>();
        maxShowValues.add(new PointValue((float) start_time, (float) defaultMaxY));
        maxShowValues.add(new PointValue((float) end_time, (float) defaultMaxY));
        Line maxShowLine = new Line(maxShowValues);
        maxShowLine.setHasLines(false);
        maxShowLine.setHasPoints(false);
        return maxShowLine;
    }

    public Line minShowLine() {
        List<PointValue> minShowValues = new ArrayList<PointValue>();
        minShowValues.add(new PointValue((float) start_time, (float) defaultMinY));
        minShowValues.add(new PointValue((float) end_time, (float) defaultMinY));
        Line minShowLine = new Line(minShowValues);
        minShowLine.setHasPoints(false);
        minShowLine.setHasLines(false);
        return minShowLine;
    }

    /////////AXIS RELATED//////////////
    public Axis yAxis() {
        Axis yAxis = new Axis();
        yAxis.setAutoGenerated(false);
        List<AxisValue> axisValues = new ArrayList<AxisValue>();

        for (int j = 1; j <= 12; j += 1) {
            if (doMgdl) {
                axisValues.add(new AxisValue(j * 50));
            } else {
                axisValues.add(new AxisValue(j * 2));
            }
        }
        yAxis.setValues(axisValues);
       // yAxis.setHasLines(true);
        yAxis.setMaxLabelChars(5);
        yAxis.setInside(true);
        yAxis.setTextSize(axisTextSize);
        yAxis.setHasLines(prefs.getBoolean("show_graph_grid_glucose",true));
        return yAxis;
    }

    public Axis xAxis() {
        Axis xAxis = new Axis();
        xAxis.setAutoGenerated(false);
        List<AxisValue> xAxisValues = new ArrayList<AxisValue>();
        GregorianCalendar now = new GregorianCalendar();
        GregorianCalendar today = new GregorianCalendar(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        final java.text.DateFormat timeFormat = hourFormat();
        timeFormat.setTimeZone(TimeZone.getDefault());
        double start_hour_block = today.getTime().getTime();
        double timeNow = new Date().getTime();
        for (int l = 0; l <= 24; l++) {
            if ((start_hour_block + (60000 * 60 * (l))) < timeNow) {
                if ((start_hour_block + (60000 * 60 * (l + 1))) >= timeNow) {
                    endHour = start_hour_block + (60000 * 60 * (l));
                    l = 25;
                }
            }
        }
        for (int l = 0; l <= (24 + predictivehours); l++) {
            double timestamp = (endHour + ((predictivehours) * 60 * 1000 * 60) - (60000 * 60 * l));
            xAxisValues.add(new AxisValue((long) (timestamp / FUZZER), (timeFormat.format(timestamp)).toCharArray()));
        }
        xAxis.setValues(xAxisValues);
        xAxis.setHasLines(prefs.getBoolean("show_graph_grid_time", true));
        xAxis.setTextSize(axisTextSize);
        return xAxis;
    }

    private SimpleDateFormat hourFormat() {
        return new SimpleDateFormat(DateFormat.is24HourFormat(context) ? "HH" : "h a");
    }

    public Axis previewXAxis() {
        List<AxisValue> previewXaxisValues = new ArrayList<AxisValue>();
        final java.text.DateFormat timeFormat = hourFormat();
        timeFormat.setTimeZone(TimeZone.getDefault());
        for (int l = 0; l <= (24 + predictivehours); l += hoursPreviewStep) {
            double timestamp = (endHour + (predictivehours * 60 * 1000 * 60) - (60000 * 60 * l));
            previewXaxisValues.add(new AxisValue((long) (timestamp / FUZZER), (timeFormat.format(timestamp)).toCharArray()));
        }
        Axis previewXaxis = new Axis();
        previewXaxis.setValues(previewXaxisValues);
        previewXaxis.setHasLines(true);
        previewXaxis.setTextSize(previewAxisTextSize);
        return previewXaxis;
    }

    /////////VIEWPORT RELATED//////////////
    public Viewport advanceViewport(Chart chart, Chart previewChart) {
        viewport = new Viewport(previewChart.getMaximumViewport());
        viewport.inset((float) ((86400000 / 2.5) / FUZZER), 0);
        double distance_to_move = ((new Date().getTime()) / FUZZER) - viewport.left - (((viewport.right - viewport.left) / 2));
        viewport.offset((float) distance_to_move, 0);
        return viewport;
    }

    public double unitized(double value) {
        if (doMgdl) {
            return value;
        } else {
            return mmolConvert(value);
        }
    }

    public String unitized_string(double value) {
        DecimalFormat df = new DecimalFormat("#");
        if (value >= 400) {
            return "HIGH";
        } else if (value >= 40) {
            if (doMgdl) {
                df.setMaximumFractionDigits(0);
                return df.format(value);
            } else {
                df.setMaximumFractionDigits(1);
                //next line ensures mmol/l value is XX.x always.  Required by PebbleSync, and probably not a bad idea.
                df.setMinimumFractionDigits(1);
                return df.format(mmolConvert(value));
            }
        } else if (value > 12) {
            return "LOW";
        } else {
            switch ((int) value) {
                case 0:
                    return "??0";
                case 1:
                    return "?SN";
                case 2:
                    return "??2";
                case 3:
                    return "?NA";
                case 5:
                    return "?NC";
                case 6:
                    return "?CD";
                case 9:
                    return "?AD";
                case 12:
                    return "?RF";
                default:
                    return "???";
            }
        }
    }

    public String unitizedDeltaString(boolean showUnit, boolean highGranularity) {
    return unitizedDeltaString( showUnit, highGranularity, false);
    }
    public String unitizedDeltaString(boolean showUnit, boolean highGranularity, boolean is_follower) {

        List<BgReading> last2 = BgReading.latest(2,is_follower);
        if (last2.size() < 2 || last2.get(0).timestamp - last2.get(1).timestamp > 20 * 60 * 1000) {
            // don't show delta if there are not enough values or the values are more than 20 mintes apart
            return "???";
        }

        double value = BgReading.currentSlope(is_follower) * 5 * 60 * 1000;

        if (Math.abs(value) > 100) {
            // a delta > 100 will not happen with real BG values -> problematic sensor data
            return "ERR";
        }

        // TODO: allow localization from os settings once pebble doesn't require english locale
        DecimalFormat df = new DecimalFormat("#", new DecimalFormatSymbols(Locale.ENGLISH));
        String delta_sign = "";
        if (value > 0) {
            delta_sign = "+";
        }
        if (doMgdl) {

            if (highGranularity) {
                df.setMaximumFractionDigits(1);
            } else {
                df.setMaximumFractionDigits(0);
            }

            return delta_sign + df.format(unitized(value)) + (showUnit ? " mg/dl" : "");
        } else {
            // only show 2 decimal places on mmol/l delta when less than 0.1 mmol/l
            if (highGranularity && (Math.abs(value) < (Constants.MMOLL_TO_MGDL * 0.1))) {
                df.setMaximumFractionDigits(2);
            } else {
                df.setMaximumFractionDigits(1);
            }

            df.setMinimumFractionDigits(1);
            df.setMinimumIntegerDigits(1);
            return delta_sign + df.format(unitized(value)) + (showUnit ? " mmol/l" : "");
        }
    }

    public String unit() {
        if (doMgdl) {
            return "mg/dl";
        } else {
            return "mmol";
        }

    }

    public OnValueSelectTooltipListener getOnValueSelectTooltipListener() {
        return new OnValueSelectTooltipListener();
    }

    public class OnValueSelectTooltipListener implements LineChartOnValueSelectListener {

        private Toast tooltip;

        @Override
        public synchronized void onValueSelected(int i, int i1, PointValue pointValue) {
            final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
            //Won't give the exact time of the reading but the time on the grid: close enough.
            Long time = ((long) pointValue.getX()) * FUZZER;
            if (tooltip != null) {
                tooltip.cancel();
            }
            tooltip = Toast.makeText(context, timeFormat.format(time) + ": " + Math.round(pointValue.getY() * 10) / 10d, Toast.LENGTH_LONG);
            tooltip.show();
        }

        @Override
        public void onValueDeselected() {
            // do nothing
        }
    }
}
