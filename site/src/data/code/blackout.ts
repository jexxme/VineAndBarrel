// The blackout endings in player words. Everything that is a number or a list (weights, poses, souvenirs, ranges, the
// wake-up lines) comes from the game: VBDocsExport writes rules.tipsy.blackout, extract.mjs adds the lines from the lang
// file. Only the names and the description of where each ending can happen are written here, from the finders in
// tipsy/BlackoutSpots.java (one per ending). Citations are for maintainers; pages never show them.
export type Ranges = {
	home: number;
	openMin: number;
	openMax: number;
	animal: number;
	hay: number;
	tavern: number;
	villager: number;
	villagerNearBed: number;
	roof: number;
	roofMinHeight: number;
	roofMaxHeight: number;
	boat: number;
	monsterFree: number;
};

import { t, type Locale } from '../../lib/i18n';

/** Names and places in the page's language (t() keys are the English text). */
export const endingText: Record<string, { name: (l: Locale) => string; where: (r: Ranges, l: Locale) => string }> = {
	// BlackoutSpots.home (254-277)
	home: { name: (l) => t('Home', l), where: (r, l) => t('In your own bed, if it is in this dimension, within {home} blocks, still stands and nobody lies in it.', l, { home: r.home }) },
	// BlackoutSpots.inTheOpen (320-340)
	in_the_open: { name: (l) => t('In the open', l), where: (r, l) => t('Somewhere on the surface {openMin} to {openMax} blocks away, under the open sky.', l, { openMin: r.openMin, openMax: r.openMax }) },
	// BlackoutSpots.farmyard (341-369)
	farmyard: { name: (l) => t('Farmyard', l), where: (r, l) => t('Right next to an animal within {animal} blocks, out in the open (a pasture, not a barn), and it stares at you. Never next to anything that bites or stings back: no wolves, bees or polar bears.', l, { animal: r.animal }) },
	// BlackoutSpots.inTheHay (376-404), #vineandbarrel:hay_bales
	in_the_hay: { name: (l) => t('In the hay', l), where: (r, l) => t('On top of a hay bale or a Barley Bale within {hay} blocks, under the open sky (a haystack, not a barn).', l, { hay: r.hay }) },
	// BlackoutSpots.tavern (405-457)
	tavern: { name: (l) => t('Tavern', l), where: (r, l) => t("In front of a barrel in one of the mod's village taverns within {tavern} blocks, looking at it.", l, { tavern: r.tavern }) },
	// BlackoutSpots.someoneElsesBed (278-303)
	someone_elses_bed: { name: (l) => t("Someone else's bed", l), where: (r, l) => t("In a free villager's bed in a village house within {villager} blocks, with its villager awake and within {villagerNearBed} blocks of it. The villager shakes its head at you, and keeps its bed. Beds in your own builds never count.", l, { villager: r.villager, villagerNearBed: r.villagerNearBed }) },
	// BlackoutSpots.onTheRoof (458-520)
	on_the_roof: { name: (l) => t('On the roof', l), where: (r, l) => t('On the highest spot within {roof} blocks that stands {roofMinHeight} to {roofMaxHeight} blocks above the ground around it, under the open sky: a roof, a tower, a tall rock. Never on a cliff edge: walking off any side is a bump, not a fall.', l, { roof: r.roof, roofMinHeight: r.roofMinHeight, roofMaxHeight: r.roofMaxHeight }) },
	// BlackoutSpots.inABoat (520-)
	in_a_boat: { name: (l) => t('In a boat', l), where: (r, l) => t('Sitting in a brand new oak boat on calm water within {boat} blocks, under the open sky. The boat is yours to keep.', l, { boat: r.boat }) },
	// BlackoutEnding.RIGHT_HERE: the fallback; its souvenir comes from all the others' pools
	right_here: { name: (l) => t('Right here', l), where: (_r, l) => t('Where you fell, when none of the others is possible: in the Nether, in the End, sealed in underground. Its souvenir comes from all the others.', l) },
};
