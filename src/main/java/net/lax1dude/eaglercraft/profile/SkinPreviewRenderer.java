package net.lax1dude.eaglercraft.profile;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.EaglerMeshLoader;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.ZombieModel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.resources.ResourceLocation;
import net.lax1dude.eaglercraft.compat.EaglerOptions;

/**
 * Copyright (c) 2022-2023 lax1dude, ayunami2000. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class SkinPreviewRenderer {

	private static PlayerModel playerModelSteve = null;
	private static PlayerModel playerModelAlex = null;
	private static ZombieModel playerModelZombie = null;
	
	public static void initialize() {
		// 1.12.2 built models from a scale and a "slim" flag. 1.18.2 bakes them from the
		// entity model set against a named layer, so the geometry comes from the same
		// definitions the real player renderer uses.
		net.minecraft.client.model.geom.EntityModelSet models = Minecraft.getInstance().getEntityModels();
		playerModelSteve = new PlayerModel<>(models.bakeLayer(
				net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
		playerModelSteve.young = false;
		playerModelAlex = new PlayerModel<>(models.bakeLayer(
				net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
		playerModelAlex.young = false;
		playerModelZombie = new ZombieModel<>(models.bakeLayer(
				net.minecraft.client.model.geom.ModelLayers.ZOMBIE));
		playerModelZombie.young = false;
	}

	public static void renderPreview(int x, int y, int mx, int my, SkinModel skinModel) {
		renderPreview(x, y, mx, my, false, skinModel, null, null);
	}

	public static void renderPreview(int x, int y, int mx, int my, boolean capeMode, SkinModel skinModel, ResourceLocation skinTexture, ResourceLocation capeTexture) {
		HumanoidModel model;
		switch(skinModel) {
		case STEVE:
		default:
			model = playerModelSteve;
			break;
		case ALEX:
			model = playerModelAlex;
			break;
		case ZOMBIE:
			model = playerModelZombie;
			break;
		case LONG_ARMS:
		case WEIRD_CLIMBER_DUDE:
		case LAXATIVE_DUDE:
		case BABY_CHARLES:
		case BABY_WINSTON:
		case BACON_HAIR:
		case AMONG_US:
			if(skinModel.highPoly != null && (!skinModel.highPoly.requiresFNAWSetting()
					|| EaglerOptions.enableFNAWSkins)) {
				renderHighPoly(x, y, mx, my, skinModel.highPoly);
				return;
			}
			model = playerModelSteve;
			break;
		}
		
		/*
		 * The transform has to be built on 1.18.2's matrices, not EaglercraftX's.
		 *
		 * 1.12.2 posed this preview with GlStateManager.translate/scale/rotate, because the
		 * whole client drew through that one fixed-function matrix stack. In 1.18.2 a vanilla
		 * model is drawn by handing a PoseStack to renderToBuffer, and lax1dude's GL layer has
		 * its own separate stack that vanilla's shaders never read. Leaving the old calls in
		 * place therefore did nothing at all: the transform was applied to a matrix nobody
		 * consulted, and the model was drawn wherever the GUI's own pose happened to put it.
		 *
		 * The sequence below is vanilla's InventoryScreen.renderEntityInInventory, which is the
		 * canonical way to put a living model in a 1.18.2 GUI, with lax1dude's placement and
		 * mouse-tracking numbers kept. It uses two stacks the way vanilla does: the RenderSystem
		 * model-view stack takes the screen-space placement (and must be followed by
		 * applyModelViewMatrix, or the change never reaches the shader's uniform), while a fresh
		 * local PoseStack carries the model's own rotation and scale.
		 *
		 * The last two lines of the transform are the ones vanilla's LivingEntityRenderer would
		 * have applied on the way in. This draws the model directly rather than through an
		 * entity renderer - there is no entity here, only a mesh and a texture - so they have to
		 * be applied by hand or the model comes out inverted and standing a block and a half too
		 * high.
		 *
		 * The high-poly branch above deliberately keeps the old GlStateManager calls: it draws
		 * through EaglercraftGPU.drawHighPoly, which is lax1dude's own fixed-function pipeline
		 * and does read that stack.
		 */
		float pitch;
		if(capeMode) {
			// The cape view turns the model most of the way round and tracks more gently.
			mx = x - (x - mx) - 20;
			pitch = (y - my) * -0.02f;
		}else {
			pitch = (y - my) * -0.06f;
		}
		float yaw = (x - mx) * 0.06f + (capeMode ? 140.0f : 0.0f);

		PoseStack view = RenderSystem.getModelViewStack();
		view.pushPose();
		view.translate(x, y, 1050.0);
		view.scale(1.0f, 1.0f, -1.0f);
		RenderSystem.applyModelViewMatrix();

		PoseStack pose = new PoseStack();
		pose.translate(0.0, 0.0, 1000.0);
		pose.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
		pose.mulPose(Vector3f.ZP.rotationDegrees(180.0f));
		pose.mulPose(Vector3f.XP.rotationDegrees(pitch));
		pose.mulPose(Vector3f.YP.rotationDegrees(yaw));
		pose.scale(-1.0f, -1.0f, 1.0f);
		pose.translate(0.0, -1.501, 0.0);

		Lighting.setupForEntityInInventory();

		// 1.12.2's render(entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch,
		// scale) both posed and drew the model. 1.18.2 splits those: setupAnim poses it and
		// renderToBuffer draws it through a VertexConsumer.
		float ageInTicks = (float) (EagRuntime.steadyTimeMillis() % 2000000) / 50f;
		poseIdle(model, ageInTicks, ((x - mx) * 0.06f), ((y - my) * -0.1f));
		renderModelPart(pose, model, skinTexture);

		if(capeTexture != null && model instanceof PlayerModel) {
			pose.pushPose();
			pose.translate(0.0, 0.0, 0.125);
			pose.mulPose(Vector3f.XP.rotationDegrees(6.0f));
			pose.mulPose(Vector3f.YP.rotationDegrees(180.0f));
			// PlayerModel.renderCloak draws the cape part on its own, which is what the cape
			// texture belongs to - re-drawing the whole model with it would paint the cape
			// image over the body as well.
			renderCloak(pose, (PlayerModel<?>) model, capeTexture);
			pose.popPose();
		}

		view.popPose();
		RenderSystem.applyModelViewMatrix();
		Lighting.setupFor3DItems();
	}

	/** The scale lax1dude's preview box was laid out for. */
	private static final float PREVIEW_SCALE = 50.0f;

	private static void renderHighPoly(int x, int y, int mx, int my, HighPolySkin msh) {
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.disableCull();
		GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
		
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y - 80.0f, 100.0f);
		GlStateManager.scale(50.0f, 50.0f, 50.0f);
		GlStateManager.rotate(180.0f, 1.0f, 0.0f, 0.0f);
		GlStateManager.scale(1.0f, -1.0f, 1.0f);
		
		RenderHelper.enableGUIStandardItemLighting();
		
		// The whole body used to swing round to face the cursor. For a normal humanoid
		// that reads as broken, so the body is left alone and only the head follows.
		boolean headOnlyTracking = (msh == HighPolySkin.BACON_HAIR);
		float mousePitch = (y - my) * -0.06f;
		float mouseYaw = (x - mx) * 0.06f;
		GlStateManager.translate(0.0f, 1.0f, 0.0f);
		if(!headOnlyTracking) {
			GlStateManager.rotate(mousePitch, 1.0f, 0.0f, 0.0f);
			GlStateManager.rotate(mouseYaw, 0.0f, 1.0f, 0.0f);
		}
		GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);
		GlStateManager.translate(0.0f, -0.6f, 0.0f);
		
		GlStateManager.scale(HighPolySkin.highPolyScale, HighPolySkin.highPolyScale, HighPolySkin.highPolyScale);
		Minecraft.getInstance().getTextureManager().bindForSetup(msh.texture);
		
		if(msh.bodyModel != null) {
			EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(msh.bodyModel));
		}
		
		if(msh.headModel != null) {
			if(headOnlyTracking) {
				float[] neck = HighPolySkin.BACON_HAIR_NECK;
				GlStateManager.pushMatrix();
				GlStateManager.translate(neck[0], neck[1], neck[2]);
				GlStateManager.rotate(-mouseYaw, 0.0f, 1.0f, 0.0f);
				GlStateManager.rotate(-mousePitch, 1.0f, 0.0f, 0.0f);
				GlStateManager.translate(-neck[0], -neck[1], -neck[2]);
				EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(msh.headModel));
				GlStateManager.popMatrix();
			}else {
				EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(msh.headModel));
			}
		}
		
		if(msh.limbsModel != null && msh.limbsModel.length > 0) {
			for(int i = 0; i < msh.limbsModel.length; ++i) {
				float offset = 0.0f;
				if(msh.limbsOffset != null) {
					if(msh.limbsOffset.length == 1) {
						offset = msh.limbsOffset[0];
					}else {
						offset = msh.limbsOffset[i];
					}
				}
				if(offset != 0.0f || msh.limbsInitialRotation != 0.0f) {
					GlStateManager.pushMatrix();
					if(offset != 0.0f) {
						GlStateManager.translate(0.0f, offset, 0.0f);
					}
					if(msh.limbsInitialRotation != 0.0f) {
						GlStateManager.rotate(msh.limbsInitialRotation, 1.0f, 0.0f, 0.0f);
					}
				}
				
				EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(msh.limbsModel[i]));
				
				if(offset != 0.0f || msh.limbsInitialRotation != 0.0f) {
					GlStateManager.popMatrix();
				}
			}
		}

		GlStateManager.popMatrix();
		GlStateManager.disableLighting();
	}


	/**
	 * Poses the model the way {@code setupAnim} would for a player standing still, without an
	 * entity to read it from.
	 *
	 * <p>1.12.2's preview called {@code ModelBiped.setRotationAngles(null, ...)} and got away
	 * with it. 1.18.2's {@code HumanoidModel.setupAnim} cannot be called that way at all: its
	 * very first statement is {@code var1.getFallFlyingTicks()}, and it goes on to ask the
	 * entity whether it is swimming, what its velocity is, which arm is its main one, and what
	 * it is holding - eight dereferences before it finishes. Passing null crashed the Edit
	 * Profile screen the moment it drew, with
	 * {@code TypeError: Cannot read properties of null}.
	 *
	 * <p>There is no entity to hand it either. A real {@code LivingEntity} needs a {@code Level},
	 * and the main menu has none - building a client level to animate a 60-pixel-tall preview
	 * would be a far bigger lie than this. So the answer is to write down the pose that
	 * {@code setupAnim} produces for the case the preview actually shows: standing still, empty
	 * handed, not crouching, riding, swimming or gliding, with {@code limbSwing} and
	 * {@code limbSwingAmount} both zero, which is what the preview passed anyway.
	 *
	 * <p>Under those conditions almost everything {@code setupAnim} computes collapses to a
	 * constant - every limb rotation is {@code cos(...) * 2 * 0 * 0.5}, which is zero - so what
	 * survives is the rest positions, the head angles, and the idle arm bob. The bob is kept
	 * because it is the one part of the animation the preview visibly had, and
	 * {@code AnimationUtils.bobModelPart} needs no entity. {@code setupAttackAnimation} is left
	 * out because it returns immediately while {@code attackTime} is 0, and it never leaves 0
	 * here.
	 *
	 * <p>The two copy blocks at the end are not decoration: {@code hat.copyFrom(head)} is the
	 * last line of {@code setupAnim}, and the {@code PlayerModel} copies are the whole of its
	 * override. Without them the outer skin layer - hat, jacket, sleeves, trouser legs - would
	 * stay at the origin while the base model moved, which is precisely the second skin layer
	 * that HD skins and capes are about.
	 */
	private static void poseIdle(HumanoidModel<?> model, float ageInTicks, float yawDeg, float pitchDeg) {
		final float toRadians = (float) (Math.PI / 180.0);

		model.head.y = 0.0f;
		model.head.yRot = yawDeg * toRadians;
		model.head.xRot = pitchDeg * toRadians;

		model.body.y = 0.0f;
		model.body.xRot = 0.0f;
		model.body.yRot = 0.0f;

		model.rightArm.x = -5.0f;
		model.rightArm.y = 2.0f;
		model.rightArm.z = 0.0f;
		model.rightArm.xRot = 0.0f;
		model.rightArm.yRot = 0.0f;
		model.rightArm.zRot = 0.0f;

		model.leftArm.x = 5.0f;
		model.leftArm.y = 2.0f;
		model.leftArm.z = 0.0f;
		model.leftArm.xRot = 0.0f;
		model.leftArm.yRot = 0.0f;
		model.leftArm.zRot = 0.0f;

		model.rightLeg.y = 12.0f;
		model.rightLeg.z = 0.1f;
		model.rightLeg.xRot = 0.0f;
		model.rightLeg.yRot = 0.0f;
		model.rightLeg.zRot = 0.0f;

		model.leftLeg.y = 12.0f;
		model.leftLeg.z = 0.1f;
		model.leftLeg.xRot = 0.0f;
		model.leftLeg.yRot = 0.0f;
		model.leftLeg.zRot = 0.0f;

		AnimationUtils.bobModelPart(model.rightArm, ageInTicks, 1.0f);
		AnimationUtils.bobModelPart(model.leftArm, ageInTicks, -1.0f);

		model.hat.copyFrom(model.head);

		if (model instanceof PlayerModel) {
			PlayerModel<?> player = (PlayerModel<?>) model;
			player.jacket.copyFrom(model.body);
			player.leftSleeve.copyFrom(model.leftArm);
			player.rightSleeve.copyFrom(model.rightArm);
			player.leftPants.copyFrom(model.leftLeg);
			player.rightPants.copyFrom(model.rightLeg);
		}
	}

	/**
	 * Draws an already-posed model through 1.18.2's buffer source.
	 *
	 * <p>The preview draws immediately rather than inside an entity render pass, so the batch
	 * is flushed here rather than left for the level renderer to end.
	 *
	 * <p>The PoseStack is passed in rather than read from the compat layer's per-frame slot.
	 * That slot holds the pose the GUI is drawing flat widgets with; the model needs the
	 * entity-space one built in renderPreview, and using the GUI's was why the preview drew in
	 * the wrong place.
	 */
	private static void renderModelPart(PoseStack pose, Model model, ResourceLocation texture) {
		if (pose == null || texture == null) {
			return;
		}
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
		model.renderToBuffer(pose, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				1.0F, 1.0F, 1.0F, 1.0F);
		buffers.endBatch();
	}

	/** The cape part only - see the call site for why the whole model must not be re-drawn. */
	private static void renderCloak(PoseStack pose, PlayerModel<?> model, ResourceLocation texture) {
		if (pose == null || texture == null) {
			return;
		}
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(texture));
		model.renderCloak(pose, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		buffers.endBatch();
	}

	/** A GUI preview is lit by Lighting.setupForEntityInInventory, not by the level. */
	private static final int FULL_BRIGHT = LightTexture.pack(15, 15);
}
