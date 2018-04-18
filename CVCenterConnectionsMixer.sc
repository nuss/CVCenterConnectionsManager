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
		[includeWidgets, excludeWidgets].postln;
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
		var count = 0;

		CVCenter.cvWidgets.pairsDo({ |key, wdgt|
			if (widgetsToBeConnected.includes(key)) {
				switch(wdgt.class,
					CVWidgetKnob, {
						incomingCmds[count] !? {
							wdgt.midiOscEnv.oscResponder ?? {
								wdgt.oscConnect(
									incomingCmds[count][0].ip,
									incomingCmds[count][0].port,
									incomingCmds[count][1]
								);
								if (wdgt.getSpec.warp.class === ExponentialWarp) {
									wdgt.setOscMapping(\explin);
								};
								count = count + 1;
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
										slot: slot
									);
									if (wdgt.getSpec(slot).warp.class === ExponentialWarp) {
										wdgt.setOscMapping(\explin, slot);
									};
									count = count + 1;
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
										slot: i
									);
									if (wdgt.getSpec.warp.class === ExponentialWarp) {
										wdgt.setOscMapping(\explin, i)
									};
									count = count + 1;
								}
							}
						});
					}
				)
			}
		});
	}
}