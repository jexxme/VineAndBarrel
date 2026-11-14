// Code facts: every configuration option of the mod (common tipsy/TipsyConfig.java and world/WorldgenConfig.java; the
// loaders write them out as files). The defaults and the levels come from the game's export (runtime.json tipsy.config,
// crops.config, tipsy.*Level); the ranges and the wording are read from the spec. Citations are for maintainers; pages never show them.
// NeoForge: TOML files, keys under [section] (neoforge/NeoForgeConfig.java). Fabric: .properties files in the config
// folder, the same keys as `section.key=value` with the same comments and ranges (fabric/FabricConfig.java).
import { t, type Locale } from '../../lib/i18n';

type Levels = {
	slurLevel: number;
	stumbleLevel: number;
	rowdyLevel: number;
	cutOffLevel: number;
	drunkWalkLevel: number;
	nodLevel: number;
	doubleVisionLevel: number;
	woollyLevel: number;
	heartbeatLevel: number;
	hangover: { sleepLevel: number };
};
/** `what` says what the option does, in the page's language (t() keys are the English text; `code` in backticks). */
export type Option = { key: string; fallback: boolean | number; range?: [number, number]; what: (lv: Levels, l?: Locale) => string };
export const configFiles = {
	server: {
		// NeoForgeConfig.java:24: ModConfig.Type.SYNCED, so the server's values go to every player who joins
		file: 'vineandbarrel-tipsy-server.toml',
		fabricFile: 'vineandbarrel-tipsy-server.properties',
		section: 'tipsy',
		options: [
			{ key: 'enabled', fallback: true, what: (_lv, l = 'en') => t("`false` is the non-alcoholic mode: drinks never make you tipsy, but keep their buffs and names. Switching it off mid-game takes Tipsy away from everyone at once.", l) },
			{ key: 'maxLevel', fallback: 5, range: [1, 5], what: (_lv, l = 'en') => t('The highest level drinks can stack to. A lower cap also leaves out everything above it (no-buff, slurred chat, Rowdy, stumbling).', l) },
			{ key: 'secondsPerLevel', fallback: 90, range: [1, 3600], what: (_lv, l = 'en') => t('How long each level lasts before it drops by one.', l) },
			{ key: 'slurredChat', fallback: true, what: (lv, l = 'en') => t('Slurred chat from level {slurLevel}.', l, { slurLevel: lv.slurLevel }) },
			{ key: 'stumbling', fallback: true, what: (lv, l = 'en') => t('Drunk legs from level {drunkWalkLevel}: steps that come late and a walk that weaves; from level {stumbleLevel} the stumbles, the trips and heavy legs.', l, { drunkWalkLevel: lv.drunkWalkLevel, stumbleLevel: lv.stumbleLevel }) },
			{ key: 'noddingOff', fallback: true, what: (lv, l = 'en') => t('From level {nodLevel}: stand still a while and you nod off on your feet.', l, { nodLevel: lv.nodLevel }) },
			{ key: 'hangover', fallback: true, what: (lv, l = 'en') => t("After a blackout, or a night's sleep from level {sleepLevel}, you wake up hungover. `false`: a blackout leaves you at Tipsy I and hungry instead.", l, { sleepLevel: lv.hangover.sleepLevel }) },
			{ key: 'blackout', fallback: true, what: (_lv, l = 'en') => t('One drink too many at the top level catches up with you a little later: you go under and black out.', l) },
			{ key: 'rowdy', fallback: true, what: (lv, l = 'en') => t('Rowdy from level {rowdyLevel}: the wild punches and being cut off at the bar. `false` turns both off; the next two turn off just one.', l, { rowdyLevel: lv.rowdyLevel }) },
			{ key: 'rowdyPunches', fallback: true, what: (lv, l = 'en') => t('From level {rowdyLevel}: now and then a wild punch at whoever is in front.', l, { rowdyLevel: lv.rowdyLevel }) },
			{ key: 'cutOff', fallback: true, what: (lv, l = 'en') => t('From level {cutOffLevel}: villagers and wandering traders refuse to trade.', l, { cutOffLevel: lv.cutOffLevel }) },
		] as Option[],
	},
	worldgen: {
		// world/WorldgenConfig.java: the same server file, its own section; read as each new chunk is decorated
		file: 'vineandbarrel-tipsy-server.toml',
		fabricFile: 'vineandbarrel-tipsy-server.properties',
		section: 'worldgen',
		options: [
			{ key: 'wildGrapes', fallback: true, what: (_lv, l = 'en') => t('Wild red grapes grow in new chunks. `false`: new chunks get none, the ones already out there stay.', l) },
			{ key: 'wildWhiteGrapes', fallback: true, what: (_lv, l = 'en') => t('Wild white grapes grow in new chunks. `false`: new chunks get none, the ones already out there stay.', l) },
			{ key: 'wildHops', fallback: true, what: (_lv, l = 'en') => t('Wild hops grow in new chunks. `false`: new chunks get none, the ones already out there stay.', l) },
			{ key: 'wildBarley', fallback: true, what: (_lv, l = 'en') => t('Wild barley grows in new chunks. `false`: new chunks get none, what is already out there stays.', l) },
		] as Option[],
	},
	client: {
		file: 'vineandbarrel-tipsy-client.toml',
		fabricFile: 'vineandbarrel-tipsy-client.properties',
		section: 'tipsy',
		options: [
			{ key: 'drunkScreen', fallback: 100, range: [0, 100], what: (_lv, l = 'en') => t("How much being tipsy moves your own view, in percent: a hiccup's flick, a stumble's dip, a trip. 0 keeps it still (for motion sickness). Tipsy never moves your view on its own. Vanilla's Distortion Effects slider scales it too. It always wins over the server.", l) },
			{ key: 'otherPlayersSway', fallback: true, what: (_lv, l = 'en') => t('Show tipsy players swaying, hiccuping and stumbling, yourself in third person included.', l) },
			{ key: 'doubleVision', fallback: true, what: (lv, l = 'en') => t("Drunk eyes: soft screen edges, from level {doubleVisionLevel} double vision and fading signs, and the hangover's glare. `false` keeps your view clear.", l, { doubleVisionLevel: lv.doubleVisionLevel }) },
			{ key: 'woollySound', fallback: true, what: (lv, l = 'en') => t('From level {woollyLevel}, the world sounds muffled. Music, menus and your own heartbeat stay clear.', l, { woollyLevel: lv.woollyLevel }) },
			{ key: 'heartbeatVolume', fallback: 1, range: [0, 1], what: (_lv, l = 'en') => t('How loud you hear your own heart at Tipsy V (only you hear it). 0 turns it off.', l) },
		] as Option[],
	},
	src: 'tipsy/TipsyConfig.java:29-71, world/WorldgenConfig.java, tipsy/TipsyEvents.java:34-38, neoforge/NeoForgeConfig.java:38-50, fabric/FabricConfig.java:27-56',
};
