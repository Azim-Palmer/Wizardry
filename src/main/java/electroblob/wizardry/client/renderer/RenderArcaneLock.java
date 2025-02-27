package electroblob.wizardry.client.renderer;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.spell.ArcaneLock;
import electroblob.wizardry.util.WizardryUtilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(Side.CLIENT)
public class RenderArcaneLock {

	private static final ResourceLocation[] textures = new ResourceLocation[8];

	static {
		for(int i=0; i<textures.length; i++){
			textures[i] = new ResourceLocation(Wizardry.MODID, "textures/entity/arcane_lock_" + i + ".png");
		}
	}

	@SubscribeEvent
	public static void onRenderWorldLastEvent(RenderWorldLastEvent event){

		EntityPlayer player = Minecraft.getMinecraft().player;
		World world = Minecraft.getMinecraft().world;

		GlStateManager.pushMatrix();
		GlStateManager.enableBlend();
		GlStateManager.disableLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		Vec3d origin = player.getPositionEyes(event.getPartialTicks());
		GlStateManager.translate(-origin.x, -origin.y + player.getEyeHeight(), -origin.z);

		GlStateManager.color(1, 1, 1, 1);

		Minecraft.getMinecraft().renderEngine.bindTexture(textures[(player.ticksExisted % (textures.length * 2))/2]);

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

		// Someone managed to get a CME here so let's just copy the list to be safe
		// It's only cosmetic so if a tileentity somehow gets removed while we're rendering them it's not a big deal
		List<TileEntity> tileentities = new ArrayList<>(world.loadedTileEntityList);

		for(TileEntity tileentity : tileentities){

			if(tileentity.getDistanceSq(origin.x, origin.y, origin.z) <= tileentity.getMaxRenderDistanceSquared()
					&& tileentity.getTileData().hasUniqueId(ArcaneLock.NBT_KEY)){

				Vec3d[] vertices = WizardryUtilities.getVertices(world.getBlockState(tileentity.getPos()).getBoundingBox(world, tileentity.getPos()).grow(0.05).offset(tileentity.getPos()));

				drawFace(buffer, vertices[0], vertices[1], vertices[3], vertices[2], 0, 0, 1, 1); // Bottom
				drawFace(buffer, vertices[6], vertices[7], vertices[2], vertices[3], 0, 0, 1, 1); // South
				drawFace(buffer, vertices[5], vertices[6], vertices[1], vertices[2], 0, 0, 1, 1); // East
				drawFace(buffer, vertices[4], vertices[5], vertices[0], vertices[1], 0, 0, 1, 1); // North
				drawFace(buffer, vertices[7], vertices[4], vertices[3], vertices[0], 0, 0, 1, 1); // West
				drawFace(buffer, vertices[5], vertices[4], vertices[6], vertices[7], 0, 0, 1, 1); // Top

			}
		}

		tessellator.draw();

		GlStateManager.disableBlend();
		GlStateManager.enableTexture2D();
		GlStateManager.enableLighting();
		GlStateManager.disableRescaleNormal();
		GlStateManager.popMatrix();
	}

	private static void drawFace(BufferBuilder buffer, Vec3d topLeft, Vec3d topRight, Vec3d bottomLeft, Vec3d bottomRight, float u1, float v1, float u2, float v2){
		buffer.pos(topLeft.x, topLeft.y, topLeft.z).tex(u1, v1).endVertex();
		buffer.pos(topRight.x, topRight.y, topRight.z).tex(u2, v1).endVertex();
		buffer.pos(bottomRight.x, bottomRight.y, bottomRight.z).tex(u2, v2).endVertex();
		buffer.pos(bottomLeft.x, bottomLeft.y, bottomLeft.z).tex(u1, v2).endVertex();
	}

}
