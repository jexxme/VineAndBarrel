// Code facts: how each advancement is earned, exactly, in player words (the advancement files only say what the tab
// shows). Citations are for maintainers; pages never show them.
// advancement/VBAdvancementGenerator.java:54-97 unless noted
import { t, type Locale } from '../../lib/i18n';

/** In the page's language (t() keys are the English text). */
export const advancementHints: Record<string, (l: Locale) => string> = {
	'vineandbarrel:main/root': (l) => t('Unlocks as soon as you pick anything up.', l),
	// kettle/BrewKettleBlockEntity.java:380-388
	'vineandbarrel:main/first_brew': (l) => t('Take a finished brew out of a Brew Kettle: with a bottle or bucket, or from its screen.', l),
	// barrel/AgingBarrelBlockEntity.java:90-94, 283
	'vineandbarrel:main/patience_pays': (l) => t("Tap an aged drink from a barrel you filled yourself. A tavern's aged barrel doesn't count; one you emptied and refilled does.", l),
	'vineandbarrel:main/vintner': (l) => t("Drink red wine, white wine and sweet berry wine, each young and aged. Melon wine isn't needed.", l),
	'vineandbarrel:main/brewmaster': (l) => t('Drink pale ale and wheat beer, each young and aged.', l),
	'vineandbarrel:main/mead_hall': (l) => t('Drink mead, young and aged.', l),
	'vineandbarrel:main/cider_house': (l) => t('Drink hard cider, young and aged.', l),
	'vineandbarrel:main/cellar_master': (l) => t('Place an Aging Barrel on top of another.', l),
	// barrel/TappedBarrelTrigger.java:27-29
	'vineandbarrel:main/on_tap': (l) => t('Tap any barrel that stands inside a village, your own included.', l),
	// drink/Cheers.java:29-113
	'vineandbarrel:main/cheers': (l) => t('Finish a drink at the same moment as another player nearby. Everyone in the round gets it.', l),
	// tipsy/TipsyEvents.java:61-64
	'vineandbarrel:main/sleep_it_off': (l) => t('Be tipsy when a night you slept through ends.', l),
	'vineandbarrel:main/where_am_i': (l) => t('Only with the blackout server option on.', l),
	// advancement/BlackoutTrigger.java: every collectable ending once
	'vineandbarrel:main/lost_weekend': (l) => t('Only with the blackout server option on.', l),
};
