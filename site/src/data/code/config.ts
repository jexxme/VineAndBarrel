// Code facts: every configuration option of the mod (common tipsy/TipsyConfig.java is the only config spec; the loaders
// write it out as files). The defaults and the levels come from the game's export (runtime.json tipsy.config,
// tipsy.*Level); the ranges and the wording are read from the spec. Citations are for maintainers; pages never show them.
// NeoForge: TOML files, keys under [section] (neoforge/NeoForgeConfig.java). Fabric: .properties files in the config
// folder, the same keys as `section.key=value` with the same comments and ranges (fabric/FabricConfig.java).
import { t, type Locale } from '../../lib/i18n';

type Levels = { slurLevel: number; stumbleLevel: number; rowdyLevel: number; cutOffLevel: number };
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
			{ key: 'stumbling', fallback: true, what: (lv, l = 'en') => t('Unsteady legs at level {stumbleLevel}: the drift while walking, the stumbles and the trips.', l, { stumbleLevel: lv.stumbleLevel }) },
			{ key: 'noddingOff', fallback: true, what: (_lv, l = 'en') => t('Kept for nodding off on your feet; it changes nothing yet.', l) },
			{ key: 'hangover', fallback: true, what: (_lv, l = 'en') => t('Kept for the hangover; it changes nothing yet.', l) },
			{ key: 'blackout', fallback: true, what: (_lv, l = 'en') => t('Black out after one drink too many at the top level.', l) },
			{ key: 'rowdy', fallback: true, what: (lv, l = 'en') => t('Rowdy from level {rowdyLevel}: the wild punches and being cut off at the bar. `false` turns both off; the next two turn off just one.', l, { rowdyLevel: lv.rowdyLevel }) },
			{ key: 'rowdyPunches', fallback: true, what: (lv, l = 'en') => t('From level {rowdyLevel}: now and then a wild punch at whoever is in front.', l, { rowdyLevel: lv.rowdyLevel }) },
			{ key: 'cutOff', fallback: true, what: (lv, l = 'en') => t('From level {cutOffLevel}: villagers and wandering traders refuse to trade.', l, { cutOffLevel: lv.cutOffLevel }) },
		] as Option[],
	},
	client: {
		file: 'vineandbarrel-tipsy-client.toml',
		fabricFile: 'vineandbarrel-tipsy-client.properties',
		section: 'tipsy',
		options: [
			{ key: 'drunkScreen', fallback: 100, range: [0, 100], what: (_lv, l = 'en') => t("How much being tipsy moves your own view, in percent: a hiccup's flick, a stumble's dip, a trip. 0 keeps it still (for motion sickness). Tipsy never moves your view on its own. Vanilla's Distortion Effects slider scales it too. It always wins over the server.", l) },
			{ key: 'otherPlayersSway', fallback: true, what: (_lv, l = 'en') => t('Show tipsy players swaying, hiccuping and stumbling, yourself in third person included.', l) },
			{ key: 'doubleVision', fallback: true, what: (_lv, l = 'en') => t('Kept for double vision; it changes nothing yet.', l) },
			{ key: 'heartbeatVolume', fallback: 1, range: [0, 1], what: (_lv, l = 'en') => t('How loud you hear your own heart at Tipsy V (only you hear it). 0 turns it off.', l) },
		] as Option[],
	},
	src: 'tipsy/TipsyConfig.java:29-71, tipsy/TipsyEvents.java:34-38, neoforge/NeoForgeConfig.java:38-50, fabric/FabricConfig.java:27-56',
};
