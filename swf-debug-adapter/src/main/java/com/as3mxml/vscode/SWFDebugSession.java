/*
Copyright 2016-2019 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.as3mxml.vscode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.as3mxml.vscode.debug.DebugSession;
import com.as3mxml.vscode.debug.events.BreakpointEvent;
import com.as3mxml.vscode.debug.events.InitializedEvent;
import com.as3mxml.vscode.debug.events.OutputEvent;
import com.as3mxml.vscode.debug.events.StoppedEvent;
import com.as3mxml.vscode.debug.events.TerminatedEvent;
import com.as3mxml.vscode.debug.events.ThreadEvent;
import com.as3mxml.vscode.debug.protocol.Request;
import com.as3mxml.vscode.debug.protocol.Response;
import com.as3mxml.vscode.debug.requests.AttachRequest;
import com.as3mxml.vscode.debug.requests.ConfigurationDoneRequest;
import com.as3mxml.vscode.debug.requests.ContinueRequest;
import com.as3mxml.vscode.debug.requests.EvaluateRequest;
import com.as3mxml.vscode.debug.requests.ExceptionInfoRequest;
import com.as3mxml.vscode.debug.requests.InitializeRequest;
import com.as3mxml.vscode.debug.requests.LaunchRequest;
import com.as3mxml.vscode.debug.requests.NextRequest;
import com.as3mxml.vscode.debug.requests.PauseRequest;
import com.as3mxml.vscode.debug.requests.ScopesRequest;
import com.as3mxml.vscode.debug.requests.SetBreakpointsRequest;
import com.as3mxml.vscode.debug.requests.SetVariableRequest;
import com.as3mxml.vscode.debug.requests.Source;
import com.as3mxml.vscode.debug.requests.SourceBreakpoint;
import com.as3mxml.vscode.debug.requests.StackTraceRequest;
import com.as3mxml.vscode.debug.requests.StepInRequest;
import com.as3mxml.vscode.debug.requests.StepOutRequest;
import com.as3mxml.vscode.debug.requests.VariablesRequest;
import com.as3mxml.vscode.debug.responses.Breakpoint;
import com.as3mxml.vscode.debug.responses.Capabilities;
import com.as3mxml.vscode.debug.responses.EvaluateResponseBody;
import com.as3mxml.vscode.debug.responses.ExceptionDetails;
import com.as3mxml.vscode.debug.responses.ExceptionInfoResponseBody;
import com.as3mxml.vscode.debug.responses.Scope;
import com.as3mxml.vscode.debug.responses.ScopesResponseBody;
import com.as3mxml.vscode.debug.responses.SetBreakpointsResponseBody;
import com.as3mxml.vscode.debug.responses.SetVariableResponseBody;
import com.as3mxml.vscode.debug.responses.StackFrame;
import com.as3mxml.vscode.debug.responses.StackTraceResponseBody;
import com.as3mxml.vscode.debug.responses.Thread;
import com.as3mxml.vscode.debug.responses.ThreadsResponseBody;
import com.as3mxml.vscode.debug.responses.Variable;
import com.as3mxml.vscode.debug.responses.VariablePresentationHint;
import com.as3mxml.vscode.debug.responses.VariablesResponseBody;
import com.as3mxml.vscode.debug.utils.DeviceInstallUtils;
import com.as3mxml.vscode.debug.utils.DeviceInstallUtils.DeviceCommandResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import flash.tools.debugger.AIRLaunchInfo;
import flash.tools.debugger.CommandLineException;
import flash.tools.debugger.DefaultDebuggerCallbacks;
import flash.tools.debugger.Frame;
import flash.tools.debugger.ILaunchNotification;
import flash.tools.debugger.InProgressException;
import flash.tools.debugger.Isolate;
import flash.tools.debugger.IsolateSession;
import flash.tools.debugger.Location;
import flash.tools.debugger.NoResponseException;
import flash.tools.debugger.NotConnectedException;
import flash.tools.debugger.NotSuspendedException;
import flash.tools.debugger.Player;
import flash.tools.debugger.PlayerDebugException;
import flash.tools.debugger.SourceFile;
import flash.tools.debugger.SuspendReason;
import flash.tools.debugger.SwfInfo;
import flash.tools.debugger.Value;
import flash.tools.debugger.VariableAttribute;
import flash.tools.debugger.VariableType;
import flash.tools.debugger.VersionException;
import flash.tools.debugger.events.BreakEvent;
import flash.tools.debugger.events.DebugEvent;
import flash.tools.debugger.events.ExceptionFault;
import flash.tools.debugger.events.FaultEvent;
import flash.tools.debugger.events.IsolateCreateEvent;
import flash.tools.debugger.events.IsolateExitEvent;
import flash.tools.debugger.events.SwfLoadedEvent;
import flash.tools.debugger.events.SwfUnloadedEvent;
import flash.tools.debugger.events.TraceEvent;
import flash.tools.debugger.expression.ASTBuilder;
import flash.tools.debugger.expression.ECMA;
import flash.tools.debugger.expression.NoSuchVariableException;
import flash.tools.debugger.expression.PlayerFaultException;
import flash.tools.debugger.expression.ValueExp;
import flash.tools.debugger.threadsafe.ThreadSafeBootstrap;
import flash.tools.debugger.threadsafe.ThreadSafeSession;
import flash.tools.debugger.threadsafe.ThreadSafeSessionManager;

public class SWFDebugSession extends DebugSession {
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_HX = ".hx";
    private static final String FILE_EXTENSION_EXE = ".exe";
    private static final String FILE_EXTENSION_BAT = ".bat";
    private static final String FILE_EXTENSION_XML = ".xml";
    private static final String ADL_BASE_NAME = "bin/adl";
    private static final String ADT_BASE_NAME = "bin/adt";
    private static final String ADB_BASE_NAME = "lib/android/bin/adb";
    private static final String IDB_BASE_NAME = "lib/aot/bin/iOSBin/idb";
    private static final String FLEXLIB_PROPERTY = "flexlib";
    private static final String WORKSPACE_PROPERTY = "workspace";
    private static final String SDK_PATH_SIGNATURE_UNIX = "/frameworks/projects/";
    private static final String SDK_PATH_SIGNATURE_WINDOWS = "\\frameworks\\projects\\";
    private static final String PLATFORM_IOS = "ios";
    private static final String PLATFORM_IOS_SIMULATOR = "ios_simulator";
    private static final long LOCALS_VALUE_ID = 1;
    private ThreadSafeSession swfSession;
    private List<IsolateWithState> isolates = new ArrayList<>();
    private Process swfRunProcess;
    private java.lang.Thread sessionThread;
    private boolean cancelRunner = false;
    private boolean waitingForResume = false;
    private FaultEvent previousFaultEvent = null;
    private Path flexlib;
    private Path flexHome;
    private Path adlPath;
    private Path adtPath;
    private Path adbPath;
    private Path idbPath;
    private Map<String, PendingBreakpoints> pendingBreakpoints;
    private Map<String, BreakpointExtras> savedBreakpointExtras;
    private int nextBreakpointID = 1;
    private String forwardedPortPlatform = null;
    private int forwardedPort = -1;
    private Integer mainSwfIndex = null;
    private String mainSwfPath = null;
    private boolean configDone = false;

    private class IsolateWithState {
        public IsolateWithState(Isolate isolate) {
            this.isolate = isolate;
        }

        public Isolate isolate;
        public boolean waitingForResume = false;
        public FaultEvent previousFaultEvent = null;
    }

    private class PendingBreakpoints {
        public PendingBreakpoints(SourceBreakpoint[] breakpoints) {
            this.breakpoints = breakpoints;
            idStart = nextBreakpointID;
        }

        SourceBreakpoint[] breakpoints;
        int idStart;
    }

    private class BreakpointExtras {
        public BreakpointExtras(Location location, String logMessage, String condition) {
            this.location = location;
            this.logMessage = logMessage;
            this.condition = condition;
        }

        public Location location;
        public String logMessage;
        public String condition;
    }

    private class RunProcessRunner implements Runnable {
        public RunProcessRunner() {
        }

        public void run() {
            while (true) {
                if (cancelRunner) {
                    break;
                }
                try {
                    swfRunProcess.exitValue();
                    cancelRunner = true;
                    sendEvent(new TerminatedEvent());
                } catch (IllegalThreadStateException e) {
                    // safe to ignore
                }
                try {
                    java.lang.Thread.sleep(50);
                } catch (InterruptedException ie) {
                }
            }
        }
    }

    private class SessionRunner implements Runnable {
        private boolean initialized = false;

        public SessionRunner() {
        }

        public void run() {
            while (true) {
                if (cancelRunner) {
                    break;
                }
                try {
                    boolean logPointOrFalseCondition = false;
                    while (swfSession.getEventCount() > 0) {
                        DebugEvent event = swfSession.nextEvent();
                        if (handleEvent(event)) {
                            logPointOrFalseCondition = true;
                        }
                        if (cancelRunner) {
                            // this might get set when handling the event
                            break;
                        }
                    }
                    if (cancelRunner) {
                        // this might get set when handling one of the events
                        break;
                    }
                    while (swfSession.isSuspended() && !waitingForResume) {
                        handleSuspended(logPointOrFalseCondition);
                    }
                    for (IsolateWithState isolateWithState : isolates) {
                        Isolate isolate = isolateWithState.isolate;
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        while (isolateSession.isSuspended() && !isolateWithState.waitingForResume) {
                            handleIsolateSuspended(isolateWithState);
                        }
                    }
                } catch (NotConnectedException e) {
                    cancelRunner = true;
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
                    sendEvent(new TerminatedEvent());
                } catch (Exception e) {
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
                }
                try {
                    java.lang.Thread.sleep(50);
                } catch (InterruptedException ie) {
                }
            }
        }

        private boolean handleEvent(DebugEvent event)
                throws NotConnectedException, NoResponseException, NotSuspendedException {
            if (event instanceof TraceEvent) {
                TraceEvent traceEvent = (TraceEvent) event;
                String output = traceEvent.information;
                if (output.length() == 0) {
                    // empty string or empty line added with \n
                    output = "\n";
                } else if (output.charAt(output.length() - 1) != '\n') {
                    output += '\n';
                }
                OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                // we can't populate the location for a trace() to the
                // console because the result of getFrames() is empty
                // when the runtime isn't suspended
                body.output = output;
                sendEvent(new OutputEvent(body));
            } else if (event instanceof FaultEvent) {
                FaultEvent faultEvent = (FaultEvent) event;
                if (faultEvent.isolateId == Isolate.DEFAULT_ID) {
                    previousFaultEvent = faultEvent;
                } else {
                    for (IsolateWithState isolateWithState : isolates) {
                        if (faultEvent.isolateId == isolateWithState.isolate.getId()) {
                            isolateWithState.previousFaultEvent = faultEvent;
                            break;
                        }
                    }
                }
                String output = faultEvent.information + "\n" + faultEvent.stackTrace();
                if (output.charAt(output.length() - 1) != '\n') {
                    output += '\n';
                }
                OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                populateLocationInOutputBody(faultEvent.isolateId, body);
                body.output = output;
                body.category = OutputEvent.CATEGORY_STDERR;
                sendEvent(new OutputEvent(body));

            } else if (event instanceof BreakEvent) {
                BreakEvent breakEvent = (BreakEvent) event;
                for (BreakpointExtras extras : savedBreakpointExtras.values()) {
                    Location location = extras.location;
                    boolean logPointOrFalseCondition = false;
                    if (breakEvent.fileId == location.getFile().getId() && breakEvent.line == location.getLine()) {
                        boolean conditionIsTrue = true;
                        if (extras.condition != null) {
                            conditionIsTrue = false;
                            Frame[] swfFrames = swfSession.getFrames();
                            if (swfFrames.length > 0) {
                                Frame swfFrame = swfFrames[0];
                                try {
                                    ASTBuilder builder = new ASTBuilder(false);
                                    ValueExp result = builder.parse(new StringReader(extras.condition));
                                    Object evaluateResult = result
                                            .evaluate(new SWFExpressionContext(swfSession, location.getIsolateId(),
                                                    swfFrame));
                                    if (evaluateResult instanceof flash.tools.debugger.Variable) {
                                        flash.tools.debugger.Variable evaluateVar = (flash.tools.debugger.Variable) evaluateResult;
                                        conditionIsTrue = ECMA.toBoolean(evaluateVar.getValue());
                                    } else if (evaluateResult instanceof Value) {
                                        Value evaluateValue = (Value) evaluateResult;
                                        conditionIsTrue = ECMA.toBoolean(evaluateValue);
                                    } else {
                                        conditionIsTrue = Boolean.TRUE.equals(evaluateResult);
                                    }
                                    logPointOrFalseCondition = !conditionIsTrue;
                                } catch (Exception e) {
                                }
                            }
                        }
                        if (conditionIsTrue && extras.logMessage != null) {
                            OutputEvent.OutputBody body = new OutputEvent.OutputBody();
                            populateLocationInOutputBody(breakEvent.isolateId, body);
                            body.output = extras.logMessage;
                            sendEvent(new OutputEvent(body));
                            logPointOrFalseCondition = true;
                        }
                    }
                    if (logPointOrFalseCondition) {
                        return true;
                    }
                }
            } else if (event instanceof IsolateCreateEvent) {
                // a worker has been created
                IsolateCreateEvent isolateEvent = (IsolateCreateEvent) event;
                Isolate isolate = isolateEvent.isolate;
                IsolateWithState isolateWithState = new IsolateWithState(isolate);
                isolates.add(isolateWithState);

                ThreadEvent.ThreadBody body = new ThreadEvent.ThreadBody();
                body.reason = ThreadEvent.REASON_STARTED;
                body.threadID = isolate.getId();
                sendEvent(new ThreadEvent(body));

                refreshPendingBreakpoints();
            } else if (event instanceof IsolateExitEvent) {
                // a worker has exited
                IsolateExitEvent isolateEvent = (IsolateExitEvent) event;
                Isolate isolate = isolateEvent.isolate;
                isolates.removeIf((isolateWithState) -> isolate.equals(isolateWithState.isolate));

                ThreadEvent.ThreadBody body = new ThreadEvent.ThreadBody();
                body.reason = ThreadEvent.REASON_EXITED;
                body.threadID = isolate.getId();
                sendEvent(new ThreadEvent(body));
            } else if (event instanceof SwfLoadedEvent) {
                SwfLoadedEvent loadEvent = (SwfLoadedEvent) event;
                if (mainSwfPath == null) {
                    mainSwfPath = loadEvent.path;
                    mainSwfIndex = loadEvent.index;
                }
                refreshPendingBreakpoints();
            } else if (event instanceof SwfUnloadedEvent) {
                SwfUnloadedEvent unloadEvent = (SwfUnloadedEvent) event;
                if ((mainSwfPath == null && mainSwfPath == null)
                        || (unloadEvent.path.equals(mainSwfPath) && unloadEvent.index == mainSwfIndex)) {
                    cancelRunner = true;
                    sendEvent(new TerminatedEvent());
                }
            }
            return false;
        }

        private void handleSuspended(boolean logPoint)
                throws NotConnectedException, NoResponseException, NotSuspendedException {
            StoppedEvent.StoppedBody body = null;
            switch (swfSession.suspendReason()) {
                case SuspendReason.ScriptLoaded: {
                    if (initialized) {
                        if (configDone) {
                            refreshPendingBreakpoints();
                            swfSession.resume();
                        } else {
                            waitingForResume = true;
                        }
                    } else {
                        // initialize when the first script is loaded
                        initialized = true;
                        sendEvent(new InitializedEvent());
                    }
                    break;
                }
                case SuspendReason.Breakpoint: {
                    if (logPoint) {
                        // if it was a logpoint, then resume
                        // immediately because we should not stop
                        swfSession.resume();
                    } else {
                        body = new StoppedEvent.StoppedBody();
                        body.reason = StoppedEvent.REASON_BREAKPOINT;
                    }
                    break;
                }
                case SuspendReason.StopRequest: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_PAUSE;
                    break;
                }
                case SuspendReason.Fault: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_EXCEPTION;
                    body.description = "Paused on exception";
                    if (previousFaultEvent != null) {
                        body.text = previousFaultEvent.information;
                    }
                    break;
                }
                case SuspendReason.Unknown: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_UNKNOWN;
                    break;
                }
                default: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_UNKNOWN;
                    sendOutputEvent("Unknown suspend reason: " + swfSession.suspendReason() + "\n");
                }
            }
            if (body != null) {
                waitingForResume = true;
                body.threadId = Isolate.DEFAULT_ID;
                sendEvent(new StoppedEvent(body));
            }
        }

        private void handleIsolateSuspended(IsolateWithState isolateWithState)
                throws NotConnectedException, NoResponseException, NotSuspendedException {
            Isolate isolate = isolateWithState.isolate;
            IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());

            StoppedEvent.StoppedBody body = null;
            switch (isolateSession.suspendReason()) {
                case SuspendReason.ScriptLoaded: {
                    isolateSession.resume();
                    break;
                }
                case SuspendReason.Breakpoint: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_BREAKPOINT;
                    break;
                }
                case SuspendReason.StopRequest: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_PAUSE;
                    break;
                }
                case SuspendReason.Fault: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_EXCEPTION;
                    body.description = "Paused on exception";
                    if (isolateWithState.previousFaultEvent != null) {
                        body.text = isolateWithState.previousFaultEvent.information;
                    }
                    break;
                }
                case SuspendReason.Unknown: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_UNKNOWN;
                    break;
                }
                default: {
                    body = new StoppedEvent.StoppedBody();
                    body.reason = StoppedEvent.REASON_UNKNOWN;
                    sendOutputEvent("Unknown suspend reason: " + isolateSession.suspendReason()
                            + " for isolate with ID: " + isolate.getId() + "\n");
                }
            }
            if (body != null) {
                isolateWithState.waitingForResume = true;
                body.threadId = isolate.getId();
                sendEvent(new StoppedEvent(body));
            }
        }

        private void populateLocationInOutputBody(int isolateId, OutputEvent.OutputBody body) {
            try {
                Frame[] swfFrames = getFramesForIsolate(isolateId);
                if (swfFrames.length > 0) {
                    Frame swfFrame = swfFrames[0];
                    Location location = swfFrame.getLocation();
                    SourceFile file = location.getFile();
                    if (file != null) {
                        Source source = sourceFileToSource(file);
                        body.source = source;
                        body.line = location.getLine();
                        body.column = 0;
                    }
                }
            } catch (NotConnectedException e) {
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
                return;
            }
        }
    }

    public SWFDebugSession() {
        super(false);
        pendingBreakpoints = new HashMap<>();
        savedBreakpointExtras = new HashMap<>();
        String flexlibPath = System.getProperty(FLEXLIB_PROPERTY);
        if (flexlibPath != null) {
            flexlib = Paths.get(flexlibPath);
            flexHome = flexlib.getParent();
            String adlRelativePath = ADL_BASE_NAME;
            String adtRelativePath = ADT_BASE_NAME;
            String adbRelativePath = ADB_BASE_NAME;
            String idbRelativePath = IDB_BASE_NAME;
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                adlRelativePath += FILE_EXTENSION_EXE;
                adtRelativePath += FILE_EXTENSION_BAT;
                adbRelativePath += FILE_EXTENSION_EXE;
                idbRelativePath += FILE_EXTENSION_EXE;
            }
            adlPath = flexHome.resolve(adlRelativePath);
            if (!adlPath.toFile().exists()) {
                adlPath = null;
            }
            adtPath = flexHome.resolve(adtRelativePath);
            if (!adtPath.toFile().exists()) {
                adtPath = null;
            }
            adbPath = flexHome.resolve(adbRelativePath);
            if (!adbPath.toFile().exists()) {
                adbPath = null;
            }
            idbPath = flexHome.resolve(idbRelativePath);
            if (!idbPath.toFile().exists()) {
                idbPath = null;
            }
        }
    }

    public void initialize(Response response, InitializeRequest.InitializeRequestArguments args) {
        OutputEvent.OutputBody body = new OutputEvent.OutputBody();
        sendEvent(new OutputEvent(body));

        Capabilities capabilities = new Capabilities();
        capabilities.supportsExceptionInfoRequest = true;
        capabilities.supportsConditionalBreakpoints = true;
        capabilities.supportsLogPoints = true;
        capabilities.supportsSetVariable = true;
        capabilities.supportsConfigurationDoneRequest = true;
        capabilities.supportsEvaluateForHovers = true;
        sendResponse(response, capabilities);
    }

    public void launch(Response response, LaunchRequest.LaunchRequestArguments args) {
        SWFLaunchRequestArguments swfArgs = (SWFLaunchRequestArguments) args;
        ThreadSafeSessionManager manager = ThreadSafeBootstrap.sessionManager();
        swfSession = null;
        swfRunProcess = null;
        forwardedPortPlatform = null;
        forwardedPort = -1;
        try {
            manager.startListening();
            if (manager.supportsLaunch()) {
                String program = swfArgs.program;
                if (!program.startsWith("http:") && !program.startsWith("https:")) {
                    Path programPath = Paths.get(program);
                    if (!programPath.isAbsolute()) {
                        // if it's not an absolute path, we'll treat it as a
                        // relative path within the workspace
                        String workspacePath = System.getProperty(WORKSPACE_PROPERTY);
                        if (workspacePath != null) {
                            program = Paths.get(workspacePath).resolve(programPath).toAbsolutePath().toString();
                        }
                    }
                }
                AIRLaunchInfo airLaunchInfo = null;
                if (program.endsWith(FILE_EXTENSION_XML)) {
                    String extdir = swfArgs.extdir;
                    if (extdir != null) {
                        Path extdirPath = Paths.get(extdir);
                        if (!extdirPath.isAbsolute()) {
                            String workspacePath = System.getProperty(WORKSPACE_PROPERTY);
                            if (workspacePath != null) {
                                extdir = Paths.get(workspacePath).resolve(extdirPath).toAbsolutePath().toString();
                            }
                        }
                    }
                    airLaunchInfo = new AIRLaunchInfo();
                    airLaunchInfo.profile = swfArgs.profile;
                    airLaunchInfo.screenSize = swfArgs.screensize;
                    airLaunchInfo.dpi = swfArgs.screenDPI;
                    airLaunchInfo.versionPlatform = swfArgs.versionPlatform;
                    if (swfArgs.rootDirectory != null) {
                        Path rootDir = Paths.get(swfArgs.rootDirectory);
                        if (!rootDir.isAbsolute()) {
                            String workspacePath = System.getProperty(WORKSPACE_PROPERTY);
                            if (workspacePath != null) {
                                rootDir = Paths.get(workspacePath).resolve(swfArgs.rootDirectory).toAbsolutePath();
                            }
                        }
                        airLaunchInfo.applicationContentRootDir = rootDir.toFile();
                    }
                    if (swfArgs.runtimeExecutable != null) {
                        airLaunchInfo.airDebugLauncher = Paths.get(swfArgs.runtimeExecutable).toFile();
                    } else if (adlPath != null) {
                        airLaunchInfo.airDebugLauncher = adlPath.toFile();
                    } else {
                        sendErrorResponse(response, 10001,
                                "Error launching SWF debug session. Runtime not found for program: " + program);
                        return;
                    }
                    airLaunchInfo.extDir = extdir;
                    airLaunchInfo.applicationArgumentsArray = swfArgs.args;
                }

                Player player = null;
                CustomRuntimeLauncher launcher = null;
                // setting environment variables requires a runtime exectutable
                if ((swfArgs.runtimeArgs != null || (swfArgs.env != null && !swfArgs.env.isEmpty()))
                        && swfArgs.runtimeExecutable == null) {
                    Player customPlayer = manager.playerForUri(program, airLaunchInfo);
                    if (customPlayer != null) {
                        File customPlayerFile = customPlayer.getPath();
                        if (customPlayerFile != null) {
                            swfArgs.runtimeExecutable = customPlayer.getPath().getAbsolutePath();
                        }
                    }
                    if (swfArgs.runtimeExecutable == null) {
                        sendErrorResponse(response, 10001,
                                "Error launching SWF debug session. Runtime not found for program: " + program);
                        return;
                    }
                }
                if (swfArgs.runtimeExecutable != null) {
                    // if runtimeExecutable is specified, we'll launch that
                    launcher = new CustomRuntimeLauncher(swfArgs.runtimeExecutable, swfArgs.runtimeArgs, swfArgs.env);
                    if (airLaunchInfo != null) {
                        launcher.isAIR = true;
                    }
                } else {
                    // otherwise, let the SWF debugger automatically figure out
                    // which runtime is required based on the program path
                    String playerPath = program;
                    try {

                        URI uri = Paths.get(playerPath).toUri();
                        playerPath = uri.toString();
                    } catch (Exception e) {
                        // safe to ignore
                    }
                    player = manager.playerForUri(playerPath, airLaunchInfo);
                    if (player == null && !playerPath.startsWith("http:") && !playerPath.startsWith("https:")
                            && playerPath.endsWith(".swf")) {
                        // fallback: try to find standalone Flash Player
                        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                            launcher = findWindowsStandalonePlayer();
                        } else if (!System.getProperty("os.name").toLowerCase().startsWith("mac os")) // linux
                        {
                            launcher = findLinuxStandalonePlayer();
                        }
                    }
                }
                if (player == null && launcher == null) {
                    sendErrorResponse(response, 10001,
                            "Error launching SWF debug session. Runtime not found for program: " + program);
                    return;
                }
                if (swfArgs.noDebug) {
                    if (launcher != null) {
                        swfRunProcess = manager.launchForRun(program, airLaunchInfo, null, new RunLaunchNotification(),
                                launcher);
                    } else {
                        swfRunProcess = manager.launchForRun(program, airLaunchInfo, null, new RunLaunchNotification());
                    }
                } else {
                    // notice that we use the value of launcher if it isn't null, but
                    // we don't actually use the value of player. player's purpose is
                    // more about verifying that a runtime can be auto-detected.
                    if (launcher != null) {
                        swfSession = (ThreadSafeSession) manager.launch(program, airLaunchInfo, !swfArgs.noDebug, null,
                                null, launcher);
                    } else {
                        swfSession = (ThreadSafeSession) manager.launch(program, airLaunchInfo, !swfArgs.noDebug, null,
                                null);
                    }
                }
            }
        } catch (IOException e) {
            sendErrorResponse(response, 10001, getLaunchFailureMessage(e));
            return;
        }
        if (swfSession != null) {
            try {
                swfSession.bind();
            } catch (VersionException e) {
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                sendErrorResponse(response, 10001, "Error launching SWF debug session.\n" + writer.toString());
                return;
            }
        }
        try {
            manager.stopListening();
        } catch (IOException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorResponse(response, 10001, "Exception in debugger on stop listening:\n" + writer.toString());
            return;
        }
        sendResponse(response);
        cancelRunner = false;
        if (swfSession != null) {
            sessionThread = new java.lang.Thread(new SessionRunner());
            sessionThread.start();
        } else if (swfRunProcess != null) {
            sessionThread = new java.lang.Thread(new RunProcessRunner());
            sessionThread.start();
        }
    }

    private class RunLaunchNotification implements ILaunchNotification {
        public void notify(IOException e) {
            if (e == null) {
                return;
            }
            sendErrorOutputEvent(getLaunchFailureMessage(e));
        }
    }

    private String getLaunchFailureMessage(IOException e) {
        if (e instanceof CommandLineException) {
            CommandLineException cle = (CommandLineException) e;
            return "Error launching SWF debug session. Process exited with code: " + cle.getExitValue() + "\n\n"
                    + cle.getMessage() + "\n\n" + cle.getCommandOutput();
        } else if (e instanceof FileNotFoundException) {
            return "Error launching SWF debug session. File not found: " + e.getMessage();
        } else {
            return "Error launching SWF debug session.\n" + e.getMessage();
        }
    }

    /**
     * On Windows, if the standalone Flash Player wasn't found by the debugger,
     * we're going try one last thing. We check the registry to see if the user made
     * a file association in explorer.
     */
    private CustomRuntimeLauncher findWindowsStandalonePlayer() {
        try {
            DefaultDebuggerCallbacks callbacks = new DefaultDebuggerCallbacks();
            String association = callbacks.queryWindowsRegistry(
                    "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.swf\\UserChoice",
                    "ProgId");
            if (association != null) {
                String path = callbacks
                        .queryWindowsRegistry("HKEY_CLASSES_ROOT\\" + association + "\\shell\\open\\command", null);
                if (path != null) {
                    if (path.startsWith("\"")) {
                        // strip any quotes that might be wrapping
                        // the executable path
                        path = path.substring(1, path.indexOf("\"", 1));
                    }
                    return new CustomRuntimeLauncher(path);
                }
            }
        } catch (IOException e) {
            // safe to ignore
        }
        return null;
    }

    /**
     * On Linux, if the standalone Flash Player wasn't found by the debugger, we're
     * going try one last thing. We check a different default file name that might
     * exist that the debugger doesn't know about.
     */
    private CustomRuntimeLauncher findLinuxStandalonePlayer() {
        try {
            String[] cmd = { "/bin/sh", "-c", "which flashplayerdebugger" };
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                File f = new File(line);
                if (f.exists()) {
                    return new CustomRuntimeLauncher(f.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            // safe to ignore
        }
        return null;
    }

    public void attach(Response response, AttachRequest.AttachRequestArguments args) {
        SWFAttachRequestArguments swfArgs = (SWFAttachRequestArguments) args;
        forwardedPortPlatform = null;
        forwardedPort = -1;
        Path platformSdkPath = null;
        if (swfArgs.platformsdk != null) {
            platformSdkPath = Paths.get(swfArgs.platformsdk);
        }
        if (swfArgs.platform != null) {
            Path workspacePath = Paths.get(System.getProperty(WORKSPACE_PROPERTY));
            if (swfArgs.bundle != null) {
                sendOutputEvent("Preparing to install Adobe AIR application...\n");
            }
            if (swfArgs.applicationID != null && swfArgs.bundle != null // don't uninstall unless also installing
                    && !uninstallApp(workspacePath, swfArgs.platform, swfArgs.applicationID, platformSdkPath)) {
                response.success = false;
                sendResponse(response);
                return;
            }
            if (swfArgs.bundle != null
                    && !installApp(workspacePath, swfArgs.platform, Paths.get(swfArgs.bundle), platformSdkPath)) {
                response.success = false;
                sendResponse(response);
                return;
            }
            if (swfArgs.connect && !forwardPort(workspacePath, swfArgs.platform, swfArgs.port)) {
                response.success = false;
                sendResponse(response);
                return;
            }
            if (!launchApp(workspacePath, adtPath, swfArgs.platform, swfArgs.applicationID, platformSdkPath)) {
                response.success = false;
                sendResponse(response);
                return;
            }
        }

        boolean success = attach(response, swfArgs);
        if (!success) {
            cleanupForwardedPort();
        }
    }

    private void sendOutputEvent(String message) {
        OutputEvent.OutputBody body = new OutputEvent.OutputBody();
        body.output = message;
        sendEvent(new OutputEvent(body));
    }

    private void sendErrorOutputEvent(String message) {
        OutputEvent.OutputBody body = new OutputEvent.OutputBody();
        body.output = message;
        body.category = OutputEvent.CATEGORY_STDERR;
        sendEvent(new OutputEvent(body));
    }

    private boolean uninstallApp(Path workspacePath, String platform, String applicationID, Path platformSdkPath) {
        DeviceCommandResult uninstallResult = DeviceInstallUtils.runUninstallCommand(platform, applicationID,
                workspacePath, adtPath, platformSdkPath);
        if (uninstallResult.error) {
            sendErrorOutputEvent(uninstallResult.message + "\n");
            return false;
        }
        return true;
    }

    private boolean installApp(Path workspacePath, String platform, Path bundlePath, Path platformSdkPath) {
        sendOutputEvent("Installing Adobe AIR application...\n");
        DeviceCommandResult installResult = DeviceInstallUtils.runInstallCommand(platform, bundlePath, workspacePath,
                adtPath, platformSdkPath);
        if (installResult.error) {
            sendErrorOutputEvent(installResult.message + "\n");
            return false;
        }
        return true;
    }

    private boolean forwardPort(Path workspacePath, String platform, int port) {
        sendOutputEvent("Forwarding port " + port + " over USB...\n");
        DeviceCommandResult forwardPortResult = DeviceInstallUtils.forwardPortCommand(platform, port, workspacePath,
                adbPath, idbPath);
        if (forwardPortResult.error) {
            sendErrorOutputEvent(forwardPortResult.message + "\n");
            return false;
        }
        forwardedPort = port;
        forwardedPortPlatform = platform;
        return true;
    }

    private boolean launchApp(Path workspacePath, Path adtPath, String platform, String applicationID,
            Path platformSdkPath) {
        if (platform.equals(PLATFORM_IOS)) {
            // ADT can't launch an iOS application automatically
            sendOutputEvent(
                    "\033[0;95mDebugger ready to attach. You must launch your application manually on the iOS device.\u001B[0m\n");
            return true;
        }
        if (applicationID == null) {
            // ADT can't launch an Android application automatically with an ID
            sendOutputEvent(
                    "\033[0;95mDebugger ready to attach. You must launch your application manually on the Android device.\u001B[0m\n");
            return true;
        }
        if (PLATFORM_IOS_SIMULATOR.equals(platform)) {
            sendOutputEvent("Launching Adobe AIR application on iOS Simulator...\n");
        } else {
            sendOutputEvent("Launching Adobe AIR application on device...\n");
        }
        DeviceCommandResult launchResult = DeviceInstallUtils.runLaunchCommand(platform, applicationID, workspacePath,
                adtPath, platformSdkPath);
        if (launchResult.error) {
            sendErrorOutputEvent(launchResult.message + "\n");
            return false;
        }
        sendOutputEvent("\033[0;92mInstallation and launch completed successfully.\u001B[0m\n");
        return true;
    }

    private boolean attach(Response response, SWFAttachRequestArguments args) {
        boolean success = true;
        ThreadSafeSessionManager manager = ThreadSafeBootstrap.sessionManager();
        swfSession = null;
        swfRunProcess = null;
        try {
            manager.startListening();
            if (args.connect) {
                swfSession = (ThreadSafeSession) manager.connect(args.port, null);
            } else {
                swfSession = (ThreadSafeSession) manager.accept(null);
            }
        } catch (ConnectException e) {
            success = false;
        } catch (IOException e) {
            success = false;
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        if (response.success) {
            try {
                swfSession.bind();
            } catch (VersionException e) {
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
            }
            try {
                manager.stopListening();
            } catch (IOException e) {
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
            }

            cancelRunner = false;
            sessionThread = new java.lang.Thread(new SessionRunner());
            sessionThread.start();
        }
        response.success = success;
        sendResponse(response);
        return success;
    }

    public void disconnect(Response response, Request.RequestArguments args) {
        cleanupForwardedPort();
        if (sessionThread != null) {
            cancelRunner = true;
            sessionThread = null;
        }
        if (swfSession != null) {
            swfSession.terminate();
            swfSession = null;
        }
        if (swfRunProcess != null) {
            swfRunProcess.destroy();
            swfRunProcess = null;
        }
        sendResponse(response);
    }

    private void cleanupForwardedPort() {
        if (forwardedPort == -1) {
            return;
        }
        try {
            Path workspacePath = Paths.get(System.getProperty(WORKSPACE_PROPERTY));
            DeviceInstallUtils.stopForwardPortCommand(forwardedPortPlatform, forwardedPort, workspacePath, adbPath,
                    idbPath);
        } finally {
            forwardedPortPlatform = null;
            forwardedPort = -1;
        }
    }

    public void configurationDone(Response response, ConfigurationDoneRequest.ConfigurationDoneArguments arguments) {
        try {
            refreshPendingBreakpoints();
            swfSession.resume();
            stopWaitingForResume(Isolate.DEFAULT_ID);
            configDone = true;
        } catch (NotSuspendedException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        } catch (NoResponseException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        } catch (NotConnectedException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void setBreakpoints(Response response, SetBreakpointsRequest.SetBreakpointsArguments arguments) {
        String path = arguments.source.path;
        List<Breakpoint> breakpoints = setBreakpoints(path, arguments.breakpoints);
        sendResponse(response, new SetBreakpointsResponseBody(breakpoints));
    }

    private List<Breakpoint> setBreakpoints(String path, SourceBreakpoint[] breakpoints) {
        // start by trying to find the file ID for this path
        Path pathAsPath = Paths.get(path);
        SourceFile foundSourceFile = null;
        boolean badExtension = false;
        try {
            SwfInfo[] swfs = swfSession.getSwfs();
            for (SwfInfo swf : swfs) {
                if (swf == null) {
                    // for some reason, the array may contain null values (vscode-swf-debug#25)
                    continue;
                }
                SourceFile[] sourceFiles = swf.getSourceList(swfSession);
                for (SourceFile sourceFile : sourceFiles) {
                    Path sourceFilePath = null;
                    try {
                        String sourceFileFullPath = sourceFile.getFullPath();
                        sourceFilePath = Paths.get(sourceFileFullPath);
                    } catch (InvalidPathException e) {
                        badExtension = true;
                        continue;
                    }
                    // we can't check if the String paths are equal due to
                    // file system case sensitivity.
                    if (pathAsPath.equals(sourceFilePath)) {
                        if (path.endsWith(FILE_EXTENSION_AS) || path.endsWith(FILE_EXTENSION_MXML)
                                || path.endsWith(FILE_EXTENSION_HX)) {
                            foundSourceFile = sourceFile;
                        } else {
                            badExtension = true;
                        }
                        break;
                    }
                }
                if (foundSourceFile != null) {
                    break;
                }
            }
            if (foundSourceFile == null) {
                for (IsolateWithState isolateWithState : isolates) {
                    Isolate isolate = isolateWithState.isolate;
                    IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                    swfs = isolateSession.getSwfs();
                    for (SwfInfo swf : swfs) {
                        SourceFile[] sourceFiles = swf.getSourceList(swfSession);
                        for (SourceFile sourceFile : sourceFiles) {
                            Path sourceFilePath = null;
                            try {
                                String sourceFileFullPath = sourceFile.getFullPath();
                                sourceFilePath = Paths.get(sourceFileFullPath);
                            } catch (InvalidPathException e) {
                                badExtension = true;
                                continue;
                            }
                            // we can't check if the String paths are equal due to
                            // file system case sensitivity.
                            if (pathAsPath.equals(sourceFilePath)) {
                                if (path.endsWith(FILE_EXTENSION_AS) || path.endsWith(FILE_EXTENSION_MXML)
                                        || path.endsWith(FILE_EXTENSION_HX)) {
                                    foundSourceFile = sourceFile;
                                } else {
                                    badExtension = true;
                                }
                                break;
                            }
                        }
                        if (foundSourceFile != null) {
                            break;
                        }
                    }
                    if (foundSourceFile != null) {
                        break;
                    }
                }
            }
        } catch (InProgressException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        } catch (NoResponseException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        if (foundSourceFile == null && !badExtension) {
            // the file was not found, but it has a supported extension,
            // so we'll try to add it again later.
            // SWF is a streaming format, so not all bytecode is loaded
            // immediately.
            pendingBreakpoints.put(path, new PendingBreakpoints(breakpoints));
            foundSourceFile = null;
        }
        try {
            // clear all old breakpoints for this file because our new list
            // doesn't specify exactly which ones are cleared
            for (Location location : swfSession.getBreakpointList()) {
                if (pathAsPath.equals(Paths.get(location.getFile().getFullPath()))) {
                    swfSession.clearBreakpoint(location);
                }
            }
            savedBreakpointExtras.remove(path);
        } catch (NoResponseException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        } catch (NotConnectedException e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        List<Breakpoint> result = new ArrayList<>();
        for (int i = 0, count = breakpoints.length; i < count; i++) {
            SourceBreakpoint sourceBreakpoint = breakpoints[i];
            int sourceLine = sourceBreakpoint.line;
            Breakpoint responseBreakpoint = new Breakpoint();
            responseBreakpoint.line = sourceLine;
            responseBreakpoint.id = nextBreakpointID;
            nextBreakpointID++;
            if (foundSourceFile == null) {
                // we couldn't find the file, so we can't verify this breakpoint
                responseBreakpoint.verified = false;
            } else {
                // we found the file, so let's try to add this breakpoint
                // it may not work, but at least we tried!
                responseBreakpoint.source = sourceFileToSource(foundSourceFile);
                try {
                    Location breakpointLocation = swfSession.setBreakpoint(foundSourceFile.getId(), sourceLine);
                    if (breakpointLocation != null) {
                        verifyBreakpoint(path, breakpointLocation, sourceBreakpoint, responseBreakpoint);
                    }
                    if (!responseBreakpoint.verified) {
                        for (IsolateWithState isolateWithState : isolates) {
                            Isolate isolate = isolateWithState.isolate;
                            IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                            breakpointLocation = isolateSession.setBreakpoint(foundSourceFile.getId(), sourceLine);
                            if (breakpointLocation != null) {
                                verifyBreakpoint(path, breakpointLocation, sourceBreakpoint, responseBreakpoint);
                                break;
                            }
                        }
                    }
                    // setBreakpoint() may return null if the breakpoint
                    // could not be set. that's fine. the user will simply
                    // see that the breakpoint is not verified.
                } catch (NoResponseException e) {
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
                    responseBreakpoint.verified = false;
                } catch (NotConnectedException e) {
                    StringWriter writer = new StringWriter();
                    e.printStackTrace(new PrintWriter(writer));
                    sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
                    responseBreakpoint.verified = false;
                }
            }
            result.add(responseBreakpoint);
        }
        return result;
    }

    private void verifyBreakpoint(String path, Location breakpointLocation, SourceBreakpoint sourceBreakpoint,
            Breakpoint responseBreakpoint) {
        // I don't know if the line could change, but might as well
        // use the one returned by the location
        responseBreakpoint.line = breakpointLocation.getLine();
        responseBreakpoint.verified = true;
        String logMessage = sourceBreakpoint.logMessage;
        String condition = sourceBreakpoint.condition;
        if (logMessage != null || condition != null) {
            savedBreakpointExtras.put(path, new BreakpointExtras(breakpointLocation, logMessage, condition));
        }
    }

    private void refreshPendingBreakpoints() {
        if (pendingBreakpoints.isEmpty()) {
            return;
        }
        // if we weren't able to add some breakpoints earlier because we
        // we couldn't find the source file, try again!
        Iterator<String> iterator = pendingBreakpoints.keySet().iterator();
        while (iterator.hasNext()) {
            String path = iterator.next();
            PendingBreakpoints pending = pendingBreakpoints.get(path);
            int idToRestore = nextBreakpointID;
            nextBreakpointID = pending.idStart;
            List<Breakpoint> breakpoints = setBreakpoints(path, pending.breakpoints);
            nextBreakpointID = idToRestore;
            boolean hasVerified = false;
            for (Breakpoint breakpoint : breakpoints) {
                // this breakpoint was unverified, but it may be verified
                // now, so let the editor know the updated status
                BreakpointEvent.BreakpointBody body = new BreakpointEvent.BreakpointBody();
                body.breakpoint = breakpoint;
                body.reason = BreakpointEvent.REASON_CHANGED;
                if (breakpoint.verified) {
                    // if any of the breakpoints are verified, that's good
                    // enough. we shouldn't keep trying the other unverified
                    // ones because they will probably always fail
                    hasVerified = true;
                }
                sendEvent(new BreakpointEvent(body));
            }
            if (hasVerified) {
                iterator.remove();
            }
        }
    }

    public void setExceptionBreakpoints(Response response, Request.RequestArguments arguments) {
        sendResponse(response);
    }

    public void continueCommand(Response response, ContinueRequest.ContinueArguments arguments) {
        try {
            if (arguments.threadId == Isolate.DEFAULT_ID) {
                if (!swfSession.isSuspended()) {
                    response.success = false;
                    sendResponse(response);
                    return;
                }
                swfSession.resume();
                stopWaitingForResume(Isolate.DEFAULT_ID);
            } else {
                // worker
                boolean foundWorker = false;
                for (IsolateWithState isolateWithState : isolates) {
                    Isolate isolate = isolateWithState.isolate;
                    if (arguments.threadId == isolate.getId()) {
                        foundWorker = true;
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        if (!isolateSession.isSuspended()) {
                            response.success = false;
                            sendResponse(response);
                            return;
                        }
                        isolateSession.resume();
                        stopWaitingForResume(isolate.getId());
                    }
                }
                if (!foundWorker) {
                    response.success = false;
                }
            }
        } catch (NotSuspendedException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (NoResponseException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (Exception e) {
            response.success = false;
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void next(Response response, NextRequest.NextArguments arguments) {
        try {
            if (arguments.threadId == Isolate.DEFAULT_ID) {
                if (!swfSession.isSuspended()) {
                    response.success = false;
                    sendResponse(response);
                    return;
                }
                swfSession.stepOver();
                stopWaitingForResume(Isolate.DEFAULT_ID);
            } else {
                // worker
                boolean foundWorker = false;
                for (IsolateWithState isolateWithState : isolates) {
                    Isolate isolate = isolateWithState.isolate;
                    if (arguments.threadId == isolate.getId()) {
                        foundWorker = true;
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        if (!isolateSession.isSuspended()) {
                            response.success = false;
                            sendResponse(response);
                            return;
                        }
                        isolateSession.stepOver();
                        stopWaitingForResume(isolate.getId());
                    }
                }
                if (!foundWorker) {
                    response.success = false;
                }
            }
        } catch (NotSuspendedException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (NoResponseException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (Exception e) {
            response.success = false;
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void stepIn(Response response, StepInRequest.StepInArguments arguments) {
        try {
            if (arguments.threadId == Isolate.DEFAULT_ID) {
                if (!swfSession.isSuspended()) {
                    response.success = false;
                    sendResponse(response);
                    return;
                }
                swfSession.stepInto();
                stopWaitingForResume(Isolate.DEFAULT_ID);
            } else {
                // worker
                boolean foundWorker = false;
                for (IsolateWithState isolateWithState : isolates) {
                    Isolate isolate = isolateWithState.isolate;
                    if (arguments.threadId == isolate.getId()) {
                        foundWorker = true;
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        if (!isolateSession.isSuspended()) {
                            response.success = false;
                            sendResponse(response);
                            return;
                        }
                        isolateSession.stepInto();
                        stopWaitingForResume(isolate.getId());
                    }
                }
                if (!foundWorker) {
                    response.success = false;
                }
            }
        } catch (NotSuspendedException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (NoResponseException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (Exception e) {
            response.success = false;
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void stepOut(Response response, StepOutRequest.StepOutArguments arguments) {
        try {
            if (arguments.threadId == Isolate.DEFAULT_ID) {
                if (!swfSession.isSuspended()) {
                    response.success = false;
                    sendResponse(response);
                    return;
                }
                swfSession.stepOut();
                stopWaitingForResume(Isolate.DEFAULT_ID);
            } else {
                // worker
                boolean foundWorker = false;
                for (IsolateWithState isolateWithState : isolates) {
                    Isolate isolate = isolateWithState.isolate;
                    if (arguments.threadId == isolate.getId()) {
                        foundWorker = true;
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        if (!isolateSession.isSuspended()) {
                            response.success = false;
                            sendResponse(response);
                            return;
                        }
                        isolateSession.stepOut();
                        stopWaitingForResume(isolate.getId());
                    }
                }
                if (!foundWorker) {
                    response.success = false;
                }
            }
        } catch (NotSuspendedException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (NoResponseException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (Exception e) {
            response.success = false;
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void pause(Response response, PauseRequest.PauseArguments arguments) {
        try {
            if (arguments.threadId == Isolate.DEFAULT_ID) {
                if (swfSession.isSuspended()) {
                    response.success = false;
                    sendResponse(response);
                    return;
                }
                swfSession.suspend();
                stopWaitingForResume(Isolate.DEFAULT_ID);
            } else {
                // worker
                boolean foundWorker = false;
                for (IsolateWithState isolateWithState : isolates) {
                    Isolate isolate = isolateWithState.isolate;
                    if (arguments.threadId == isolate.getId()) {
                        foundWorker = true;
                        IsolateSession isolateSession = swfSession.getWorkerSession(isolate.getId());
                        if (isolateSession.isSuspended()) {
                            response.success = false;
                            sendResponse(response);
                            return;
                        }
                        isolateSession.suspend();
                        stopWaitingForResume(isolate.getId());
                    }
                }
                if (!foundWorker) {
                    response.success = false;
                }
            }
        } catch (NoResponseException e) {
            response.success = false;
            sendErrorOutputEvent(e.getMessage() + "\n");
        } catch (Exception e) {
            response.success = false;
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            sendErrorOutputEvent("Exception in debugger: " + writer.toString() + "\n");
        }
        sendResponse(response);
    }

    public void stackTrace(Response response, StackTraceRequest.StackTraceArguments arguments) {
        List<StackFrame> stackFrames = new ArrayList<>();
        int threadId = arguments.threadId;
        IsolateSession isolateSession = getIsolateSession(threadId);
        Frame[] swfFrames = null;
        try {
            if (isolateSession != null) {
                swfFrames = isolateSession.getFrames();
            } else {
                swfFrames = swfSession.getFrames();
            }
        } catch (NotConnectedException e) {
        }
        if (swfFrames == null) {
            response.success = false;
            sendResponse(response);
            return;
        }
        for (int i = 0, count = swfFrames.length; i < count; i++) {
            Frame swfFrame = swfFrames[i];
            Location location = swfFrame.getLocation();
            SourceFile file = location.getFile();
            StackFrame stackFrame = new StackFrame();
            // include ids for both the isolate and the frame
            stackFrame.id = new IsolateAndFrame(threadId, i).toCombinedID();
            stackFrame.name = swfFrame.getCallSignature();
            if (file != null) {
                Source source = sourceFileToSource(file);
                stackFrame.source = source;
                stackFrame.line = location.getLine();
                // location doesn't include column
                // use 1 as the default since that's required to show
                // exception info (0 won't work)
                stackFrame.column = 1;
            }
            stackFrames.add(stackFrame);
        }
        sendResponse(response, new StackTraceResponseBody(stackFrames));
    }

    public void scopes(Response response, ScopesRequest.ScopesArguments arguments) {
        List<Scope> scopes = new ArrayList<>();
        IsolateAndFrame isolateAndFrame = new IsolateAndFrame(arguments.frameId);
        Frame[] swfFrames = null;
        try {
            swfFrames = getFramesForIsolate(isolateAndFrame.isolateId);
        } catch (NotConnectedException e) {
        }
        if (swfFrames == null) {
            response.success = false;
            sendResponse(response);
            return;
        }
        if (isolateAndFrame.frameId >= 0 && isolateAndFrame.frameId < swfFrames.length) {
            FaultEvent faultEvent = getPreviousFaultEvent(isolateAndFrame.isolateId);
            if (faultEvent != null) {
                ExceptionFault fault = (ExceptionFault) faultEvent;
                Value thrownValue = fault.getThrownValue();
                IsolateAndFrameOrValue faultIsolateAndFrameOrValue = new IsolateAndFrameOrValue(Isolate.DEFAULT_ID, 0,
                        thrownValue.getId());
                Scope exceptionScope = new Scope();
                exceptionScope.name = "Exception";
                exceptionScope.variablesReference = faultIsolateAndFrameOrValue.toVariablesReference();
                scopes.add(exceptionScope);
            }

            Scope localScope = new Scope();
            localScope.name = "Locals";
            IsolateAndFrameOrValue isolateAndFrameOrValue = new IsolateAndFrameOrValue(isolateAndFrame.isolateId,
                    isolateAndFrame.frameId, LOCALS_VALUE_ID);
            localScope.variablesReference = isolateAndFrameOrValue.toVariablesReference();
            scopes.add(localScope);
        }

        sendResponse(response, new ScopesResponseBody(scopes));
    }

    private IsolateSession getIsolateSession(int isolateId) {
        if (isolateId == Isolate.DEFAULT_ID) {
            return null;
        }
        for (IsolateWithState isolateWithState : isolates) {
            Isolate isolate = isolateWithState.isolate;
            if (isolateId == isolate.getId()) {
                return swfSession.getWorkerSession(isolate.getId());
            }
        }
        return null;
    }

    private Frame[] getFramesForIsolate(int isolateID) throws NotConnectedException {
        if (isolateID == Isolate.DEFAULT_ID) {
            return swfSession.getFrames();
        }
        IsolateSession isolateSession = swfSession.getWorkerSession(isolateID);
        return isolateSession.getFrames();
    }

    public void variables(Response response, VariablesRequest.VariablesArguments arguments) {
        List<Variable> variables = new ArrayList<>();
        try {
            IsolateAndFrameOrValue isolateAndFrameOrValue = new IsolateAndFrameOrValue(arguments.variablesReference);
            flash.tools.debugger.Variable[] members = getVariables(isolateAndFrameOrValue);
            mapMembersToVariables(isolateAndFrameOrValue, members, arguments.filter, arguments.start, arguments.count,
                    variables);
        } catch (PlayerDebugException e) {
            // ignore
        }
        sendResponse(response, new VariablesResponseBody(variables));
    }

    private void mapMembersToVariables(IsolateAndFrameOrValue isolateAndFrameOrValue,
            flash.tools.debugger.Variable[] members, String filter, Integer start, Integer count, List<Variable> result)
            throws PlayerDebugException {
        boolean isThis = false;
        IsolateSession isolateSession = getIsolateSession(isolateAndFrameOrValue.isolateId);
        if (isolateSession != null) {
            Value swfThisValue = isolateSession.getValue(Value.THIS_ID);
            if (swfThisValue != null) {
                isThis = swfThisValue.getId() == isolateAndFrameOrValue.valueId;
            }
        } else if (swfSession.isSuspended()) {
            Value swfThisValue = swfSession.getValue(Value.THIS_ID);
            if (swfThisValue != null) {
                isThis = swfThisValue.getId() == isolateAndFrameOrValue.valueId;
            }
        }
        List<Integer> indexes = null;
        boolean requireIndexed = VariablesRequest.FILTER_INDEXED.equals(filter);
        if (requireIndexed) {
            indexes = new ArrayList<>();
        }
        for (flash.tools.debugger.Variable member : members) {
            if (member.isAttributeSet(VariableAttribute.IS_STATIC)) {
                // we're showing non-static members only
                continue;
            }
            int index = -1;
            String memberName = member.getName();
            if (filter != null) {
                try {
                    index = Integer.parseInt(memberName);
                    if (!requireIndexed) {
                        continue;
                    }
                    int startIndex = start != null ? start : 0;
                    if (index < startIndex) {
                        continue;
                    }
                    if (count != null && count != 0 && index >= (startIndex + count)) {
                        continue;
                    }
                } catch (NumberFormatException e) {
                    if (requireIndexed) {
                        continue;
                    }
                }
            }
            Value memberValue = member.getValue();
            Variable variable = new Variable();
            variable.name = memberName;
            variable.type = memberValue.getTypeName();
            if (isolateAndFrameOrValue.valueId == LOCALS_VALUE_ID) {
                variable.evaluateName = memberName;
            } else if (isThis) {
                if (index != -1) {
                    variable.evaluateName = "this[" + memberName + "]";
                } else {
                    variable.evaluateName = "this." + memberName;
                }
            }
            long id = memberValue.getId();
            if (id != Value.UNKNOWN_ID) {
                IsolateAndFrameOrValue memberIsolateAndFrameOrValue = new IsolateAndFrameOrValue(
                        isolateAndFrameOrValue.isolateId, isolateAndFrameOrValue.frameId, id);
                variable.value = memberValue.getTypeName();
                variable.variablesReference = memberIsolateAndFrameOrValue.toVariablesReference();
                flash.tools.debugger.Variable[] subMembers = memberValue.getMembers(swfSession);
                int namedVariables = 0;
                int indexedVariables = 0;
                for (flash.tools.debugger.Variable subMember : subMembers) {
                    try {
                        Integer.parseInt(subMember.getName());
                        indexedVariables++;
                    } catch (NumberFormatException e) {
                        namedVariables++;
                    }
                }
                variable.indexedVariables = indexedVariables;
                variable.namedVariables = namedVariables;
            } else {
                if (memberValue.getType() == VariableType.STRING) {
                    variable.value = "\"" + memberValue.getValueAsString() + "\"";
                } else {
                    variable.value = memberValue.getValueAsString();
                }
            }
            VariablePresentationHint presentationHint = new VariablePresentationHint();
            if (member.getScope() == VariableAttribute.PUBLIC_SCOPE) {
                presentationHint.visibility = VariablePresentationHint.VISIBILITY_PUBLIC;
            } else if (member.getScope() == VariableAttribute.PRIVATE_SCOPE) {
                presentationHint.visibility = VariablePresentationHint.VISIBILITY_PRIVATE;
            } else if (member.getScope() == VariableAttribute.PROTECTED_SCOPE) {
                presentationHint.visibility = VariablePresentationHint.VISIBILITY_PROTECTED;
            } else if (member.getScope() == VariableAttribute.INTERNAL_SCOPE) {
                presentationHint.visibility = VariablePresentationHint.VISIBILITY_INTERNAL;
            }
            List<String> attributes = new ArrayList<>();
            if (member.isAttributeSet(VariableAttribute.IS_STATIC)) {
                attributes.add(VariablePresentationHint.ATTRIBUTES_STATIC);
            }
            if (member.isAttributeSet(VariableAttribute.IS_CONST)) {
                attributes.add(VariablePresentationHint.ATTRIBUTES_CONSTANT);
            }
            if (member.isAttributeSet(VariableAttribute.READ_ONLY)) {
                attributes.add(VariablePresentationHint.ATTRIBUTES_READ_ONLY);
            }
            if (attributes.size() > 0) {
                presentationHint.attributes = attributes.toArray(new String[attributes.size()]);
            }
            variable.presentationHint = presentationHint;
            if (requireIndexed) {
                boolean inserted = false;
                for (int i = 0; i < indexes.size(); i++) {
                    int otherIndex = indexes.get(i);
                    if (index < otherIndex) {
                        indexes.add(i, index);
                        result.add(i, variable);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    indexes.add(index);
                    result.add(variable);
                }
            } else {
                result.add(variable);
            }
        }
    }

    /**
     * Frames don't have a global id, so we need to combine a value with the
     * isolate/worker id to be able to figure out which one to reference.
     */
    private class IsolateAndFrame {
        private static final int FRAME_MULTIPLIER = 100;
        private static final int ISOLATE_MULTIPLIER = 1;

        public IsolateAndFrame(int isolateAndFrameId) {
            frameId = (int) (isolateAndFrameId / FRAME_MULTIPLIER);
            isolateAndFrameId -= (frameId * FRAME_MULTIPLIER);
            isolateId = (int) (isolateAndFrameId / ISOLATE_MULTIPLIER);
        }

        public IsolateAndFrame(int isolateId, int frameId) {
            this.isolateId = isolateId;
            this.frameId = frameId;
        }

        public int frameId;
        public int isolateId;

        public int toCombinedID() {
            return (frameId * FRAME_MULTIPLIER) + (isolateId * ISOLATE_MULTIPLIER);
        }
    }

    /**
     * Values from isolates cannot be accessed from the main session, so we need to
     * encode the frame and isolate/worker id with the value id.
     */
    private class IsolateAndFrameOrValue {
        private static final long VALUE_MULTIPLIER = 10000L;
        private static final long FRAME_MULTIPLIER = 100L;
        private static final long ISOLATE_MULTIPLIER = 1L;

        public IsolateAndFrameOrValue(long variablesReference) {
            valueId = variablesReference / VALUE_MULTIPLIER;
            variablesReference -= valueId * VALUE_MULTIPLIER;
            frameId = (int) (variablesReference / FRAME_MULTIPLIER);
            variablesReference -= (frameId * FRAME_MULTIPLIER);
            isolateId = (int) (variablesReference / ISOLATE_MULTIPLIER);
        }

        public IsolateAndFrameOrValue(int isolateId, int frameId, long valueId) {
            this.isolateId = isolateId;
            this.frameId = frameId;
            this.valueId = valueId;
        }

        public int frameId;
        public int isolateId;
        public long valueId;

        public long toVariablesReference() {
            return (valueId * VALUE_MULTIPLIER) + (frameId * FRAME_MULTIPLIER) + (isolateId * ISOLATE_MULTIPLIER);
        }
    }

    private flash.tools.debugger.Variable[] getVariablesForFrame(Frame swfFrame) {
        try {
            flash.tools.debugger.Variable[] args = swfFrame.getArguments(swfSession);
            flash.tools.debugger.Variable[] locals = swfFrame.getLocals(swfSession);
            flash.tools.debugger.Variable swfThis = swfFrame.getThis(swfSession);
            int memberCount = locals.length + args.length;
            int offset = 0;
            if (swfThis != null) {
                offset++;
            }
            flash.tools.debugger.Variable[] members = new flash.tools.debugger.Variable[memberCount + offset];
            if (swfThis != null) {
                members[0] = swfThis;
            }
            System.arraycopy(args, 0, members, offset, args.length);
            System.arraycopy(locals, 0, members, args.length + offset, locals.length);
            return members;
        } catch (PlayerDebugException e) {
            return new flash.tools.debugger.Variable[0];
        }
    }

    private flash.tools.debugger.Variable[] getVariables(IsolateAndFrameOrValue isolateAndFrameOrValue) {
        try {
            if (isolateAndFrameOrValue.valueId == LOCALS_VALUE_ID) {
                Frame[] frames = getFramesForIsolate(isolateAndFrameOrValue.isolateId);
                Frame frameWithLocals = frames[isolateAndFrameOrValue.frameId];
                return getVariablesForFrame(frameWithLocals);
            }
            Value swfValue = null;
            IsolateSession isolateSession = getIsolateSession(isolateAndFrameOrValue.isolateId);
            if (isolateSession != null) {
                swfValue = isolateSession.getValue(isolateAndFrameOrValue.valueId);
            } else if (swfSession.isSuspended()) {
                swfValue = swfSession.getValue(isolateAndFrameOrValue.valueId);
            }
            if (swfValue != null) {
                return swfValue.getMembers(swfSession);
            }
        } catch (PlayerDebugException e) {
            return new flash.tools.debugger.Variable[0];
        }
        return new flash.tools.debugger.Variable[0];
    }

    private flash.tools.debugger.Variable getMemberByName(IsolateAndFrameOrValue variablesReference, String name)
            throws PlayerDebugException {
        flash.tools.debugger.Variable[] members = getVariables(variablesReference);
        if (members != null) {
            for (flash.tools.debugger.Variable member : members) {
                if (member.getName().equals(name)) {
                    return member;
                }
            }
        }
        return null;
    }

    public void setVariable(Response response, SetVariableRequest.SetVariableArguments arguments) {
        IsolateAndFrameOrValue isolateAndFrameOrValue = new IsolateAndFrameOrValue(arguments.variablesReference);

        try {
            flash.tools.debugger.Variable member = getMemberByName(isolateAndFrameOrValue, arguments.name);
            if (member != null) {
                Value memberValue = member.getValue();
                FaultEvent faultEvent = null;
                boolean setValue = true;
                String rawValue = arguments.value;
                if (Boolean.TRUE.toString().equals(rawValue) || Boolean.FALSE.toString().equals(rawValue)) {
                    faultEvent = member.setValue(swfSession, VariableType.BOOLEAN, rawValue);
                } else if (Pattern.compile("\\-?[0-9]*(\\.[0-9]+)?").matcher(rawValue).matches()) {
                    faultEvent = member.setValue(swfSession, VariableType.NUMBER, rawValue);
                } else if (Pattern.compile("\\-?0x[0-9A-Fa-f]+").matcher(rawValue).matches()) {
                    boolean negative = false;
                    int offset = 2;
                    if (rawValue.startsWith("-")) {
                        negative = true;
                        offset++;
                    }
                    rawValue = rawValue.substring(offset).toUpperCase();
                    if (negative) {
                        rawValue = "-" + rawValue;
                    }
                    rawValue = Long.parseLong(rawValue, 16) + "";
                    faultEvent = member.setValue(swfSession, VariableType.NUMBER, rawValue);
                } else if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
                    rawValue = rawValue.substring(1, rawValue.length() - 1);
                    faultEvent = member.setValue(swfSession, VariableType.STRING, rawValue);
                } else {
                    setValue = false;
                }
                setValue = setValue && faultEvent == null;

                if (setValue) {
                    // need to get it again to access the new value
                    member = getMemberByName(isolateAndFrameOrValue, arguments.name);
                    memberValue = member.getValue();
                    SetVariableResponseBody body = new SetVariableResponseBody();
                    body.type = memberValue.getTypeName();
                    long id = memberValue.getId();
                    if (id != Value.UNKNOWN_ID) {
                        IsolateAndFrameOrValue memberIsolateAndFrameOrValue = new IsolateAndFrameOrValue(
                                isolateAndFrameOrValue.isolateId, isolateAndFrameOrValue.frameId, memberValue.getId());
                        body.value = memberValue.getTypeName();
                        body.variablesReference = memberIsolateAndFrameOrValue.toVariablesReference();
                    } else {
                        if (memberValue.getType() == VariableType.STRING) {
                            body.value = "\"" + memberValue.getValueAsString() + "\"";
                        } else {
                            body.value = memberValue.getValueAsString();
                        }
                    }
                    sendResponse(response, body);
                    return;
                } else {
                    if (faultEvent != null && faultEvent.information != null) {
                        response.message = faultEvent.information;
                    } else {
                        response.message = "Failed to set value of '" + arguments.name + "'";
                    }
                }
            }
        } catch (PlayerDebugException e) {

        }
        response.success = false;
        sendResponse(response);

    }

    public void threads(Response response, Request.RequestArguments arguments) {
        List<Thread> threads = new ArrayList<>();
        threads.add(new Thread(Isolate.DEFAULT_ID, "Main SWF"));
        for (IsolateWithState isolateWithState : isolates) {
            Isolate isolate = isolateWithState.isolate;
            threads.add(new Thread(isolate.getId(), "Worker " + isolate.getId()));
        }
        sendResponse(response, new ThreadsResponseBody(threads));
    }

    public void evaluate(Response response, EvaluateRequest.EvaluateArguments arguments) {
        boolean suspended = false;
        try {
            suspended = swfSession.isSuspended();
        } catch (PlayerDebugException e) {
        }
        if (!suspended) {
            response.success = false;
            response.message = "Must be paused to evaluate expressions";
            sendResponse(response);
            return;
        }
        IsolateAndFrameOrValue isolateAndFrameOrValue = new IsolateAndFrameOrValue(arguments.frameId);
        EvaluateResponseBody body = new EvaluateResponseBody();
        Object evaluateResult = null;
        try {
            Integer frameId = isolateAndFrameOrValue.frameId;
            if (frameId != null) {
                Frame[] swfFrames = swfSession.getFrames();
                if (frameId >= 0 && frameId < swfFrames.length) {
                    Frame swfFrame = swfFrames[frameId];

                    ASTBuilder builder = new ASTBuilder(false);
                    ValueExp result = builder.parse(new StringReader(arguments.expression));
                    evaluateResult = result
                            .evaluate(new SWFExpressionContext(swfSession, Isolate.DEFAULT_ID, swfFrame));
                }
            }
        } catch (PlayerFaultException e) {
        } catch (NoSuchVariableException e) {
        } catch (IOException e) {
        } catch (ParseException e) {
        } catch (PlayerDebugException e) {
        }

        Value value = null;
        if (evaluateResult == null) {
            try {
                value = swfSession.getGlobal(arguments.expression);
            } catch (PlayerDebugException e) {
            }
        } else if (evaluateResult instanceof flash.tools.debugger.Variable) {
            flash.tools.debugger.Variable variable = (flash.tools.debugger.Variable) evaluateResult;
            value = variable.getValue();
        } else if (evaluateResult instanceof Value) {
            value = (Value) evaluateResult;
        }

        if (value != null) {
            long id = value.getId();
            if (id != Value.UNKNOWN_ID) {
                IsolateAndFrameOrValue evaluatedIsolateAndFrameOrValue = new IsolateAndFrameOrValue(
                        isolateAndFrameOrValue.isolateId, isolateAndFrameOrValue.frameId, value.getId());
                body.result = value.getTypeName();
                body.variablesReference = evaluatedIsolateAndFrameOrValue.toVariablesReference();
                body.type = value.getTypeName();
            } else {
                if (value.getType() == VariableType.STRING) {
                    body.result = "\"" + value.getValueAsString() + "\"";
                } else {
                    body.result = value.getValueAsString();
                }
                body.type = value.getTypeName();
            }
        } else if (evaluateResult != null) {
            if (evaluateResult instanceof String) {
                body.result = "\"" + evaluateResult + "\"";
            } else {
                body.result = evaluateResult.toString();
            }
        }

        if (body.result == null) {
            body.result = "undefined";
        }
        sendResponse(response, body);
    }

    public FaultEvent getPreviousFaultEvent(int isolateId) {
        if (isolateId == Isolate.DEFAULT_ID) {
            return previousFaultEvent;
        }
        for (IsolateWithState isolateWithState : isolates) {
            if (isolateId == isolateWithState.isolate.getId()) {
                return isolateWithState.previousFaultEvent;
            }
        }
        return null;
    }

    public void exceptionInfo(Response response, ExceptionInfoRequest.ExceptionInfoArguments arguments) {
        int isolateId = arguments.threadId;
        FaultEvent faultEvent = getPreviousFaultEvent(isolateId);
        if (faultEvent == null) {
            sendResponse(response);
            return;
        }

        String typeName = null;
        if (faultEvent instanceof ExceptionFault) {
            ExceptionFault exceptionFault = (ExceptionFault) faultEvent;
            Value thrownValue = exceptionFault.getThrownValue();
            typeName = thrownValue.getTypeName();
        }

        ExceptionDetails details = new ExceptionDetails();
        details.message = faultEvent.information;
        details.stackTrace = faultEvent.stackTrace();
        details.typeName = typeName;
        sendResponse(response, new ExceptionInfoResponseBody(null,
                ExceptionInfoResponseBody.EXCEPTION_BREAK_MODE_ALWAYS, faultEvent.information, details));
    }

    private void stopWaitingForResume(int isolateId) {
        if (isolateId == Isolate.DEFAULT_ID) {
            waitingForResume = false;
            // previousFaultEvent = null;
        } else {
            for (IsolateWithState isolateWithState : isolates) {
                if (isolateWithState.isolate.getId() == isolateId) {
                    isolateWithState.waitingForResume = false;
                    // isolateWithState.previousFaultEvent = null;
                    break;
                }
            }
        }
    }

    protected String transformPath(String sourceFilePath) {
        int index = sourceFilePath.indexOf(SDK_PATH_SIGNATURE_UNIX);
        boolean sourcePathIsUnix = true;
        if (index == -1) {
            index = sourceFilePath.indexOf(SDK_PATH_SIGNATURE_WINDOWS);
            if (index == -1) {
                return sourceFilePath;
            }
            sourcePathIsUnix = false;
        }
        if (flexHome == null) {
            return sourceFilePath;
        }
        String relativePath = sourceFilePath.substring(index + 1);
        boolean osIsWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (sourcePathIsUnix && osIsWindows) {
            relativePath = relativePath.replace('/', '\\');
        } else if (!sourcePathIsUnix && !osIsWindows) {
            relativePath = relativePath.replace('\\', '/');
        }
        Path transformedPath = flexHome.resolve(relativePath);
        if (Files.exists(transformedPath)) {
            // only transform the path if the transformed file exists
            // if it doesn't exist, the original path may be valid
            return transformedPath.toAbsolutePath().toString();
        }
        return sourceFilePath;
    }

    private Source sourceFileToSource(SourceFile sourceFile) {
        Source source = new Source();
        source.name = sourceFile.getName();
        source.path = transformPath(sourceFile.getFullPath());
        return source;
    }

    protected Gson createGson() {
        GsonBuilder builder = new GsonBuilder();
        return builder.registerTypeAdapter(Request.class, new DebugSession.RequestDeserializer())
                .registerTypeAdapter(LaunchRequest.LaunchRequestArguments.class, new SWFLaunchRequestDeserializer())
                .registerTypeAdapter(AttachRequest.AttachRequestArguments.class, new SWFAttachRequestDeserializer())
                .create();
    }

    public static class SWFLaunchRequestDeserializer implements JsonDeserializer<LaunchRequest.LaunchRequestArguments> {
        public LaunchRequest.LaunchRequestArguments deserialize(JsonElement je, Type type,
                JsonDeserializationContext jdc) throws JsonParseException {
            return gson.fromJson(je, SWFLaunchRequestArguments.class);
        }
    }

    public static class SWFAttachRequestDeserializer implements JsonDeserializer<AttachRequest.AttachRequestArguments> {
        public AttachRequest.AttachRequestArguments deserialize(JsonElement je, Type type,
                JsonDeserializationContext jdc) throws JsonParseException {
            return gson.fromJson(je, SWFAttachRequestArguments.class);
        }
    }
}
