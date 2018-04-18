CVCenterConnectionsMixer {
	var <incomingCmds, receiveFunc;
	var <widgetsToBeConnected;

	*new { |includeWidgets, excludeWidgets|
		^super.new.init(includeWidgets, excludeWidgets)
	}

	init { |includeWidgets, excludeWidgets|
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
		};

		incomingCmds = [];
	}

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
			if (widgetsToBeConnected.includes(key)) {
				// more than one value per cmd
				isMultiSlotCmd = incomingCmds[count][2] > 2;
				switch(wdgt.class,
					CVWidgetKnob, {
						incomingCmds[count] !? {
							wdgt.midiOscEnv.oscResponder ?? {
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
								// otherwise increment count and go to nect incoming cmd
								if (isMultiSlotCmd and:{
									msgIndex < (incomingCmds[count][2]-1)
								}) { msgIndex = msgIndex + 1 } {
									count = count + 1;
									msgIndex = 1;
								}
							}
						}
					},
					CVWidget2D, {
						#[lo, hi].do({ |slot|
							incomingCmds[count] !? {
								wdgt.midiOscEnv[slot].oscResponder ?? {
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
							}
						})
					},
					CVWidgetMS, {
						wdgt.msSize.do({ |i|
							incomingCmds[count] !? {
								wdgt.midiOscEnv[i].oscResponder ?? {
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
							}
						});
					}
				)
			}
		});
	}
}