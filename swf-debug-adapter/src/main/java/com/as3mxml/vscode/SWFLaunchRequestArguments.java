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

import java.util.Map;

import com.as3mxml.vscode.debug.requests.LaunchRequest;

public class SWFLaunchRequestArguments extends LaunchRequest.LaunchRequestArguments {
    public String program;
    public String request;
    public String profile;
    public String[] args;
    public String screensize;
    public int screenDPI;
    public String versionPlatform;
    public String runtimeExecutable;
    public String[] runtimeArgs;
    public String extdir;
    public String rootDirectory;
    public Map<String, String> env;
}
