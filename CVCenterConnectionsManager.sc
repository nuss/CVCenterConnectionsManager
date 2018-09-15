CVCenterConnectionsManager {
	classvar <all;
	var <name;
	var <incomingCmds, receiveFunc;
	var <widgetsToBeConnected;

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
	collectAddresses { |...cmdPatterns|
		var addrCmd;

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
						incomingCmds = incomingCmds.add(addrCmd);
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

	connectWidgets { |addMixer = false|
		var count = 0; // iteration over incomingCmds
		var msgIndex = 1; // a cmd may have more than 1 value
		var isMultiSlotCmd = false; // should be set to true if cmd has more than one value

		CVCenter.cvWidgets.pairsDo({ |key, wdgt|
			if (widgetsToBeConnected.includes(key) and:{
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

		if (addMixer) {
			this.prMakeSlider;
		}
	}

	prMakeSlider {
		var sliderSize = 0;
		var numSliders = 0;
		var mixerIndices = [];
		var index = 0;
		var cmdName;
		var makeWidget;
		var tmpSize;

		CVCenter.getCmdNamesAndAddressesInUse.do({ |cmd|
			// the number of values sent within a command
			// should always be cmd[2]-1 - first slot is the command name
			// cmd[2]-1 must be retrieved from incomingCmds
			// since we don't get it from CVCenter'
			"cmd: %, incomingCmds: %\n".postf(cmd, incomingCmds);
			tmpSize = incomingCmds.detect({ |n| n[1] === cmd[1] })[2];
			"tmpSize: %\n".postf(tmpSize);
			sliderSize = sliderSize + tmpSize-1;
			"cmd: %, sliderSize: %\n".postf(cmd, sliderSize);
		});

		widgetsToBeConnected.do({ |key, i|
			switch (CVCenter.cvWidgets[key].class,
				CVWidgetKnob, {
					numSliders = numSliders + 1;
					"knob '%': %\n".postf(key, CVCenter.cvWidgets[key].midiOscEnv);
					CVCenter.cvWidgets[key].midiOscEnv.oscResponder !? {
						cmdName = CVCenter.cvWidgets[key].midiOscEnv.oscResponder.cmdName;
						mixerIndices = mixerIndices.add([cmdName, index]);
						index = index + 1;
					}
				},
				CVWidget2D, {
					numSliders = numSliders + 2;
					"2D '%': %\n".postf(key, CVCenter.cvWidgets[key].midiOscEnv);
					#[lo, hi].do({ |slot|
						CVCenter.cvWidgets[key].midiOscEnv[slot].oscResponder !? {
							cmdName = CVCenter.cvWidgets[key].midiOscEnv[slot].oscResponder.cmdName;
							mixerIndices = mixerIndices.add([cmdName, index]);
							index = index + 1;
						}
					})
					// "2D: %\n".postf([key, i]);
				},
				CVWidgetMS, {
					numSliders = numSliders + CVCenter.cvWidgets[key].size;
					"ms '%': %\n".postf(key, CVCenter.cvWidgets[key].midiOscEnv);
					numSliders.do({ |j|
						if (CVCenter.cvWidgets[key].midiOscEnv[j].notNil and: {
							CVCenter.cvWidgets[key].midiOscEnv[j].oscResponder.notNil
						}) {
							cmdName = CVCenter.cvWidgets[key].midiOscEnv[j].oscResponder.cmdName;
							mixerIndices = mixerIndices.add([cmdName, index]);
							index = index + 1
						}
					});
					// "ms: %\n".postf([key, i]);
				}
			)
		});

		"mixerIndices: %\n".postf(mixerIndices);

		mixerIndices = mixerIndices.flop.postln;

		CVCenter.scv.connectionsManager ?? {
			CVCenter.scv.put(\connectionsManager, ());
		};

		CVCenter.scv.connectionsManager.put(name.asSymbol, this);
		// "incomingCmds: %\n".postf(CVCenter.scv.connectionsManager[name.asSymbol].incomingCmds);

		CVCenter.use(
			name.asSymbol,
			[0, incomingCmds.size-1, \lin, 1, 0]!numSliders,
			mixerIndices[1],
			\default/*,
			svItems: mixerIndices[0]!numSliders*/
		);

		CVCenter.addActionAt(name.asSymbol, 'switch responder', { |cv|
			var valCount = 0;
			[cv.value.size, widgetsToBeConnected.size].postln;
			// var is2d = false;
			widgetsToBeConnected.do({ |key, i|
				switch(CVCenter.cvWidgets[key].class,
					CVWidget2D, {
						#[lo, hi].do({ |slot|
							if (valCount < cv.value.size) {
								CVCenter.cvWidgets[key].oscDisconnect(slot);
								CVCenter.cvWidgets[key].oscConnect(
									incomingCmds[cv.value[valCount]][0].ip,
									incomingCmds[cv.value[valCount]][0].port,
									incomingCmds[cv.value[valCount]][1],
									1,
									slot
								);
								valCount = valCount + 1;
							}
						});
					},
					CVWidgetMS, {
						CVCenter.cvWidgets[key].size.do({ |i|
							if (valCount < cv.value.size) {
								CVCenter.cvWidgets[key].oscDisconnect(i);
								CVCenter.cvWidgets[key].oscConnect(
									incomingCmds[cv.value[valCount]][0].ip,
									incomingCmds[cv.value[valCount]][0].port,
									incomingCmds[cv.value[valCount]][1],
									1,
									i
								);
								valCount = valCount + 1;
							}
						})
					},
					CVWidgetKnob, {
						if (valCount < cv.value.size) {
							CVCenter.cvWidgets[key].oscDisconnect;
							CVCenter.cvWidgets[key].oscConnect(
								incomingCmds[cv.value[valCount]][0].ip,
								incomingCmds[cv.value[valCount]][0].port,
								incomingCmds[cv.value[valCount]][1],
								1
							);
							valCount = valCount + 1;
						}
					}
				)
			})
		})
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
