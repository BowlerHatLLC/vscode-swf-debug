/*
Copyright 2016-2025 Bowler Hat LLC

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

import org.apache.royale.compiler.constants.IASKeywordConstants;

import flash.tools.debugger.Frame;
import flash.tools.debugger.NoResponseException;
import flash.tools.debugger.NotConnectedException;
import flash.tools.debugger.NotSuspendedException;
import flash.tools.debugger.Session;
import flash.tools.debugger.Value;
import flash.tools.debugger.Variable;
import flash.tools.debugger.concrete.DValue;
import flash.tools.debugger.expression.Context;
import flash.tools.debugger.expression.NoSuchVariableException;
import flash.tools.debugger.expression.PlayerFaultException;

public class SWFExpressionContext implements Context {
	public SWFExpressionContext(Session session, int isolateId, Object frameOrVariable) {
		this.swfSession = session;
		this.isolateId = isolateId;
		this.frameOrVariable = frameOrVariable;
	}

	private Session swfSession;
	private Object frameOrVariable;
	private int isolateId;

	public Object lookup(Object o) throws NoSuchVariableException {
		if (o instanceof Variable) {
			return o;
		}
		if (o instanceof Value) {
			return o;
		}
		if (!(o instanceof String)) {
			throw new NoSuchVariableException(o);
		}
		String memberName = (String) o;
		try {
			if (frameOrVariable instanceof Frame) {
				Frame contextFrame = (Frame) frameOrVariable;
				if (memberName.equals(IASKeywordConstants.THIS)) {
					return contextFrame.getThis(swfSession);
				}
				Variable[] args = contextFrame.getArguments(swfSession);
				for (Variable argVar : args) {
					if (argVar.getName().equals(memberName)) {
						return argVar;
					}
				}
				Variable[] locals = contextFrame.getLocals(swfSession);
				for (Variable localVar : locals) {
					if (localVar.getName().equals(memberName)) {
						return localVar;
					}
				}
				Variable thisVar = contextFrame.getThis(swfSession);
				if (thisVar != null) {
					Object member = lookupMember(thisVar, memberName);
					if (member != null) {
						return member;
					}
				}
				Variable[] scopes = contextFrame.getScopeChain(swfSession);
				for (Variable scope : scopes) {
					Value scopeValue = scope.getValue();
					Variable[] members = scopeValue.getMembers(swfSession);
					if (members != null) {
						for (Variable member : members) {
							if (member.getName().equals(memberName)) {
								return member;
							}
						}
					}
				}

			}
		} catch (NoResponseException e) {
			throw new NoSuchVariableException(o);
		} catch (NotSuspendedException e) {
			throw new NoSuchVariableException(o);
		} catch (NotConnectedException e) {
			throw new NoSuchVariableException(o);
		}
		if (frameOrVariable instanceof Variable) {
			Variable contextVar = (Variable) frameOrVariable;
			Object member = lookupMember(contextVar, memberName);
			if (member != null) {
				return member;
			}
		}
		throw new NoSuchVariableException(o);
	}

	private Object lookupMember(Variable context, String memberName) {
		if (context == null) {
			return null;
		}
		Variable[] members = null;
		try {
			members = context.getValue().getMembers(swfSession);
		} catch (Exception e) {
			return null;
		}
		if (members != null) {
			for (Variable member : members) {
				if (member.getName().equals(memberName)) {
					return member;
				}
			}
		}
		return null;
	}

	public Object lookupMembers(Object o) throws NoSuchVariableException {
		Object lookupResult = lookup(o);
		if (!(lookupResult instanceof Variable)) {
			throw new NoSuchVariableException(o);
		}
		Variable variable = (Variable) lookupResult;
		Variable[] members = null;
		try {
			members = variable.getValue().getMembers(swfSession);
		} catch (Exception e) {
			throw new NoSuchVariableException(o);
		}
		return members;
	}

	public Context createContext(Object o) {
		Object lookupResult = null;
		try {
			lookupResult = lookup(o);
		} catch (NoSuchVariableException e) {
			return null;
		}
		return new SWFExpressionContext(swfSession, isolateId, lookupResult);
	}

	public void assign(Object o, Value v) throws NoSuchVariableException, PlayerFaultException {
		Variable variable = null;
		if (o instanceof Variable) {
			variable = (Variable) o;
		} else {
			if (frameOrVariable != null) {
				try {
					variable = (Variable) lookup(o);
				} catch (NoSuchVariableException e) {
				}
			}
		}
		if (variable == null) {
			return;
		}
		try {
			variable.setValue(swfSession, v.getType(), v.getValueAsString());
		} catch (NotConnectedException e) {
			return;
		} catch (NoResponseException e) {
			return;
		} catch (NotSuspendedException e) {
			return;
		}
	}

	public void createPseudoVariables(boolean create) {
	}

	public Value toValue(Object o) {
		if (o instanceof Value) {
			return (Value) o;
		}
		if (o instanceof Variable) {
			Variable variable = (Variable) o;
			return variable.getValue();
		}
		if (o instanceof Frame) {
			Frame frame = (Frame) o;
			Value frameValue = null;
			try {
				frameValue = frame.getThis(swfSession).getValue();
			} catch (NotConnectedException e) {
				return null;
			} catch (NoResponseException e) {
				return null;
			} catch (NotSuspendedException e) {
				return null;
			}
			return frameValue;
		}
		return DValue.forPrimitive(o, isolateId);
	}

	public Value toValue() {
		return toValue(frameOrVariable);
	}

	public Session getSession() {
		return swfSession;
	}

	public int getIsolateId() {
		return isolateId;
	}
}