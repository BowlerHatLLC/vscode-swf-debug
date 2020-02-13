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
package com.as3mxml.vscode.debug.responses;

import com.as3mxml.vscode.debug.protocol.Response;

public class SetVariableResponseBody extends Response.ResponseBody {
	public String value;
	public String type;
	public Long variablesReference = 0L;

	public SetVariableResponseBody() {
		super();
	}

	public SetVariableResponseBody(String value, String type, Long variablesReference) {
		this.value = value;
		this.type = type;
		this.variablesReference = variablesReference;
	}
}
