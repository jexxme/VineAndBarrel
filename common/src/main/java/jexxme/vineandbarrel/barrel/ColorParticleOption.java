package jexxme.vineandbarrel.barrel;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.FastColor;

/**
 * A particle in a colour (ARGB): the tap drip in its drink's colour. 1.20.1 has no {@code ColorParticleOption} of its
 * own (vanilla's came with 1.20.5), so this is ours, with the same name and methods.
 */
public class ColorParticleOption implements ParticleOptions
{
	/** {@code /particle vineandbarrel:tap_drip <argb as a number>}, and the packet: the colour as one int. */
	@SuppressWarnings("deprecation")
	public static final ParticleOptions.Deserializer<ColorParticleOption> DESERIALIZER = new ParticleOptions.Deserializer<>()
	{
		@Override
		public ColorParticleOption fromCommand(ParticleType<ColorParticleOption> type, StringReader reader) throws CommandSyntaxException {
			reader.expect(' ');
			return new ColorParticleOption(type, reader.readInt());
		}

		@Override
		public ColorParticleOption fromNetwork(ParticleType<ColorParticleOption> type, FriendlyByteBuf buf) {
			return new ColorParticleOption(type, buf.readInt());
		}
	};

	private final ParticleType<ColorParticleOption> type;
	private final int color;

	private ColorParticleOption(ParticleType<ColorParticleOption> type, int color) {
		this.type = type;
		this.color = color;
	}

	public static ColorParticleOption create(ParticleType<ColorParticleOption> type, int color) {
		return new ColorParticleOption(type, color);
	}

	public static Codec<ColorParticleOption> codec(ParticleType<ColorParticleOption> type) {
		return Codec.INT.xmap(color -> new ColorParticleOption(type, color), option -> option.color).fieldOf("color").codec();
	}

	@Override
	public ParticleType<ColorParticleOption> getType() {
		return type;
	}

	@Override
	public void writeToNetwork(FriendlyByteBuf buf) {
		buf.writeInt(color);
	}

	@Override
	public String writeToString() {
		return BuiltInRegistries.PARTICLE_TYPE.getKey(type) + " " + color;
	}

	public float getRed() {
		return FastColor.ARGB32.red(color) / 255.0F;
	}

	public float getGreen() {
		return FastColor.ARGB32.green(color) / 255.0F;
	}

	public float getBlue() {
		return FastColor.ARGB32.blue(color) / 255.0F;
	}

	public float getAlpha() {
		return FastColor.ARGB32.alpha(color) / 255.0F;
	}
}
