// Code facts about the Brew Kettle screen: slot and widget positions in GUI pixels. The wiki draws the real screen
// texture with items at these positions. Citations are for maintainers; pages never show them.
export const kettleScreen = {
	// kettle/BrewKettleMenu.java: ingredients 30 + column * 18, 17 + row * 18 (2 rows x 3 columns), nothing else
	inputs: [30, 17],
	step: 18,
	// kettle/client/BrewKettleScreen.java: GAUGE_X/Y 120,16 (20 x 55); LIQUID_X/Y 123,19 (14 x 49, filled from the
	// bottom to the brew's share of the servings); the glass over it at GLASS_U/V 176,64
	gauge: [120, 16, 20, 55],
	liquid: [123, 19, 14, 49],
	glass: [176, 64],
	// HEAT_X/Y 47,55 (17 x 15, sprite at 176,0); ARROW_X/Y 89,25 (24 x 17, sprite at 176,15); title at x 28
	heat: [47, 55, 17, 15, 176, 0],
	arrow: [89, 25, 24, 17, 176, 15],
	titleX: 28,
	size: [176, 166],
	src: 'kettle/BrewKettleMenu.java:58-63, kettle/client/BrewKettleScreen.java:29-34',
};
