package com.lynnlyc.app;

import com.lynnlyc.Config;
import com.lynnlyc.Util;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JVirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Created by yuanchun on 5/5/15.
 * Package: webview-flow
 */
public class AppManager {
    public String appFilePath;
    private boolean isPrepared = false;
    private boolean isPTAFinished = false;
    private PointsToAnalysis pta = null;

    public AppManager(String appFilePath) {
        this.appFilePath = appFilePath;
        this.prepare();
    }

    // entry points of soot-based app analysis
    public List<SootMethod> appEntryPoints = new ArrayList<SootMethod>();

    private void prepare() {
        if (!Config.isInitialized) {
            Util.LOGGER.log(Level.WARNING, "Configuration not initialized");
            return;
        }
        Scene.v().loadNecessaryClasses();
        appEntryPoints = Util.findEntryPoints();
        Scene.v().setEntryPoints(appEntryPoints);
        this.isPrepared = true;
    }

    public void runPTA() {
        if (!this.isPrepared) {
            Util.LOGGER.log(Level.WARNING, "App not perpared " + this.appFilePath);
            return;
        }
        PTA.runSparkPTA();
        this.pta = Scene.v().getPointsToAnalysis();
        this.isPTAFinished = true;
        CallGraph cg = Scene.v().getCallGraph();
        MethodOrMethodContext methodOrMethodContext = cg.sourceMethods().next();
        SootMethod sootMethod = methodOrMethodContext.method();
        Body body = sootMethod.getActiveBody();

        return;
    }

    public void generateApp2WebBridge() {
        String addJavascriptInterface = "addJavascriptInterface";
        String loadUrl = "loadUrl";

        for (SootClass cls : Scene.v().getClasses()) {
            for (SootMethod m : cls.getMethods()) {
                if (!m.hasActiveBody()) continue;
                Body b = m.getActiveBody();
                for (Unit u : b.getUnits()) {
                    if (u instanceof JInvokeStmt) {
                        InvokeExpr expr = ((JInvokeStmt) u).getInvokeExpr();
                        if (expr instanceof JVirtualInvokeExpr) {
                            JVirtualInvokeExpr virtual_expr = (JVirtualInvokeExpr) expr;

                            if (addJavascriptInterface.equals(virtual_expr.getMethod().getName())){
                                Value base = virtual_expr.getBase();
                                List<Value> args = virtual_expr.getArgs();
                                StringBuffer sb = new StringBuffer();
                                sb.append("\nClass: " + cls);
                                sb.append("\n\tMethod: " + m);
                                sb.append("\n\t\tUnit: " + u);
                                sb.append("\n\t\t\tBase: " + base);
                                if (base instanceof Local) {
                                    PointsToSet reaching_objects = pta.reachingObjects((Local) base);
                                    sb.append(String.format("\n\t\t\t\tposible string constants: %s",
                                            reaching_objects.possibleStringConstants()));
                                    sb.append(String.format("\n\t\t\t\tposible types: %s",
                                            reaching_objects.possibleTypes()));
                                }

                                for (Value arg : args) {
                                    sb.append("\n\t\t\tArg: " + arg);
                                    if (arg instanceof Local) {
                                        PointsToSet reaching_objects = pta.reachingObjects((Local) arg);
                                        sb.append(String.format("\n\t\t\t\tposible string constants: %s",
                                                reaching_objects.possibleStringConstants()));
                                        sb.append(String.format("\n\t\t\t\tposible types: %s",
                                                reaching_objects.possibleTypes()));
                                    }
                                }
                                sb.append("\n");
                                Util.LOGGER.log(Level.INFO, sb.toString());
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
