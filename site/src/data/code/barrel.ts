// Code facts about the Aging Barrel's status line (the action-bar text on an empty-hand right-click), so the wiki's
// aging timeline words "when" exactly like the game does. Citations are for maintainers; pages never show them.
export const barrelStatus = {
	// barrel/BarrelStatus.java:15-16: a day is 24000 ticks; under 1200 ticks (a minute) to go is "any minute now"
	soon: 1200,
	// BarrelStatus.partOfDay (78-84): morning until 6000, afternoon until 12000, evening until 13000, then night.
	// Days start at sunrise (day time 0) and include the following night.
	parts: [
		[6000, 'morning'],
		[12000, 'afternoon'],
		[13000, 'evening'],
		[24000, 'night'],
	] as [number, string][],
	// assets/vineandbarrel/lang/en_us.json, message.vineandbarrel.aging_barrel.*
	text: {
		status: '%1 · %2 · %3',
		servings: '%1 servings',
		oneServing: '1 serving',
		fermenting: 'fermenting, ready %1',
		aging: 'aging, aged %1',
		aged: 'aged',
		stillFermenting: 'Still fermenting: %1 is ready %2',
		soon: 'any minute now',
		inDays: 'in %1 days',
		today: { morning: 'this morning', afternoon: 'this afternoon', evening: 'this evening', night: 'tonight' },
		tomorrow: { morning: 'tomorrow morning', afternoon: 'tomorrow afternoon', evening: 'tomorrow evening', night: 'tomorrow night' },
	},
	src: 'barrel/BarrelStatus.java:15-84, lang/en_us.json message.vineandbarrel.aging_barrel.*',
};
