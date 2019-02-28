CVCenterConnectionsManager {
	classvar <all;
	var <name;
	var <incomingCmds, receiveFunc;
	var <widgetsToBeConnected;

	// TODO: netAddress should be passed in as second arg
	*new { |name, includeWidgets, excludeWidgets|
		^super.newCopyArgs(name).init(includeWidgets, excludeWidgets)
	}

	init { |includeWidgets, excludeWidgets|
		name ?? {
			Error("Please provide a name for the CVCenterConnectionsManager.").throw;
		};

		all ?? {
			all = ();
		};

		if (all.keys.includes(name.asSymbol)) {
			Error("There is already a CVCenterConnectionsManager stored under the name '%'. Please choose a different name.".format(name)).throw;
		} { all.put(name.asSymbol, this) };

		excludeWidgets !? {
			includeWidgets = nil;
			if (excludeWidgets.isArray.not) {
				Error("arg 'excludeWidgets' is expected to be an array of Symbols or Strings, denoting the widgets that shall not be managed by the mixer!").throw;
			} {
				if (excludeWidgets.select { |name| name.isString or: { name.class == Symbol }}.size != excludeWidgets.size) {
					Error("arg 'excludeWidgets' includes values that are neither Strings nor Symbols!").throw;
				}
			};
			widgetsToBeConnected = CVCenter.all.keys.copy.asArray.takeThese({ |item|
				excludeWidgets.includes(item)
			}).sort;
			"excludeWidgets: widgetsToBeConnected: %\n".postf(widgetsToBeConnected);
		};

		includeWidgets !? {
			if (includeWidgets.isArray.not) {
				Error("arg 'includeWidgets' is expected to be an array of Symbols or Strings, denoting the widgets that shall be managed by the mixer!").throw;
			} {
				if (includeWidgets.select { |name| name.isString or: { name.class == Symbol }}.size != includeWidgets.size) {
					Error("arg 'includeWigets' includes values that are neither Strings nor Symbols!").throw;
				}
			};
			widgetsToBeConnected = includeWidgets.sort;
			"includeWidgets: widgetsToBeConnected: %\n".postf(widgetsToBeConnected);
		};

		incomingCmds = [];
	}

	// cmdPatterns can be command names like "/my/cmd1", "/my/cmd2" etc.
	// or a regular expression like "/[a-z1-4]+/(fader|slider)[1-9]+"
	// TODO: exclude status commands like "/ping" or similar
	// that some controllers may emmit at irregular intervals
	collectAddresses { |netAddress ...cmdPatterns|
		var addrCmd, cmdsInUse = CVCenter.getCmdNamesAndAddressesInUse;
		var cmds = cmdsInUse.collect { |it| it[1] };
		var matchFunc = { |addrCmd|
			case
			{ netAddress.isNil } {
				if (cmds.indexOfEqual(addrCmd[1]).isNil) {
					incomingCmds = incomingCmds.add(addrCmd);
				}
			}
			{ netAddress.notNil and:{ netAddress.port.isNil }} {
				if (addrCmd[0].ip == netAddress.ip and: {
					cmdsInUse.detect { |it| it[0].ip == addrCmd[0].ip and: { it[1] == addrCmd[1] }}.isNil
				}) {
					incomingCmds = incomingCmds.add(addrCmd);
				}
			}
			{ netAddress.notNil and: { netAddress.port.notNil }} {
				if (addrCmd[0] == netAddress and: {
					cmdsInUse.detect { |it| it[0] == addrCmd[0] and: { it[1] == addrCmd[1] }}.isNil
				}) {
					incomingCmds = incomingCmds.add(addrCmd);
				}
			}

		};

		receiveFunc = { |msg, time, replyAddr, recvPort|
			if (msg[0] != '/status.reply') {
				addrCmd = [replyAddr, msg[0], msg.size];
				if (cmdPatterns.notEmpty) {
					cmdPatterns.do({ |pattern|
						if (pattern.matchRegexp(msg[0].asString) and:{
							incomingCmds.includesEqual(addrCmd).not
						}) {
							matchFunc.value(addrCmd);
						}
					})
				} {
					matchFunc.value(addrCmd);
				}
			}
		};

		thisProcess.addOSCRecvFunc(receiveFunc);

		CmdPeriod.add({
			thisProcess.removeOSCRecvFunc(receiveFunc);
		});
	}

	stopCollectingAddresses {
		thisProcess.removeOSCRecvFunc(receiveFunc);
		CmdPeriod.remove({
			thisProcess.removeOSCRecvFunc(receiveFunc);
		});
	}

	orderVideOSCCmds {
		var red, green, blue;

		red = incomingCmds.select { |cmdData| "/.+/red[0-9]+".matchRegexp(cmdData[1].asString) };
		green = incomingCmds.select { |cmdData| "/.+/green[0-9]+".matchRegexp(cmdData[1].asString) };
		blue = incomingCmds.select { |cmdData| "/.+/blue[0-9]+".matchRegexp(cmdData[1].asString) };

		red = red.sort({ |a, b|
			a = a[1].asString.split($/).last;
			b = b[1].asString.split($/).last;
			a.select { |s| s.isDecDigit }.asInteger < b.select { |s| s.isDecDigit }.asInteger;
		});
		green = green.sort({ |a, b|
			a = a[1].asString.split($/).last;
			b = b[1].asString.split($/).last;
			a.select { |s| s.isDecDigit }.asInteger < b.select { |s| s.isDecDigit }.asInteger;
		});
		blue = blue.sort({ |a, b|
			a = a[1].asString.split($/).last;
			b = b[1].asString.split($/).last;
			a.select { |s| s.isDecDigit }.asInteger < b.select { |s| s.isDecDigit }.asInteger;
		});

		incomingCmds = red ++ green ++ blue;
	}

	clearCmds {
		incomingCmds = [];
	}

	connectWidgets { |...widgets|
		var count = 0; // iteration over incomingCmds
		var msgIndex = 1; // a cmd may have more than 1 value
		var isMultiSlotCmd = false; // should be set to true if cmd has more than one value
		var thisWidgetsToBeConnected;

		if (widgets.isEmpty) {
			thisWidgetsToBeConnected = widgetsToBeConnected;
		} {
			thisWidgetsToBeConnected = widgets;
		};

		CVCenter.cvWidgets.pairsDo({ |key, wdgt|
			if (thisWidgetsToBeConnected.includes(key) and:{
				count < incomingCmds.size
			}) {
				// more than one value per cmd
				isMultiSlotCmd = incomingCmds[count][2] > 2;
				switch(wdgt.class,
					CVWidgetKnob, {
						// [key, count].postln;
						if (incomingCmds[count].notNil and:{
							wdgt.midiOscEnv.oscResponder.isNil
						}) {
							"CVWidgetKnob: '%', incomingCmds[%]: %, msgIndex: %, wdgt.class: %\n".postf(
								key, count, incomingCmds[count], msgIndex, wdgt.class
							);

							wdgt.oscConnect(
								incomingCmds[count][0].ip,
								incomingCmds[count][0].port,
								incomingCmds[count][1],
								msgIndex,
							);
							if (wdgt.getSpec.warp.class === ExponentialWarp) {
								wdgt.setOscMapping(\linexp);
							};
							// jump to the next value in cmd if cmd has more than one value
							// otherwise increment count and go to next incoming cmd
							if (isMultiSlotCmd and:{
								msgIndex < (incomingCmds[count][2]-1)
							}) { msgIndex = msgIndex + 1 } {
								count = count + 1;
								msgIndex = 1;
							}
						}
					},
					CVWidget2D, {
						#[lo, hi].do({ |slot|
							// [key, count, slot].postln;
							if (incomingCmds[count].notNil and:{
								wdgt.midiOscEnv[slot].oscResponder.isNil
							}) {
								"CVWidget2D: '%', incomingCmds[%]: %, msgIndex: %, wdgt.class: %\n".postf(
									key, count, incomingCmds[count], msgIndex, wdgt.class
								);

								wdgt.oscConnect(
									incomingCmds[count][0].ip,
									incomingCmds[count][0].port,
									incomingCmds[count][1],
									msgIndex,
									slot
								);
								if (wdgt.getSpec(slot).warp.class === ExponentialWarp) {
									wdgt.setOscMapping(\linexp, slot);
								};
								if (isMultiSlotCmd and:{
									msgIndex < (incomingCmds[count][2]-1)
								}) { msgIndex = msgIndex + 1 } {
									count = count + 1;
									msgIndex = 1;
								}
							}
						})
					},
					CVWidgetMS, {
						wdgt.size.do({ |i|
							// [key, count, i].postln;
							if (incomingCmds[count].notNil and:{
								wdgt.midiOscEnv[i].oscResponder.isNil
							}) {
								"CVWidgetMS: '%', incomingCmds[%]: %, msgIndex: %, wdgt.class: %\n".postf(
									key, count, incomingCmds[count], msgIndex, wdgt.class
								);

								wdgt.oscConnect(
									incomingCmds[count][0].ip,
									incomingCmds[count][0].port,
									incomingCmds[count][1],
									msgIndex,
									i
								);
								if (wdgt.getSpec.warp.class === ExponentialWarp) {
									wdgt.setOscMapping(\linexp, i)
								};
								if (isMultiSlotCmd and:{
									msgIndex < (incomingCmds[count][2]-1)
								}) { msgIndex = msgIndex + 1 } {
									count = count + 1;
									msgIndex = 1;
								}
							}
						})
					}
				)
			}
		});

		"connecting sliders done".postln;
	}

	*clear {
		all.pairsDo({ |k, v| v.clear });
	}

	clear {
		incomingCmds = [];
		widgetsToBeConnected = [];
		all.removeAt(name.asSymbol);
		name = nil;
	}
}
