// Configurations of webview analysis tool
/*
 * This config class contains the important options
 * which might be used in both app and web analysis
 * For app analysis, it configures soot tool by operating on Options.v()
 * For web analysis, it stores the javascript bridges and temp js file paths
 *  generated during app analysis
 */

package com.lynnlyc;

import org.apache.commons.cli.*;
import soot.options.Options;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

/**
 * Created by yuanchun on 5/4/15.
 * Package: webview-flow
 */
public class Config {
    public final String projectName = "HybridFlow";

    // File path of apk
    public static String appFilePath = "";

    // File path of source to sink definition
    public static String source2SinkFilePath = "";

    // Directory path to find android.jar
    public static String androidPlatformDir = "";

    // File path of android.jar which is forced to use by soot
    public static String forceAndroidJarPath = "";

    // Directory path of app html side
    public static String htmlDirPath = "";
    // Directory path of app java side
    public static String javaDirPath = "";
    // Directory path of bridges
    public static String bridgeDirPath = "";

    // Directory for result output
    public static String outputDirPath = "";

    // Output format
    public static String outputFormat = "dex";

    public static boolean isInitialized = false;

    // printer of output
    private static File logFile;
    private static PrintStream logPs;
    private static File bridgeFile;
    private static PrintStream bridgePs;

    // noticeable webview methods
    public static String[] webview_methods = {
        "addJavascriptInterface",
        "loadUrl",
        "evaluateJavascript",
        "loadData",
        "loadDataWithBaseURL"
    };

    // possible app entries, used for generating appEntryPoints
    public static String[] possible_entries = {
        "onCreate",
        "onStart",
        "onCreateView",
        "onClick",
    };

    public static boolean parseArgs(String[] args) {
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
        Option help = new Option("help", "print this message");
        Option quiet = new Option("quiet", "be extra quiet");
        Option debug = new Option("debug", "print debug information");
        Option outputDir = OptionBuilder.withArgName("directory").isRequired()
                .hasArg().withDescription("path to output dir").create('d');
        Option appPath = OptionBuilder.withArgName("file").isRequired()
                .hasArg().withDescription("path to target app").create("app");
        Option forceAndroidJar = OptionBuilder.withArgName("file").isRequired()
                .hasArg().withDescription("path to android.jar").create("sdk");
        Option webDir = OptionBuilder.withArgName("directory")
                .hasArg().withDescription("path to webpages").create("web");
        Option outFormat = OptionBuilder.withArgName("jimple or dex")
                .hasArg().withDescription("output format, default is dex").create('f');
        Option sourceToSinkOpt = OptionBuilder.withArgName("file").isRequired()
                .hasArg().withDescription("definitions of sources and sinks").create("source2sink");
        options.addOption(help);
        options.addOption(quiet);
        options.addOption(debug);
        options.addOption(outputDir);
        options.addOption(appPath);
        options.addOption(forceAndroidJar);
        options.addOption(webDir);
        options.addOption(outFormat);
        options.addOption(sourceToSinkOpt);

        CommandLineParser parser = new BasicParser();

        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption('d')) Config.outputDirPath = cmd.getOptionValue('d');
            if (cmd.hasOption("app")) Config.appFilePath = cmd.getOptionValue("app");
            File appFile = new File(Config.appFilePath);
            if (!appFile.exists()) {
                throw new ParseException("invalid app file path");
            }
            if (cmd.hasOption("sdk"))
                Config.forceAndroidJarPath = cmd.getOptionValue("sdk");
            if (cmd.hasOption("web")) Config.htmlDirPath = cmd.getOptionValue("web");
            if (cmd.hasOption('f')) Config.outputFormat = cmd.getOptionValue('f');
            if (cmd.hasOption("debug")) Util.LOGGER.setLevel(Level.ALL);
            if (cmd.hasOption("quiet")) Util.LOGGER.setLevel(Level.WARNING);
            if (!("jimple".equals(Config.outputFormat) || "dex".equals(Config.outputFormat))) {
                throw new ParseException("output format should be jimple or dex");
            }
            if (cmd.hasOption("source2sink"))
                Config.source2SinkFilePath = cmd.getOptionValue("source2sink");
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("WebViewFlow", options);
            return false;
        }

        return true;
    }

    public static void init() {
        if (!setUpFileStructure())
            return;
        Util.LOGGER.log(Level.INFO, "initializing...");
        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_output_dir(Config.outputDirPath);
        Options.v().set_debug(false);

        if ("jimple".equals(Config.outputFormat)) {
            Options.v().set_output_format(Options.output_format_jimple);
        }
        else if ("dex".equals(Config.outputFormat)) {
            Options.v().set_output_format(Options.output_format_dex);
        }

        List<String> process_dirs = new ArrayList<>();
        process_dirs.add(Config.appFilePath);
        Options.v().set_process_dir(process_dirs);
        if (!("".equals(Config.androidPlatformDir)))
            Options.v().set_android_jars(Config.androidPlatformDir);
        if (!("".equals(Config.forceAndroidJarPath)))
            Options.v().set_force_android_jar(Config.forceAndroidJarPath);

        Config.isInitialized = true;
        Util.LOGGER.log(Level.INFO,  "initialization finished...");
    }

    public static PrintStream getBridgePs() {
        if (bridgePs == null) {
            Util.LOGGER.log(Level.WARNING, "bridge printer is null, use stdout instead.");
            return System.out;
        }
        return bridgePs;
    }

    public static PrintStream getExceptionLogPs() {
        if (logPs == null) {
            Util.LOGGER.warning("log printer is null, use stdout instead.");
            return System.out;
        }
        return logPs;
    }

    public static boolean setUpFileStructure() {
        File workingDir = new File(String.format("%s/webviewflow_%s/", Config.outputDirPath, Util.getTimeString()));

        Config.outputDirPath = workingDir.getPath();
        if (!workingDir.exists() && !workingDir.mkdirs())
            return false;

        File javaDir = new File(Config.outputDirPath + "/java");
        if (!javaDir.exists() && !javaDir.mkdir())
            return false;
        javaDirPath = javaDir.getPath();

        File htmlDir = new File(Config.outputDirPath + "/html");
        if (!htmlDir.exists() && !htmlDir.mkdir())
            return false;
        htmlDirPath = htmlDir.getPath();

        File bridgeDir = new File(Config.outputDirPath + "/bridge");
        if (!bridgeDir.exists() && !bridgeDir.mkdir())
            return false;
        bridgeDirPath = bridgeDir.getPath();

        logFile = new File(Config.outputDirPath + "/exception.log");
        bridgeFile = new File(Config.bridgeDirPath + "/bridge.txt");
        File normalLogFile = new File(Config.outputDirPath + "/analysis.log");

        try {
            bridgePs = new PrintStream(new FileOutputStream(bridgeFile));
            logPs = new PrintStream(new FileOutputStream(logFile));
            FileHandler fh = new FileHandler(normalLogFile.getAbsolutePath());
            fh.setFormatter(new SimpleFormatter());
            Util.LOGGER.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
