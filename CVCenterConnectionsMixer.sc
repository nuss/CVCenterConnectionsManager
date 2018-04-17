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

		incomingCmds = ();
		[includeWidgets, excludeWidgets].postln;
	}

	collectAddresses { |...cmdPatterns|
		var addrKey;

		cmdPatterns ?? {
			Error("No command patterns given. Please provide at least one, e.g '/my/cmd'").throw;
		};

		receiveFunc = { |msg, time, replyAddr, recvPort|
			if (msg[0] != '/status.reply') {
				cmdPatterns.do({ |pattern|
					addrKey = (replyAddr.ip ++ ":" ++replyAddr.port).asSymbol;
					if (pattern.matchRegexp(msg[0].asString)) {
						if (incomingCmds.keys.includes(addrKey).not) {
							incomingCmds.put(addrKey, []);
						};
						if (incomingCmds[addrKey].includes(msg[0]).not) {
							incomingCmds[addrKey] = incomingCmds[addrKey].add(msg[0]);
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

	connectWidgets {
		CVCenter.cvWidgets.pairsDo({ |key, wdgt|
			if (widgetsToBeConnected.includes(key)) {
				[key, wdgt].postln;
				// TODO
			}
		});
	}
}