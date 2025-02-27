package electroblob.wizardry.item;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.data.SpellGlyphData;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.event.SpellCastEvent.Source;
import electroblob.wizardry.packet.PacketCastSpell;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.registry.WizardryTabs;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;

public class ItemScroll extends Item implements ISpellCastingItem {
	
	/** The maximum number of ticks a continuous spell scroll can be cast for (by holding the use item button). */
	public static final int CASTING_TIME = 120;

	public ItemScroll(){
		super();
		setHasSubtypes(true);
		setMaxStackSize(16);
		setCreativeTab(WizardryTabs.SPELLS);
	}
	
	@Override
	public Spell getCurrentSpell(ItemStack stack){
		return Spell.byMetadata(stack.getItemDamage());
	}

	@Override
	public boolean showSpellHUD(EntityPlayer player, ItemStack stack){
		return false;
	}

	@Override
	public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> list){

		if(tab == WizardryTabs.SPELLS){

			List<Spell> spells = Spell.getSpells(Spell.allSpells);
			spells.removeIf(s -> !s.applicableForItem(this));

			for(Spell spell : spells){
				list.add(new ItemStack(this, 1, spell.metadata()));
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack){
		return true;
	}

	@Override
	// Item's version of this method is, quite frankly, an abomination. Why is a deprecated method being used as such
	// an integral part of the code? And what's the point in getUnlocalisedNameInefficiently?
	public String getItemStackDisplayName(ItemStack stack){

		/* Ok, so this method can be called from either the client or the server side. Obviously, on the client the
		 * spell name is either translated or obfuscated, then it is put into the item name as part of that translation.
		 * On the server side, however, there's a problem: on the one hand, the spell name shouldn't be obfuscated in
		 * case the server wants to do something with it, and in that case returning world-specific gobbledegook is not
		 * particularly helpful. On the other hand, something might happen that causes this method to be called on the
		 * server side, but the result to then be sent to the client, which means broken discovery system. Simply put, I
		 * can't predict that, and it's not my job to cater for other people's incorrect usage of code, especially when
		 * that might compromise some perfectly reasonable use (think Bibliocraft's 'best guess' book detection). */
		return Wizardry.proxy.getScrollDisplayName(stack);

	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack itemstack, World world, List<String> tooltip, net.minecraft.client.util.ITooltipFlag advanced){

		if(world != null){

			Spell spell = Spell.byMetadata(itemstack.getItemDamage());

			boolean discovered = Wizardry.proxy.shouldDisplayDiscovered(spell, itemstack);

			// Advanced tooltips display more information, mainly for searching purposes in creative
			if(discovered && advanced.isAdvanced()){ // No cheating!
				tooltip.add(spell.getTier().getDisplayName());
				tooltip.add(spell.getElement().getDisplayName());
				tooltip.add(spell.getType().getDisplayName());
			}
		}
	}
	
	@Override
	public int getMaxItemUseDuration(ItemStack stack){
		return CASTING_TIME;
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand){

		ItemStack stack = player.getHeldItem(hand);

		Spell spell = Spell.byMetadata(stack.getItemDamage());
		// By default, scrolls have no modifiers - but with the event system, they could be added.
		SpellModifiers modifiers = new SpellModifiers();

		if(canCast(stack, spell, player, hand, 0, modifiers)){
			// Now we can cast continuous spells with scrolls!
			if(spell.isContinuous){
				if(!player.isHandActive()){
					player.setActiveHand(hand);
					return new ActionResult<>(EnumActionResult.SUCCESS, stack);
				}
			}else{
				if(cast(stack, spell, player, hand, 0, modifiers)){
					return new ActionResult<>(EnumActionResult.SUCCESS, stack);
				}
			}
		}

		return new ActionResult<>(EnumActionResult.FAIL, stack);
	}
	
	// For continuous spells. The count argument actually decrements by 1 each tick.
	@Override
	public void onUsingTick(ItemStack stack, EntityLivingBase user, int count){

		if(user instanceof EntityPlayer){

			EntityPlayer player = (EntityPlayer)user;

			Spell spell = Spell.byMetadata(stack.getItemDamage());
			// By default, scrolls have no modifiers - but with the event system, they could be added.
			SpellModifiers modifiers = new SpellModifiers();
			int castingTick = stack.getMaxItemUseDuration() - count;

			// Continuous spells (these must check if they can be cast each tick since the mana changes)
			// In theory the spell is always continuous here but just in case it isn't...
			if(spell.isContinuous && canCast(stack, spell, player, player.getActiveHand(), castingTick, modifiers)){
				cast(stack, spell, player, player.getActiveHand(), castingTick, modifiers);
			}else{
				// Scrolls normally work on the max use duration so this isn't ever reached by wizardry, but if the
				// casting was interrupted by SpellCastEvent.Tick it will be used
				player.stopActiveHand();
			}
		}
	}

	@Override
	public boolean canCast(ItemStack stack, Spell spell, EntityPlayer caster, EnumHand hand, int castingTick, SpellModifiers modifiers){
		// Even neater!
		if(castingTick == 0){
			return !MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Pre(Source.SCROLL, spell, caster, modifiers));
		}else{
			return !MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Tick(Source.SCROLL, spell, caster, modifiers, castingTick));
		}
	}

	@Override
	public boolean cast(ItemStack stack, Spell spell, EntityPlayer caster, EnumHand hand, int castingTick, SpellModifiers modifiers){

		World world = caster.world;

		if(world.isRemote && !spell.isContinuous && spell.requiresPacket()) return false;

		if(spell.cast(world, caster, hand, castingTick, modifiers)){

			if(castingTick == 0) MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Post(Source.SCROLL, spell, caster, modifiers));

			if(!world.isRemote){

				// Continuous spells never require packets so don't rely on the requiresPacket method to specify it
				if(!spell.isContinuous && spell.requiresPacket()){
					// Sends a packet to all players in dimension to tell them to spawn particles.
					IMessage msg = new PacketCastSpell.Message(caster.getEntityId(), hand, spell, modifiers);
					WizardryPacketHandler.net.sendToDimension(msg, world.provider.getDimension());
				}

				// Scrolls are consumed upon successful use in survival mode
				if(!spell.isContinuous && !caster.isCreative()) stack.shrink(1);
			}

			return true;
		}

		return false;
	}
	
	@Override
	public void onPlayerStoppedUsing(ItemStack stack, World world, EntityLivingBase user, int timeLeft){
		// Consumes a continuous spell scroll when a player in survival mode stops using it.
		if(Spell.byMetadata(stack.getItemDamage()).isContinuous
				&& (!(user instanceof EntityPlayer) || !((EntityPlayer)user).isCreative())){
			stack.shrink(1);
		}
	}
	
	@Override
	public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase user){
		// Consumes a continuous spell scroll when the casting elapses whilst in use by a player in survival mode.
		if(Spell.byMetadata(stack.getItemDamage()).isContinuous
				&& (!(user instanceof EntityPlayer) || !((EntityPlayer)user).isCreative())){
			stack.shrink(1);
		}
		
		return stack;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public net.minecraft.client.gui.FontRenderer getFontRenderer(ItemStack stack){
		return Wizardry.proxy.getFontRenderer(stack);
	}
}
