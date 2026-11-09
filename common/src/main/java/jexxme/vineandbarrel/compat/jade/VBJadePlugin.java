package jexxme.vineandbarrel.compat.jade;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration: what a kettle is brewing and a barrel's status line, at a glance. Jade is optional: only Jade
 * loads this class. The kettle's progress lives on the server, so it is sent along; the barrel's client already knows
 * its batch and age (the same data its bubbles and fill gauge use).
 */
@WailaPlugin(VineAndBarrel.MODID)
public class VBJadePlugin implements IWailaPlugin
{
	@Override
	public void register(IWailaCommonRegistration registration) {
		registration.registerBlockDataProvider(KettleDataProvider.INSTANCE, BrewKettleBlockEntity.class);
	}

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		registration.registerBlockComponent(KettleProvider.INSTANCE, BrewKettleBlock.class);
		registration.registerBlockComponent(KettleProvider.INSTANCE, jexxme.vineandbarrel.kettle.BrewKettlePartBlock.class);
		registration.registerBlockComponent(BarrelProvider.INSTANCE, AgingBarrelBlock.class);
		registration.registerBlockComponent(PlacedDrinksProvider.INSTANCE, jexxme.vineandbarrel.drink.PlacedDrinksBlock.class);
		// Out cold: the screen is black, so Jade shows nothing either (its tooltip would draw over the blackout)
		registration.addRayTraceCallback((hit, accessor, original) -> JadeClient.blackedOut() ? null : accessor);
	}
}
