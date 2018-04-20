CVCenterConnectionsManager {
	classvar <all;
	var name;
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

		incomingCmds.do({ |cmd|
			// the number of values sent within a command
			// should always be cmd[2]-1 - first slot is the command name
			sliderSize = sliderSize + cmd[2]-1;
		});

		widgetsToBeConnected.do({ |key|
			switch(CVCenter.cvWidgets[key].class,
				CVWidgetKnob, { numSliders = numSliders+1 },
				CVWidget2D, { numSliders = numSliders+2 },
				CVWidgetMS, {
					numSliders = numSliders + CVCenter.cvWidgets[key].msSize
				}
			)
		});

		CVCenter.use(
			name.asSymbol,
			[0, sliderSize-1, \lin, 1, 0]!numSliders,
			0, // TODO: value
			\default
		)
	}
}
