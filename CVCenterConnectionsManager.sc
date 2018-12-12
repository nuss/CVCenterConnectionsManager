CVCenterConnectionsManager {
	classvar <all;
	var <name;
	var <incomingCmds, receiveFunc;
	var <widgetsToBeConnected;

	// TODO: netAddress should be passed in as second arg
	*new { |name, includeWidgets, excludeWidgets|
		^super.newCopyArgs(name).init(name, includeWidgets, excludeWidgets)
	}

	init { |name, includeWidgets, excludeWidgets|
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

		cmdPatterns ?? {
			Error("No command patterns given. Please provide at least one, e.g '/my/cmd'").throw;
		};

		receiveFunc = { |msg, time, replyAddr, recvPort|
			if (msg[0] != '/status.reply') {
				addrCmd = [replyAddr, msg[0], msg.size];
				cmdPatterns.do({ |pattern|
					if (pattern.matchRegexp(msg[0].asString) and:{
						incomingCmds.includesEqual(addrCmd).not
					}) {
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
					}
				})
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
		var thisWidgetsToBeConnected = widgets ?? {
			widgetsToBeConnected
		};

		CVCenter.cvWidgets.pairsDo({ |key, wdgt|
			if (thisWidgetsToBeConnected.includes(key) and:{
				count < incomingCmds.size
			}) {
				// more than one value per cmd
				isMultiSlotCmd = incomingCmds[count][2] > 2;
				switch(wdgt.class,
					CVWidgetKnob, {
						[key, count].postln;
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
							[key, count, slot].postln;
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
							[key, count, i].postln;
							if (incomingCmds[count].notNil and:{
								wdgt.midiOscEnv[i].oscResponder.isNil
							}) {
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

	filterVideOSC { |...wdgts|
		// TODO: how pass widget names in
		var name, valName, mixName;
		var tab, valTab, mixTab;
		var thisWdgts = [];
		var nilSpec;

		var action, newAction, match, matchedName;

		// allow regex as argument
		wdgts.do({ |wdgt|
			thisWdgts = thisWdgts.add(CVCenter.all.keys.selectAs({ |name|
				wdgt.asString.matchRegexp(name.asString);
			}, Array));
		});


		if (thisWdgts.flat.size > 0) {
			thisWdgts.flat.do({ |w|
				valName = ("val" ++ w.asString[0].toUpper ++ w.asString[1..]).asSymbol;
				mixName = ("mix" ++ w.asString[0].toUpper ++ w.asString[1..]).asSymbol;
				tab = CVCenter.getTab(w).asString;
				valTab = "val" ++ tab[0].toUpper ++ tab[1..].asSymbol;
				mixTab = "mix" ++ tab[0].toUpper ++ tab[1..].asSymbol;

				switch (CVCenter.cvWidgets[w].class,
					CVWidget2D, {
						#[lo, hi].do({ |slot|
							CVCenter.use(
								valName,
								CVCenter.at(w)[slot].spec,
								CVCenter.at(w).value[slot],
								valTab,
								slot
							);
							CVCenter.use(
								mixName,
								nil,
								0,
								mixTab,
								slot
							);

							CVCenter.cvWidgets[valName].setOscMapping(CVCenter.cvWidgets[w].getOscMapping(slot), slot);

							// get the action of the original widgt
							action = CVCenter.cvWidgets[w].wdgtActions[slot].default1.asArray[0][0].asCompileString;
							// what we later replace in the function string
							matchedName = action.findRegexp("\\[(.+)\\]");

							// must be a compile string...
							newAction =
							"{ |cv|
								var n = '" ++ w ++ "';
								var vn = '" ++ valName ++ "';
								var mn = '" ++ mixName ++ "';
								var mixed = ();
								var cubedLo1 = CVCenter.at(mn).lo.input.cubed;
								var cubedLo2 = (1 - CVCenter.at(mn).lo.input).cubed;
								var multLo = 1/(cubedLo1 + cubedLo2);
								var cubedHi1 = CVCenter.at(mn).hi.input.cubed;
								var cubedHi2 = (1 - CVCenter.at(mn).hi.input).cubed;
								var multHi = (cubedHi1 + cubedHi2).reciprocal;
								mixed.put('lo', CVCenter.at(vn).lo.value * cubedLo1 * multLo + (cv.value * cubedLo2 * multLo));
								mixed.put('hi', CVCenter.at(vn).hi.value * cubedHi1 * multHi + (cv.value * cubedHi2 * multHi));
							";

							// old action, replace values by mix of manually set values and values coming from videosc
							newAction = newAction ++ action[7..action.size-3].replace(matchedName[0][1], "[mixed.lo, mixed.hi]");
							newAction = newAction ++ "}";

							// disable old default action
							CVCenter.activateActionAt(w, \default1, false, slot);
							// add new action
							CVCenter.addActionAt(w, 'VideOSC mix', newAction, slot);
						});
					},
					{
						if (CVCenter.cvWidgets[w].class == CVWidgetMS) {
							nilSpec = nil ! CVCenter.cvWidgets[w].size;
							CVCenter.cvWidgets[w].size.do({ |i|
								CVCenter.cvWidgets[w].setOscMapping(CVCenter.cvWidgets[w].getOscMapping(i), i);
							})
						} {
							nilSpec = nil;
							CVCenter.cvWidgets[w].setOscMapping(CVCenter.cvWidgets[w].getOscMapping);
						};
						CVCenter.use(
							valName,
							CVCenter.at(w).spec,
							CVCenter.at(w).value,
							valTab
						);
						CVCenter.use(
							mixName,
							nilSpec,
							CVCenter.at(w).value,
							mixTab
						);

						action = CVCenter.cvWidgets[w].wdgtActions.default1.asArray[0][0];

						newAction =
						"{ |cv|
							var vn = '" ++ valName ++ "';
							var mn = '" ++ mixName ++ "';
							var cubed1 = CVCenter.at(mn).input.cubed;
							var cubed2 = (1 - CVCenter.at(mn).input).cubed;
							var mult = (cubed1 + cubed2).reciprocal;
							var mixed = CVCenter.at(vn).value * cubed1 * mult  + (cv.value * cubed2 * mult);
						";
						newAction = newAction ++ action[7..action.size-2].replace("cv.value", "mixed");
						newAction = newAction ++ "}";

						// disable old default action
						CVCenter.activateActionAt(w, \default1, false);
						// add new action
						CVCenter.addActionAt(w, 'VideOSC mix', newAction);
					}
				);


			})
		}
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
