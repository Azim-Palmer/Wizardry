package electroblob.wizardry.spell;

import electroblob.wizardry.constants.Constants;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.util.AllyDesignationSystem;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.ParticleBuilder.Type;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WizardryUtilities;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

@Mod.EventBusSubscriber
public class EmpoweringPresence extends Spell {

	public EmpoweringPresence(){
		super("empowering_presence", EnumAction.BOW, false);
		addProperties(EFFECT_RADIUS, EFFECT_DURATION, EFFECT_STRENGTH);
	}

	@Override
	public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers){

		List<EntityPlayer> targets = WizardryUtilities.getEntitiesWithinRadius(getProperty(EFFECT_RADIUS).doubleValue()
				* modifiers.get(WizardryItems.blast_upgrade), caster.posX, caster.posY, caster.posZ, world, EntityPlayer.class);

		for(EntityPlayer target : targets){
			if(AllyDesignationSystem.isPlayerAlly(caster, target) || target == caster){

				int bonusAmplifier = SpellBuff.getStandardBonusAmplifier(modifiers.get(SpellModifiers.POTENCY));

				target.addPotionEffect(new PotionEffect(WizardryPotions.empowerment,
						(int)(getProperty(EFFECT_DURATION).floatValue() * modifiers.get(WizardryItems.duration_upgrade)),
						getProperty(EFFECT_STRENGTH).intValue() + bonusAmplifier));
			}
		}

		if(world.isRemote){

			for(int i = 0; i < 50 * modifiers.get(WizardryItems.blast_upgrade); i++){

				double radius = (1 + world.rand.nextDouble() * 4) * modifiers.get(WizardryItems.blast_upgrade);
				float angle = world.rand.nextFloat() * (float)Math.PI * 2;

				double x = caster.posX + radius * MathHelper.cos(angle);
				double y = caster.getEntityBoundingBox().minY;
				double z = caster.posZ + radius * MathHelper.sin(angle);

				ParticleBuilder.create(Type.SPARKLE).pos(x, y, z).vel(0, 0.03, 0).time(50).clr(0.5f, 0.4f, 0.75f).spawn(world);

			}
		}

		//WizardryUtilities.playSoundAtPlayer(caster, WizardrySounds.SPELL_CONJURATION, 1, 1 + 0.2f * world.rand.nextFloat());
		return true;
	}

	@SubscribeEvent(priority = EventPriority.LOW) // Doesn't really matter but there's no point processing it if casting is blocked
	public static void onSpellCastPreEvent(SpellCastEvent.Pre event){
		// Empowerment stacks extra potency on top of the existing potency.
		if(event.getCaster() != null && event.getCaster().isPotionActive(WizardryPotions.empowerment)){

			float potency = 1 + Constants.EMPOWERMENT_POTENCY_PER_LEVEL
					* (event.getCaster().getActivePotionEffect(WizardryPotions.empowerment).getAmplifier() + 1);

			event.getModifiers().set(SpellModifiers.POTENCY,
					event.getModifiers().get(SpellModifiers.POTENCY) * potency, true);
		}
	}
}