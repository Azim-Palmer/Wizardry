package electroblob.wizardry.spell;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.constants.SpellType;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.entity.living.EntityWizard;
import electroblob.wizardry.item.ItemScroll;
import electroblob.wizardry.item.ItemSpellBook;
import electroblob.wizardry.packet.PacketSpellProperties;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.SpellProperties;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generic spell class which is the superclass to all spells in wizardry. When extending this class, you must do the
 * following:
 * <p></p>
 * - Have a constructor which passes all necessary constants into the super constructor. I define the constants here so
 * that the constructor for an individual spell has no parameters, but you may prefer to pass in the parameters when the
 * spell is registered, so all the mana costs etc. are in one place like a sort of sandbox.
 * <p></p>
 * - Implement the {@link Spell#cast(World, EntityPlayer, EnumHand, int, SpellModifiers)} method, in which you should
 * execute the code that makes the spell work, and return true or false depending on whether the spell succeeded and
 * therefore whether mana should be used up.
 * <p></p>
 * - Register the spell using {@link RegistryEvent.Register}, with {@link Spell} as the type parameter. Each spell
 * should have a single instance, like blocks and items. <i>As of Wizardry 2.1, spells use the Forge registry system.
 * Related methods such as {@link Spell#metadata()} and {@link Spell#byMetadata(int)} have been re-routed to use this system, leaving
 * minimal external changes. Note also that the constructor automatically sets the registry name for you, though you may
 * change it afterwards if necessary.</i>
 * <p></p>
 * Also note that you can override some other methods from this class. For example, to add a specific kind of formatting
 * to a spell name or description, you can override {@link Spell#getDisplayName()},
 * {@link Spell#getDisplayNameWithFormatting()} or {@link Spell#getDescription()} and append the formatting code (though
 * you will have to call super() to get the name itself, since the unlocalised name is private). See
 * {@link SummonShadowWraith#getDescription()} for an example.
 * <hr>
 * This class is also home to some useful static methods for interacting with the spell registry:
 * <p></p>
 * {@link Spell#byMetadata(int)} gets a spell instance from its integer metadata, which corresponds to the metadata of its spell
 * book.<br>
 * {@link Spell#get(String)} gets a spell instance from its unlocalised name.<br>
 * {@link Spell#getSpells(Predicate)} returns a list of spell instances that match the given {@link Predicate}.<br>
 * {@link Spell#getTotalSpellCount()} returns the total number of registered spells.
 * <hr>
 * Spell implements the {@link Comparable} interface, and as such, any collection of spells can be sorted. Spells are
 * sorted by increasing tier (see {@link Tier}), from novice to master. Within each tier, spells are sorted by element,
 * the order of which is as defined in {@link Element} (i.e. magic, fire, ice, lightning, necromancy, earth, sorcery,
 * healing).
 * <hr>
 *
 * @since Wizardry 1.0
 * @see ItemSpellBook ItemSpellBook
 * @see ItemScroll ItemScroll
 * @see Spells
 */
@Mod.EventBusSubscriber
public abstract class Spell extends IForgeRegistryEntry.Impl<Spell> implements Comparable<Spell> {

	// Spell checklist:
	// - Create and register spell, add texture and properties json file
	// - Add name AND description to lang files
	// - Add sound(s) to sounds.json
	// - Add to advancements/all_spells.json

	// Common property identifiers
	public static final String DAMAGE = "damage";
	public static final String RANGE = "range";
	public static final String DURATION = "duration";
	public static final String EFFECT_RADIUS = "effect_radius";
	public static final String BLAST_RADIUS = "blast_radius";
	public static final String EFFECT_DURATION = "effect_duration";
	public static final String EFFECT_STRENGTH = "effect_strength";
	public static final String BURN_DURATION = "burn_duration";
	public static final String DIRECT_DAMAGE = "direct_damage";
	public static final String SPLASH_DAMAGE = "splash_damage";
	public static final String HEALTH = "health";
	public static final String SEEKING_STRENGTH = "seeking_strength";
	public static final String DIRECT_EFFECT_DURATION = "direct_effect_duration";
	public static final String DIRECT_EFFECT_STRENGTH = "direct_effect_strength";
	public static final String SPLASH_EFFECT_DURATION = "splash_effect_duration";
	public static final String SPLASH_EFFECT_STRENGTH = "splash_effect_strength";

	/** Forge registry-based replacement for the internal spells list. */
	public static IForgeRegistry<Spell> registry;

	/** The unlocalised name of the spell. */
	private final String unlocalisedName;
	/** This spell's associated SpellProperties object. */
	private SpellProperties properties;
	/** A reference to the global spell properties for this spell, so they are only loaded once. */
	private SpellProperties globalProperties;
	/** Used in initialisation. */
	private Set<String> propertyKeys = new HashSet<>();

	/** The action the player does when this spell is cast. */
	public final EnumAction action;
	/** Whether or not the spell is continuous (keeps going as long as the mouse button is held) */
	public final boolean isContinuous;

	/** ResourceLocation of the spell icon. */
	private final ResourceLocation icon;

	/** False if the spell has been disabled in the config file, true otherwise. This is now encapsulated to stop it
	 * being fiddled with. */
	private boolean enabled = true;

	/** The sound(s) played when this spell is cast. */
	@Nullable
	protected final SoundEvent[] sounds;
	/** The volume of the sound played when this spell is cast. Defaults to 1. */
	protected float volume = 1;
	/** The pitch of the sound played when this spell is cast. Defaults to 1. */
	protected float pitch = 1;
	/** The pitch variation of the sound played when this spell is cast. Defaults to 0. */
	protected float pitchVariation = 0;

	private static int nextSpellId = 0;
	/** The spell's integer ID, mainly used for networking. */
	// This was added after I learnt the hard way why you can't assume Forge's registry IDs are sequential...
	private final int id;

	/**
	 * This constructor should be called from any subclasses, either feeding in the constants directly or through their
	 * own constructor from wherever the spell is registered. This is the constructor for wizardry's own spells; spells
	 * added by other mods should use
	 * {@link Spell#Spell(String, String, EnumAction, boolean)}.
	 * @param name The <i>registry name</i> of the spell. This will also be the name of the icon file. The spell's
	 *        unlocalised name will be a resource location with the format [modid]:[name].
	 * @param action The vanilla usage action to be displayed when casting this spell.
	 * @param isContinuous Whether this spell is continuous, meaning you cast it for a length of time by holding the
	 */
	public Spell(String name, EnumAction action, boolean isContinuous){
		this(Wizardry.MODID, name, action, isContinuous);
	}

	/**
	 * This constructor should be called from any subclasses, either feeding in the constants directly or through their
	 * own constructor from wherever the spell is registered.
	 * @param modID The mod id of the mod that added this spell. This allows wizardry to use the correct file path for
	 *        the spell icon, and also more generally to distinguish between original and addon spells.
	 * @param name The <i>registry name</i> of the spell, excluding the mod id. This will also be the name of the icon
	 *        file. The spell's unlocalised name will be a resource location with the format [modid]:[name].
	 * @param action The vanilla usage action to be displayed when casting this spell (see {@link}EnumAction)
	 * @param isContinuous Whether this spell is continuous, meaning you cast it for a length of time by holding the
	 */
	public Spell(String modID, String name, EnumAction action, boolean isContinuous){
		this.setRegistryName(modID, name);
		this.unlocalisedName = this.getRegistryName().toString();
		this.action = action;
		this.isContinuous = isContinuous;
		this.icon = new ResourceLocation(modID, "textures/spells/" + name + ".png");
		this.sounds = createSounds();
		this.id = nextSpellId++;
	}

	// ========================================= Initialisation methods ===========================================

	/** Called from {@code init()} in the main mod class. Used to initialise spell fields and properties that depend on
	 * other things being registered (e.g. potions). <i>Always initialise things in the constructor wherever possible.</i> */
	public void init(){}

	/**
	 * Called from the constructor to initialise this spell's sounds. By default, this creates and returns a 1-element
	 * array containing a single sound event called {@code spell.[unlocalised name]}. Override this to add a custom
	 * sound array, perhaps using one of the convenience methods (see below).
	 * @return An array of sound events played by this spell.
	 * @see Spell#createSoundWithSuffix(String)
	 * @see Spell#createContinuousSpellSounds
	 * @see Spell#playSound(World, double, double, double, int, int, SpellModifiers, String...)
	 */
	protected SoundEvent[] createSounds(){
		return new SoundEvent[]{WizardrySounds.createSound("spell." + this.getRegistryName().getPath())};
	}

	// Note 1: The aim here is conciseness. Keeping the identifiers in the spell classes means we don't usually have to
	// qualify them with a class name, we can simply type RANGE or whatever.
	// Note 2: Identifiers should be named concisely but descriptively. Usually, "range" will suffice, but "duration"
	// is somewhat ambiguous - is it the duration of a potion effect, a conjured item, a summoned mob or the casting
	// itself? This is why it is qualified as "effect_duration", "minion_lifetime", etc.

	/**
	 * Adds the given JSON identifiers to the configurable base properties of this spell. This should be called from
	 * the constructor or {@link Spell#init()}. <i>It is highly recommended that property keys be defined as constants,
	 * as they will be needed later to retrieve the properties during the casting methods.</i>
	 * <p></p>
	 * General spell classes will call this method to set any properties they require in order to work properly, and
	 * the relevant keys will be public constants.
	 * @param keys One or more spell property keys to add to the spell. By convention, these are lowercase_with_underscores.
	 *             If any of these already exists, a warning will be printed to the console.
	 * @return The spell instance, allowing this method to be chained onto the constructor.
	 * @throws IllegalStateException if this method is called after the spell properties have been initialised.
	 */
	// Nobody can remove property keys, which guarantees that spell classes always have the properties they need.
	// It also means that subclasses need not worry about properties already defined and used in their superclass.
	// Conversely, general spell classes ONLY EVER define the properties they ACTUALLY USE.
	public final Spell addProperties(String... keys){

		if(arePropertiesInitialised()) throw new IllegalStateException("Tried to add spell properties after they were initialised");

		for(String key : keys) if(propertyKeys.contains(key)) Wizardry.logger.warn("Tried to add a duplicate property key '"
		+ key + "' to spell " + this.getRegistryName());

		Collections.addAll(propertyKeys, keys);

		return this;
	}

	/** Internal, do not use. */
	public final String[] getPropertyKeys(){
		return propertyKeys.toArray(new String[0]);
	}

	/** Returns true if this spell's properties have been initialised, false if not. Check this if you're attempting
	 * to access them from code that could be called before wizardry's {@code init()} method (e.g. item attributes). */
	public final boolean arePropertiesInitialised(){
		return properties != null;
	}

	/** Sets this spell's properties to the given {@link SpellProperties} object, but only if it doesn't already
	 * have one. This prevents spell properties from being changed after initialisation. */
	public void setProperties(@Nonnull SpellProperties properties){

		if(!arePropertiesInitialised()){
			this.properties = properties;
			if(this.globalProperties == null) this.globalProperties = properties;
		}else{
			Wizardry.logger.info("A mod attempted to set a spell's properties, but they were already initialised.");
		}
	}

	/** Called from the event handler when a player logs in. */
	public static void syncProperties(EntityPlayer player){
		if(player instanceof EntityPlayerMP){
			// On the server side, send a packet to the player to synchronise their spell properties
			// To avoid sending extra data unnecessarily, the spell properties are sent in order of spell ID
			List<Spell> spells = new ArrayList<>(registry.getValuesCollection());
			spells.sort(Comparator.comparingInt(Spell::networkID));
			WizardryPacketHandler.net.sendToAll(new PacketSpellProperties.Message(spells.stream()
					.map(s -> s.properties).toArray(SpellProperties[]::new)));
		}else{
			// On the client side, wipe the spell properties so the new ones can be set
			// TESTME: Can we guarantee this happens before the packet arrives?
			clearProperties();
		}
	}

	private static void clearProperties(){
		for(Spell spell : registry){
			spell.properties = null;
		}
	}

	// These three methods are final because they are for use by subclasses (and are pseudo-static, so to speak).

	/**
	 * Convenience method that generates a sound event for this spell, with the given suffix.
	 * @param suffix The suffix to use in the name of the returned sound event (excluding the dot)
	 * @return A sound event called {@code spell.[unlocalised name].[suffix]}, where [suffix] is the given string.
	 * @see Spell#createSoundsWithSuffixes(String[])
	 */
	public final SoundEvent createSoundWithSuffix(String suffix){
		return WizardrySounds.createSound("spell." + this.getRegistryName().getPath() + "." + suffix);
	}

	/**
	 * Compact version of {@link Spell#createSoundWithSuffix(String)} which accepts multiple suffixes and packs them
	 * into an array.
	 * @param suffixes 1 or more suffixes to use in the names of the returned sound events (excluding dots)
	 * @return An array of the resulting sound events.
	 */
	public final SoundEvent[] createSoundsWithSuffixes(String... suffixes){
		return Arrays.stream(suffixes).map(this::createSoundWithSuffix).toArray(SoundEvent[]::new);
	}

	/**
	 * Convenience method that generates an array of 3 sound events which can be fed directly into either of the
	 * continuous spell sound classes' constructors.
	 * @return An array of three sound events called {@code spell.[unlocalised name].start},
	 * {@code spell.[unlocalised name].loop} and {@code spell.[unlocalised name].end} respectively.
	 */
	public final SoundEvent[] createContinuousSpellSounds(){
		return createSoundsWithSuffixes("start", "loop", "end");
	}

	// ============================================ Casting methods ==============================================

	/**
	 * Casts the spell. Each subclass must override this method and within it execute the code to make the spell work.
	 * Returns a boolean so that the main onItemRightClick or onUsingItemTick method can check if the spell was actually
	 * cast or whether a spell specific condition caused it not to be (for example, heal won't work if the player is on
	 * full health), preventing unfair drain of mana.
	 * <p></p>
	 * Each spell must return true when it works or the spell will not use up mana. Note that (!world.isRemote) does not
	 * count as a condition; return true should be outside it - in other words, return a value on both the client and
	 * the server.
	 * <p></p>
	 * It's worth noting that on the client side, this method only gets called if the server side cast() method
	 * succeeded, so you can put any particle spawning code outside of any success conditions if there are discrepancies
	 * between client and server.
	 *
	 * @param world The world in which the spell is being cast.
	 * @param caster The EntityPlayer that cast the spell.
	 * @param hand The hand that is holding the item used to cast the spell. If no item was used, this will be the main
	 *        hand.
	 * @param ticksInUse The number of ticks the spell has already been cast for. For all non-continuous spells, this is
	 *        0 and is not used. For continuous spells, it is passed in as the maximum use duration of the item minus
	 *        the count parameter in onUsingItemTick and therefore it increases by 1 each tick.
	 * @param modifiers A {@link SpellModifiers} object containing the modifiers that have been applied to the spell.
	 *        See the javadoc for that class for more information. If no modifiers are required, pass in
	 *        {@code new SpellModifiers()}.
	 * @return True if the spell succeeded and mana should be used up, false if not.
	 */
	public abstract boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers);

	/**
	 * Casts the spell, but with an EntityLiving as the caster. Each subclass can optionally override this method and
	 * within it execute the code to make the spell work. Returns a boolean to allow whatever calls this method to check
	 * if the spell was actually cast or whether a spell specific condition caused it not to be (for example, heal won't
	 * work if the caster is on full health).
	 * <p></p>
	 * This method is intended for use by NPCs (see {@link EntityWizard}) so that they can cast spells. Override it if
	 * you want a spell to be cast by wizards. Note that you must also override {@link Spell#canBeCastByNPCs()} to
	 * return true to allow wizards to select the spell. For some spells, this method may well be exactly the same as
	 * the regular cast method; for others it won't be - for example, projectile-based spells are normally done using
	 * the player's look vector, but NPCs need to use a target-based method instead.
	 * <p></p>
	 * Each spell must return true when it works. Note that (!world.isRemote) does not count as a condition; return true
	 * should be outside it - in other words, return a value on both the client and the server.
	 * <p></p>
	 * It's worth noting that on the client side, this method only gets called if the server side cast() method
	 * succeeded, so you can put any particle spawning code outside of any success conditions if there are discrepancies
	 * between client and server.
	 *
	 * @param world The world in which the spell is being cast.
	 * @param caster The EntityLiving that cast the spell.
	 * @param hand The hand that is holding the item used to cast the spell. This will almost certainly be the main
	 *        hand.
	 * @param ticksInUse The number of ticks the spell has already been cast for. For all non-continuous spells, this is
	 *        0 and is not used.
	 * @param target The EntityLivingBase that is targeted by the spell. May be null in some cases.
	 * @param modifiers A {@link SpellModifiers} object containing the modifiers that have been applied to the spell.
	 *        See the javadoc for that class for more information. If no modifiers are required, pass in
	 *        {@code new SpellModifiers()}.
	 * @return True if the spell succeeded, false if not. Returns false by default.
	 */
	public boolean cast(World world, EntityLiving caster, EnumHand hand, int ticksInUse, EntityLivingBase target,
			SpellModifiers modifiers){
		return false;
	}

	/**
	 * Casts the spell, but with an origin and a direction instead of a caster. Each subclass can optionally override this
	 * method and within it execute the code to make the spell work. Returns a boolean to allow whatever calls this method
	 * to check if the spell was actually cast or whether a spell specific condition caused it not to be (for example, heal
	 * won't work if the caster is on full health).
	 * <p></p>
	 * This method is intended for use by dispensers and command blocks so that they can cast spells. Override it if
	 * you want a spell to be cast by dispensers. Note that you must also override {@link Spell#canBeCastByDispensers()} to
	 * return true to allow dispensers to select the spell. For some spells, this method may well be exactly the same as
	 * the regular cast method; for others it won't be - for example, projectile-based spells are normally done using
	 * the player's look vector, but dispensers need to use a facing-based method instead.
	 * <p></p>
	 * Each spell must return true when it works. Note that (!world.isRemote) does not count as a condition; return true
	 * should be outside it - in other words, return a value on both the client and the server.
	 * <p></p>
	 * It's worth noting that on the client side, this method only gets called if the server side cast() method
	 * succeeded, so you can put any particle spawning code outside of any success conditions if there are discrepancies
	 * between client and server.
	 *
	 * @param world The world in which the spell is being cast.
	 * @param x The x coordinate of the origin point of the spell.
	 * @param y The y coordinate of the origin point of the spell.
	 * @param z The z coordinate of the origin point of the spell.
	 * @param direction The cardinal (UDNSEW) direction in which the spell is being cast.
	 * @param ticksInUse The number of ticks the spell has already been cast for. For all non-continuous spells, this is
	 *        0 and is not used.
	 * @param duration The duration this spell will be cast for, or -1 if it will be cast indefinitely. For all
	 *                 non-continuous spells, this is 0 and is not used. This is intended for use in sound loops; there
	 *                 should be no need to use it for anything else.
	 * @param modifiers A {@link SpellModifiers} object containing the modifiers that have been applied to the spell.
	 *        See the javadoc for that class for more information. If no modifiers are required, pass in
	 *        {@code new SpellModifiers()}.
	 * @return True if the spell succeeded, false if not. Returns false by default.
	 */
	public boolean cast(World world, double x, double y, double z, EnumFacing direction, int ticksInUse, int duration, SpellModifiers modifiers){
		return false;
	}

	/**
	 * Called when the spell stops being cast, either from running out of mana, being stopped by the caster, or due
	 * to a stack of scrolls running out. <i>Only ever called for continuous spells.</i> This method is mostly used
	 * for adding particle effects and sounds on spell finish.
	 * <p></p>
	 * Because this method is not used in the majority of cases, it was deemed excessive to have three separate
	 * methods for players, NPCs and dispensers. Instead, some parameters may be null depending on the circumstances,
	 * similar to the implementation in {@link electroblob.wizardry.event.SpellCastEvent SpellCastEvent}.
	 * Be sure to check for this before using them!
	 *
	 * @param world The world in which the spell was cast.
	 * @param caster The player or NPC that cast the spell, or null if it was cast from a dispenser.
	 * @param x The x coordinate of the origin point of the spell, or NaN if the spell wasn't cast from a dispenser.
	 * @param y The y coordinate of the origin point of the spell, or NaN if the spell wasn't cast from a dispenser.
	 * @param z The z coordinate of the origin point of the spell, or NaN if the spell wasn't cast from a dispenser.
	 * @param direction The cardinal (UDNSEW) direction in which the spell was cast, or null if the spell wasn't cast
	 *                  from a dispenser.
	 * @param duration The number of ticks the spell was cast for.
	 * @param modifiers The modifiers the spell was cast with.
	 */
	// Conveniently, we can't always get a reference to the target for NPC casting once the spell ends (because it
	// might have died or run off, or the NPC might have lost interest...) - so let's just not bother!
	public void finishCasting(World world, @Nullable EntityLivingBase caster, double x, double y, double z,
							  @Nullable EnumFacing direction, int duration, SpellModifiers modifiers){}

	/**
	 * Whether NPCs such as wizards can cast this spell. If you have overridden
	 * {@link Spell#cast(World, EntityLiving, EnumHand, int, EntityLivingBase, SpellModifiers)}, you should override
	 * this to return true.
	 */
	public boolean canBeCastByNPCs(){
		return false;
	}

	/**
	 * Whether dispensers can cast this spell. If you have overridden
	 * {@link Spell#cast(World, double, double, double, EnumFacing, int, int, SpellModifiers)}, you should override this
	 * to return true.
	 */
	public boolean canBeCastByDispensers(){
		return false;
	}

	/**
	 * Whether this spell requires a packet to be sent when it is cast. Returns true by default, but can be overridden
	 * to return false <b>if</b> the spell's cast() method does not use any code that must be executed client-side (i.e.
	 * particle spawning). This is not checked for continuous spells, because they never need to send packets.
	 * <p></p>
	 * <i>If in doubt, leave this method as is; it is purely an optimisation.</i>
	 *
	 * @return <b>false</b> if the spell code should only be run on the server and the client of the player casting
	 *         it<br>
	 *         <b>true</b> if the spell code should be run on the server and all clients in the dimension
	 */
	// Edit: Turns out that swingItem() actually sends packets to all nearby clients, but not the client doing the
	// swinging.
	// Also, now I think about it, this method isn't going to make the slightest bit of difference to the item usage
	// actions since setItemInUse() is called in ItemWand, not the spell class - so the only thing that matters here is
	// the particles.
	public boolean requiresPacket(){
		return true;
	}

	// ============================================ Getter methods ==============================================

	/** Returns the metadata for this spell, which corresponds to its registry ID, or -1 if the spell has not been
	 * registered.<br>
	 * <br>
	 * Because of how the registry system works, this won't change once assigned for a given world so is guaranteed to
	 * be backwards-compatible by design. <b>However, for this reason if spells are removed there may be gaps in the ID
	 * numbers.</b> If a continuous set of IDs is required (for networking), use {@link Spell#networkID()}. */
	public final int metadata(){
		return ((ForgeRegistry<Spell>)registry).getID(this);
	}

	/** Returns this spell's network ID number, similar to mod-specific entity IDs.<br>
	 * <br>
	 * Unlike {@link Spell#metadata()}, this is guaranteed to be sequential so is suitable for indexed lookup.
	 * <b>However, it may change if spells are removed so is not backwards-compatible.</b> This means it should not be
	 * used for data storage. */
	public final int networkID(){
		return id;
	}

	/** Returns the {@code ResourceLocation} for this spell's icon. */
	public final ResourceLocation getIcon(){
		return icon;
	}

	/** Returns the {@code SoundEvent}s for this spell's sound. */
	public final SoundEvent[] getSounds(){
		return sounds;
	}

	// Property getters - these are final to force addon devs to use the JSON system instead of just overriding them

	/** Returns the tier that this spell belongs to. */
	public final Tier getTier(){
		return properties.tier;
	}

	/** Returns the element that this spell belongs to. */
	public final Element getElement(){
		return properties.element;
	}

	/** Returns the type of spell this is classified as. */
	public final SpellType getType(){
		return properties.type;
	}

	/** Returns the mana cost of the spell. If it is a continuous spell the cost is per second. */
	public final int getCost(){
		return properties.cost;
	}

	/** Returns the charge-up time for the spell in ticks. */
	public final int getChargeup(){
		return properties.chargeup;
	}

	/** Returns the cooldown for the spell in ticks. */
	public final int getCooldown(){
		return properties.cooldown;
	}

	/**
	 * Returns the base value specified in JSON for the given identifier. This may be used from within the spell
	 * class, or from elsewhere (entities, items, etc.) via the spell's instance.
	 *
	 * @param identifier The JSON identifier for the required property. This <b>must</b> have been defined using
	 *                   {@link Spell#addProperties(String...)} or an exception will be thrown.
	 * @return The base value of the property, as a {@code Number} object. Internally this is handled as a float, but
	 * it is passed through as a {@code Number} to avoid casting. <i>Be careful with rounding when extracting integer
	 * values! The JSON parser cannot guarantee that the property file has an integer value.</i>
	 * @throws IllegalArgumentException if no property was defined with the given identifier. */
	public final Number getProperty(String identifier){
		return properties.getBaseValue(identifier);
	}

	/** Returns whether the spell is enabled in any of the given {@link electroblob.wizardry.util.SpellProperties.Context Context}s.
	 * A spell may be disabled globally in the config, or it may be disabled for one or more specific contexts in
	 * its JSON file using a resource pack. If called with no arguments, defaults to any context, i.e. only returns
	 * false if the spell is completely disabled in all contexts. */
	public final boolean isEnabled(SpellProperties.Context... contexts){
		return enabled && (contexts.length == 0 || properties.isEnabled(contexts));
	}

	/** Sets whether the spell is enabled or not. */
	public final void setEnabled(boolean isEnabled){
		this.enabled = isEnabled;
	}

	/** Returns true if the given item has a variant for this spell. By default, returns true if the given item is
	 * either {@link WizardryItems#spell_book} or {@link WizardryItems#scroll}. Override to give the spell a special
	 * type of book or scroll. */
	public boolean applicableForItem(Item item){
		return item == WizardryItems.spell_book || item == WizardryItems.scroll;
	}

	/**
	 * Returns the unlocalised name of the spell, without any prefixes or suffixes, e.g. "flame_ray". <b>This should
	 * only be used for translation purposes.</b>
	 */
	public final String getUnlocalisedName(){
		return unlocalisedName;
	}

	// ========================================== Translation methods ============================================

	/* The general idea with translation is to use net.minecraft.client.resources.I18n directly on the client side (and
	 * just prepend formatting codes where necessary), and to use TextComponentTranslation on the server (setting the
	 * style as necessary). TextComponentTranslation effectively stores what needs to be translated, without actually
	 * translating it. If, for whatever reason, you need to supply an ITextComponent but don't want it translated
	 * (perhaps a name?), you can use TextComponentString, which will simply keep the raw string it is given. */

	/**
	 * Returns the translated display name of the spell, without formatting (i.e. not coloured). <b>Client-side
	 * only!</b> On the server side, use {@link TextComponentTranslation} (see {@link Spell#getNameForTranslation()}).
	 */
	@SideOnly(Side.CLIENT)
	public String getDisplayName(){
		return net.minecraft.client.resources.I18n.format("spell." + unlocalisedName);
	}

	/**
	 * Returns a {@code TextComponentTranslation} which will be translated to the display name of the spell, without
	 * formatting (i.e. not coloured).
	 */
	public ITextComponent getNameForTranslation(){
		return new TextComponentTranslation("spell." + unlocalisedName);
	}

	/**
	 * Returns the translated display name of the spell, with formatting (i.e. coloured). <b>Client-side only!</b> On
	 * the server side, use {@link TextComponentTranslation} (see {@link Spell#getNameForTranslationFormatted()}).
	 */
	@SideOnly(Side.CLIENT)
	public String getDisplayNameWithFormatting(){
		return this.getElement().getFormattingCode() + net.minecraft.client.resources.I18n.format("spell." + unlocalisedName);
	}

	/**
	 * Returns a {@code TextComponentTranslation} which will be translated to the display name of the spell, with
	 * formatting (i.e. coloured).
	 */
	public ITextComponent getNameForTranslationFormatted(){
		return new TextComponentTranslation("spell." + unlocalisedName).setStyle(this.getElement().getColour());
	}

	/**
	 * Returns the translated description of the spell, without formatting. <b>Client-side only!</b> You should not need
	 * to use this on the server side.
	 */
	@SideOnly(Side.CLIENT)
	public String getDescription(){
		return net.minecraft.client.resources.I18n.format("spell." + unlocalisedName + ".desc");
	}

	// ============================================ Sound methods ==============================================

	/**
	 * Sets the sound parameters for this spell.
	 * @param volume The volume of the sound played by this spell, relative to 1.
	 * @param pitch The pitch of the sound played by this spell, relative to 1.
	 * @param pitchVariation The random variation in the pitch of the sound played by this spell. The pitch at which the
	 * sound is played will be randomly chosen from the range: {@code pitch +/- pitchVariation}.
	 * @return The spell instance, allowing this method to be chained onto the constructor. Note that since this method
	 * only returns a {@code Spell}, if you are chaining multiple methods onto the constructor this should be called last.
	 */
	public Spell soundValues(float volume, float pitch, float pitchVariation){
		this.volume = volume;
		this.pitch = pitch;
		this.pitchVariation = pitchVariation;
		return this;
	}

	// The general motivation for the spell-based sound system is as follows:
	// - There are a lot of spells, and each spell has at least one sound event, which means a lot of sounds!
	// - Blocks and entities also define their own sounds, though this system is a bit half-hearted because they
	// still have to be registered manually
	// - Most spell sounds are used only from within their respective spells
	// - I don't want hundreds of simple spell sounds that aren't referenced elsewhere cluttering up WizardrySounds,
	// so I'm keeping them in the spell instead - if you really want them you can use getSounds, but really it's bad
	// practice to reuse other sound events (something I found out the hard way!)
	// - The pitch variation thing is annoying to keep repeating so it's also centralised here

	/**
	 * Plays this spell's sound at the given entity in the given world. This calls {@link Spell#playSound(World, double, double, double, int, int, SpellModifiers, String...)}, passing in the given entity's position as the xyz coordinates. Also checks if the given entity
	 * is silent, and if so, does not play the sound.
	 * <p></p>
	 * <i>If you are overriding the {@code Spell.playSound} methods, it is recommended that you override the xyz version
	 * instead of this one, since this method calls that one anyway - unless you want different behaviour for entities.</i>
	 * @param world The world to play the sound in.
	 * @param entity The entity to play the sound at, provided it is not silent.
	 * @param ticksInUse The number of ticks this spell has already been cast for, passed in from the {@code cast(...)}
	 * methods. Not used in the base method, but included for use by subclasses overriding this method.
	 * @param duration The number of ticks this spell will be cast for, passed in from the {@code cast(...)}
	 * methods. Not used in the base method, but included for use by subclasses overriding this method.
	 * @param modifiers The modifiers this spell was cast with, passed in from the {@code cast(...)} methods.
	 * @param sounds A number of strings representing the sounds to be played. If omitted, all of this spell's sounds
 *               will be played at once. String format is as passed to {@link Spell#createSoundWithSuffix(String)}.
	 */
	protected void playSound(World world, EntityLivingBase entity, int ticksInUse, int duration, SpellModifiers modifiers, String... sounds){
		if(!entity.isSilent()){
			this.playSound(world, entity.posX, entity.posY, entity.posZ, ticksInUse, duration, modifiers, sounds);
		}
	}

	/**
	 * Plays this spell's sound at the given position in the given world. This is a vector-based wrapper for
	 * {@link Spell#playSound(World, double, double, double, int, int, SpellModifiers, String...)}.
	 * <p></p>
	 * <i>If you are overriding the {@code Spell.playSound} methods, it is recommended that you override the xyz version
	 * instead of this one, since this method calls that one anyway.</i>
	 * @param world The world to play the sound in.
	 * @param pos A vector representing the position to play the sound at.
	 * @param ticksInUse The number of ticks this spell has already been cast for, passed in from the {@code cast(...)}
	 * methods. Not used in the base method, but included for use by subclasses overriding this method.
	 * @param duration The number of ticks this spell will be cast for, passed in from the {@code cast(...)}
	 * methods. Not used in the base method, but included for use by subclasses overriding this method.
	 * @param modifiers The modifiers this spell was cast with, passed in from the {@code cast(...)} methods.
	 */
	protected void playSound(World world, Vec3d pos, int ticksInUse, int duration, SpellModifiers modifiers, String... sounds){
		this.playSound(world, pos.x, pos.y, pos.z, ticksInUse, duration, modifiers, sounds);
	}

	/**
	 * Plays this spell's sounds at the given position in the given world. This is not called automatically; subclasses
	 * should call it at the appropriate point(s) in the cast methods. By default, it checks whether each sound is null
	 * before playing, so callers shouldn't have to.
	 * <p></p>
	 * Usually, this method will be called by the general spell classes; this is clearly stated in those classes. When
	 * extending such a class, it is also possible to override this method to add extra sounds or change the sound
	 * behaviour entirely (for example, playing a continuous spell sound).
	 * @param world The world to play the sound in.
	 * @param x The x position to play the sound at.
	 * @param y The y position to play the sound at.
	 * @param z The z position to play the sound at.
	 * @param ticksInUse The number of ticks this spell has already been cast for, passed in from the {@code cast(...)}
	 * methods. Not used in the base method, but included for use by subclasses overriding this method.
	 * @param duration The number of ticks this spell will be cast for, passed in from the {@code cast(...)}
	 * methods. Not used in the base method, but included for use by subclasses overriding this method.
	 * @param modifiers The modifiers this spell was cast with, passed in from the {@code cast(...)} methods.
	 */
	protected void playSound(World world, double x, double y, double z, int ticksInUse, int duration, SpellModifiers modifiers, String... sounds){

		List<String> identifiers = Arrays.stream(sounds).map(s -> "spell." + this.getRegistryName().getPath() + "." + s)
				.collect(Collectors.toList());

		if(this.sounds != null){
			for(SoundEvent sound : this.sounds){
				ResourceLocation soundName = SoundEvent.REGISTRY.getNameForObject(sound);
				if(soundName != null && (identifiers.size() == 0 || identifiers.contains(soundName.getPath()))){
					world.playSound(null, x, y, z, sound, WizardrySounds.SPELLS, volume, pitch + pitchVariation * (world.rand.nextFloat() - 0.5f));
				}
			}
		}
	}

	/** Helper method which plays a standard continuous spell sound loop on the first casting tick, which moves
	 * with the given entity. */
	protected final void playSoundLoop(World world, EntityLivingBase entity, int ticksInUse){
		if(ticksInUse == 0 && world.isRemote) Wizardry.proxy.playSpellSoundLoop(entity, this, this.sounds,
				WizardrySounds.SPELLS, volume, pitch + pitchVariation * (world.rand.nextFloat() - 0.5f));
	}

	/** Helper method which plays a standard continuous spell sound loop on the first casting tick, at the given
	 * coordinates. If the given duration is -1, the coordinates must be those of a dispenser. */
	protected final void playSoundLoop(World world, double x, double y, double z, int ticksInUse, int duration){
		if(ticksInUse == 0 && world.isRemote){
			Wizardry.proxy.playSpellSoundLoop(world, x, y, z, this, this.sounds,
					WizardrySounds.SPELLS, volume, pitch + pitchVariation * (world.rand.nextFloat() - 0.5f), duration);
		}
	}

	// Spells are sorted according to tier and element. Where several spells have the same tier and element,
	// they will remain in the order they were registered.
	@Override
	public final int compareTo(Spell spell){

		if(this.getTier().ordinal() > spell.getTier().ordinal()){
			return 1;
		}else if(this.getTier().ordinal() < spell.getTier().ordinal()){
			return -1;
		}else{
			return Integer.compare(this.getElement().ordinal(), spell.getElement().ordinal());
		}
	}

	// ============================================ Static methods ==============================================

	/**
	 * Returns the total number of registered spells, excluding the 'None' spell. Returns the same number that would be
	 * returned by {@code Spell.getSpells(Spell.allSpells).size()}, but this method is more efficient.
	 */
	public static int getTotalSpellCount(){
		return registry.getValuesCollection().size() - 1;
	}

	/**
	 * Gets a spell instance from its integer metadata, which corresponds to its ID in the spell registry. If the given
	 * metadata has no spell assigned then the {@link None} spell will be returned.
	 */
	public static Spell byMetadata(int metadata){
		Spell spell = ((ForgeRegistry<Spell>)registry).getValue(metadata);
		return spell == null ? Spells.none : spell;
	}

	/** Gets a spell instance from its network ID. Or the {@link None} spell if no such spell exists. */
	public static Spell byNetworkID(int id){
		if(id < 0 || id >= registry.getValuesCollection().size()){
			return Spells.none;
		}
		return registry.getValuesCollection().stream().filter(s -> s.id == id).findAny().orElse(Spells.none);
	}

	/**
	 * Returns the spell with the given registry name, or null if no such spell exists. This is really only intended
	 * for cases where the user has input a name (currently commands and loot functions) and may have omitted the mod
	 * ID for spells in the base mod. Otherwise, use {@code Spell.registry.getValue(ResourceLocation)}.
	 *
	 * @param name The registry name of the spell, in the form [mod id]:[spell name]. If no mod id is specified, it
	 *        defaults to {@link Wizardry#MODID}.
	 */
	public static Spell get(String name){
		ResourceLocation key = new ResourceLocation(name);
		if(key.getNamespace().equals("minecraft")) key = new ResourceLocation(Wizardry.MODID, name);
		return registry.getValue(key);
	}

	/** Returns a list of all registered spells' registry names, excluding the 'none' spell. Used in commands. */
	public static Collection<ResourceLocation> getSpellNames(){
		// Maybe it would be better to store all of this statically?
		Set<ResourceLocation> keys = new HashSet<ResourceLocation>(registry.getKeys());
		keys.remove(registry.getKey(Spells.none));
		return keys;
	}

	/**
	 * Returns a list containing all spells matching the given {@link Predicate}. The returned list is separate from the
	 * internal spells list; any changes you make to the returned list will have no effect on wizardry since the
	 * returned list is local to this method. Never includes the {@link None} spell. For convenience, there are some
	 * predefined predicates in the Spell class (some of these really aren't shortcuts any more):
	 * <p></p>
	 * {@link Spell#allSpells} will allow all spells to be returned<br>
	 * {@link Spell#npcSpells} will only allow enabled spells that can be cast by NPCs (see
	 * {@link Spell#canBeCastByNPCs()})<br>
	 * {@link Spell#nonContinuousSpells} will filter out continuous spells but not disabled spells<br>
	 * {@link TierElementFilter} will only allow enabled spells of the specified tier and element
	 *
	 * @param filter A <code>Predicate&ltSpell&gt</code> that the returned spells must satisfy.
	 *
	 * @return A <b>local, modifiable</b> list of spells matching the given predicate. <i>Note that this list may be
	 *         empty.</i>
	 */
	public static List<Spell> getSpells(Predicate<Spell> filter){
		return registry.getValuesCollection().stream().filter(filter.and(s -> s != Spells.none)).collect(Collectors.toList());
	}

	/** Predicate which allows all spells. */
	public static Predicate<Spell> allSpells = s -> true;

	/** Predicate which allows all non-continuous spells, even those that have been disabled. */
	public static Predicate<Spell> nonContinuousSpells = s -> !s.isContinuous;

	/** Predicate which allows all enabled spells for which {@link Spell#canBeCastByNPCs()} returns true. */
	public static Predicate<Spell> npcSpells = s -> s.isEnabled(SpellProperties.Context.NPCS) && s.canBeCastByNPCs();

	/**
	 * Predicate which allows all enabled spells of the given tier and element (create an instance of this class each
	 * time you want to use it). This is somewhat useless now that Wizardry uses Java 8, but it is more readable than a
	 * lambda expression and you don't need to remember to check that the spell is enabled every time.
	 */
	public static class TierElementFilter implements Predicate<Spell> {

		private Tier tier;
		private Element element;
		private SpellProperties.Context[] contexts;

		/**
		 * Creates a new TierElementFilter that checks for the given tier and element. Does not allow spells that have
		 * been disabled in the config or in their JSON files.
		 *
		 * @param tier The EnumTier to check for. Pass in null to allow all tiers.
		 * @param element The EnumElement to check for. Pass in null to allow all elements.
		 * @param contexts The {@link electroblob.wizardry.util.SpellProperties.Context Context}s in which to check
		 *                 for enabled spells. The spell must be enabled in at least one of these contexts to pass
		 *                 the filter. If omitted, defaults to all contexts i.e. only completely disabled spells are
		 *                 filtered out.
		 */
		public TierElementFilter(Tier tier, Element element, SpellProperties.Context... contexts){
			this.tier = tier;
			this.element = element;
			this.contexts = contexts;
		}

		@Override
		public boolean test(Spell spell){
			return spell.isEnabled(contexts) && (this.tier == null || spell.getTier() == this.tier)
					&& (this.element == null || spell.getElement() == this.element);
		}
	}

	// ============================================ Event handlers ==============================================

	// Not ideal but it solves the reloading of spell properties without breaking encapsulation

	@SubscribeEvent
	public static void onWorldLoadEvent(WorldEvent.Load event){
		if(!event.getWorld().isRemote){
			if(event.getWorld().provider.getDimension() != 0) return; // Only do it once per save file
			clearProperties();
			SpellProperties.loadWorldSpecificSpellProperties(event.getWorld());
			for(Spell spell : Spell.registry){
				if(!spell.arePropertiesInitialised()) spell.setProperties(spell.globalProperties);
			}
		}
	}

	@SubscribeEvent
	public static void onClientDisconnectEvent(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){
		// Why does the world UNLOAD event happen during world LOADING? How does that even work?!
		clearProperties();
		for(Spell spell : Spell.registry){
			// If someone wants to access them from the menu, they'll get the global ones (not sure why you'd want to)
			// No need to sync here since the server is about to shut down anyway
			spell.setProperties(spell.globalProperties);
		}
	}

}
