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

public class VariablePresentationHint {
	public static final String KIND_PROPERTY = "property";
	public static final String KIND_METHOD = "method";
	public static final String KIND_CLASS = "class";
	public static final String KIND_DATA = "data";
	public static final String KIND_EVENT = "event";
	public static final String KIND_BASE_CLASS = "baseClass";
	public static final String KIND_INNER_CLASS = "innerClass";
	public static final String KIND_INTERFACE = "interface";
	public static final String KIND_MOST_DERIVED_CLASS = "mostDerivedClass";
	public static final String KIND_VIRTUAL = "virtual";
	public static final String KIND_DATA_BREAKPOINT = "dataBreakpoint";

	public static final String ATTRIBUTES_STATIC = "static";
	public static final String ATTRIBUTES_CONSTANT = "constant";
	public static final String ATTRIBUTES_READ_ONLY = "readOnly";
	public static final String ATTRIBUTES_RAW_STRING = "rawString";
	public static final String ATTRIBUTES_HAS_OBJECT_ID = "hasObjectId";
	public static final String ATTRIBUTES_CAN_HAVE_OBJECT_ID = "canHaveObjectId";
	public static final String ATTRIBUTES_HAS_SIDE_EFFECTS = "hasSideEffects";

	public static final String VISIBILITY_PUBLIC = "public";
	public static final String VISIBILITY_PRIVATE = "private";
	public static final String VISIBILITY_PROTECTED = "protected";
	public static final String VISIBILITY_INTERNAL = "internal";
	public static final String VISIBILITY_FINAL = "final";

	/**
	 * The kind of variable.
	 */
	public String kind;

	/**
	 * Set of attributes represented as an array of strings.
	 */
	public String[] attributes;

	/**
	 * Visibility of variable.
	 */
	public String visibility;
}