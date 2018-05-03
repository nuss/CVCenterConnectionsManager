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
			Error("There is already a CVCenterConnectionsManager stored under the name '%'. Please choose a different name.").throw;
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
			});
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
			widgetsToBeConnected = includeWidgets;
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

	connectWidgets {
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
						wdgt.msSize.do({ |i|
							[key, count, i].postln;
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

		this.prMakeSlider;
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
			"cmd: %\n".postf(cmd);
			// the number of values sent within a command
			// should always be cmd[2]-1 - first slot is the command name
			// cmd[2]-1 must be retrieved from incomingCmds
			// since we don't get it from CVCenter'
			tmpSize = incomingCmds.detect({ |n| n[1] === cmd[1] })[2];
			"tmpSize: %\n".postf(tmpSize);
			sliderSize = sliderSize + tmpSize-1;
		});

		widgetsToBeConnected.do({ |key, i|
			switch (CVCenter.cvWidgets[key].class,
				CVWidgetKnob, {
					numSliders = numSliders + 1;
					// value = CVCenter.at(key).value;
					"knob '%': %\n".postf(key, CVCenter.cvWidgets[key].midiOscEnv[i]);
					CVCenter.cvWidgets[key].midiOscEnv.oscResponder !? {
						cmdName = CVCenter.cvWidgets[key].midiOscEnv.oscResponder.cmdName;
						mixerIndices = mixerIndices.add([cmdName, index]);
						index = index + 1;
					}
					// "value: %\n".postf(value);
					// "knob: %\n".postf([key, i]);
				},
				CVWidget2D, {
					numSliders = numSliders + 2;
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
					numSliders = numSliders + CVCenter.cvWidgets[key].msSize;
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
						CVCenter.cvWidgets[key].msSize.do({ |i|
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

	filterVideOSC { |wdgts|
		// TODO: how pass widget names in
		var name, valName, mixName;
		var tab, valTab, mixTab;
		wdgts.do({ |w|
			name = CVCenter.cvWidgets[w].name;
			valName = ("val" ++ name[0].toUpper ++ name[1..]);
			mixName = ("mix" ++ name[0].toUpper ++ name[1..]);
			tab = CVCenter.getTab(w);
			valTab = "val" ++ tab.asString[0].toUpper ++ tab.asString[1..];
			mixTab = "mix" ++ tab.asString[0].toUpper ++ tab.asString[1..];

			switch (CVCenter.cvWidgets[w].class,
				CVWidget2D, {
					#[lo, hi].do({ |slot|
						CVCenter.use(
							valName,
							CVCenter.cvWidgets[w].getSpec(slot),
							CVCenter.at(w).value,
							valTab,
							slot
						);
						CVCenter.use(
							mixName,
							nil,
							0,
							mixTab,
							slot
						)
					})
				},
				{
					CVCenter.use(
						valName,
						CVCenter.cvWidgets[w].getSpec;
						CVCenter.at(w).value,
						valTab
					)
				}
			)
		})

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
