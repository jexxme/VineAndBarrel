// Code facts about the optional mods. Citations are for maintainers; pages never show them.
export const fd = {
	// META-INF/neoforge.mods.toml: farmersdelight versionRange "[1.3.3,)", the first 26.x build of Farmer's Delight Neoforged
	minVersion: '1.3.3',
	src: 'neoforge/src/main/resources/META-INF/neoforge.mods.toml:37-42',
	// The Fabric port (same mod id farmersdelight): fabric.mod.json suggests it; the Fabric FD GameTest run uses it
	refabricated: 'https://www.curseforge.com/minecraft/mc-mods/farmers-delight-refabricated',
	// Minecraft 1.21.1 (NeoForge) and 1.20.1 (Forge) use vectorwing's original mod: versionRange "[1.3.4,)" (1.20.1: "[1.20.1-1.3.4,)") in the
	// 1.21.1 branch's neoforge.mods.toml and the 1.20.1 branch's forge mods.toml
	original: 'https://www.curseforge.com/minecraft/mc-mods/farmers-delight',
	originalMinVersion: '1.3.4',
};
