package jexxme.vineandbarrel.compat.appleskin;

/**
 * AppleSkin integration: its tooltip and hunger bar preview show what a drink really feeds. AppleSkin already reads
 * the food of our juices and meals by itself; wheat beer feeds through its Saturation buff instead ({@link DrinkFood}),
 * so we hand AppleSkin those values as the drink's own. Plain wines, ales and ciders feed nothing and stay without a
 * preview. AppleSkin is optional: {@link jexxme.vineandbarrel.compat.ModCompat} switches it on only when AppleSkin is
 * installed, through the loader (AppleSkin's event differs a little between its NeoForge and Fabric builds:
 * {@code AppleSkinNeoForge}, {@code AppleSkinFabric}).
 */
public final class AppleSkinCompat
{
	public static final String MODID = "appleskin";

	private AppleSkinCompat() {
	}
}
