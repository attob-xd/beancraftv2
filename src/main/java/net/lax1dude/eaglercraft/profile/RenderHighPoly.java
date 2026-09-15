package net.lax1dude.eaglercraft.profile;

import static net.lax1dude.eaglercraft.opengl.RealOpenGLEnums.*;
import net.lax1dude.eaglercraft.compat.EaglerItemCompat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.EaglerMeshLoader;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.lax1dude.eaglercraft.opengl.OpenGlHelper;
import net.lax1dude.eaglercraft.vector.Matrix4f;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.lax1dude.eaglercraft.compat.EaglerOptions;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.util.Mth;

/**
 * Copyright (c) 2022-2024 lax1dude. All Rights Reserved.
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
public class RenderHighPoly extends PlayerRenderer {

	private static final Logger logger = LogManager.getLogger("RenderHighPoly");

	public RenderHighPoly(EntityRendererProvider.Context context, boolean slim) {
		super(context, slim);
	}

	private static final Matrix4f tmpMatrix = new Matrix4f();

	@Override
	public void render(AbstractClientPlayer entity, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		this.currentPoseStack = poseStack;
		this.currentBuffer = buffer;
		this.currentPackedLight = packedLight;
		doRender(entity, 0.0D, 0.0D, 0.0D, entityYaw, partialTicks);
	}

	/** Held for the duration of {@link #render}; the high-poly path draws immediately. */
	protected PoseStack currentPoseStack;
	protected MultiBufferSource currentBuffer;
	protected int currentPackedLight;

	public void doRender(AbstractClientPlayer abstractclientplayer, double d0, double d1, double d2, float f,
			float f1) {
		if (!abstractclientplayer.isLocalPlayer() || this.entityRenderDispatcher.camera.getEntity() == abstractclientplayer) {
			double nameY = d1;
			HighPolySkin highPolySkin = abstractclientplayer.getEaglerSkinModel().highPoly;
			
			if(highPolySkin == null) {
				doRenderSuper(abstractclientplayer, d0, d1, d2, f, f1);
				return;
			}
			// The renderer is now always installed for the eagler skin type, so the FNAW
			// toggle is honoured here per model instead of by swapping the whole renderer.
			if(highPolySkin.requiresFNAWSetting() && !EaglerOptions.enableFNAWSkins) {
				doRenderSuper(abstractclientplayer, d0, d1, d2, f, f1);
				return;
			}
			if(highPolySkin == HighPolySkin.LAXATIVE_DUDE) {
				nameY += 0.1;
			}else if(highPolySkin == HighPolySkin.BABY_WINSTON) {
				nameY -= 1.0;
			}
			
			GlStateManager.pushMatrix();
			GlStateManager.disableCull();
			
			try {
				Minecraft mc = Minecraft.getInstance();
				float f2 = Mth.rotLerp(f1, abstractclientplayer.yBodyRotO, abstractclientplayer.yBodyRot);
				float f3 = Mth.rotLerp(f1, abstractclientplayer.yHeadRotO, abstractclientplayer.yHeadRot);
				float f4 = f3 - f2;
				if (abstractclientplayer.isPassenger() && abstractclientplayer.getVehicle() instanceof LivingEntity) {
					LivingEntity entitylivingbase1 = (LivingEntity) abstractclientplayer.getVehicle();
					f2 = Mth.rotLerp(f1, entitylivingbase1.yBodyRotO, entitylivingbase1.yBodyRot);
					f4 = f3 - f2;
					float f5 = Mth.wrapDegrees(f4);
					if (f5 < -85.0F) {
						f5 = -85.0F;
					}
		
					if (f5 >= 85.0F) {
						f5 = 85.0F;
					}
		
					f2 = f3 - f5;
					if (f5 * f5 > 2500.0F) {
						f2 += f5 * 0.2F;
					}
				}

				this.renderLivingAt(abstractclientplayer, d0, d1, d2);
				float f10 = this.handleRotationFloat(abstractclientplayer, f1);
				this.rotateCorpse(abstractclientplayer, f10, f2, f1);
				GlStateManager.enableRescaleNormal();
				this.preRenderCallback(abstractclientplayer, f1);
				float f6 = 0.0625F;
				GlStateManager.scale(HighPolySkin.highPolyScale, HighPolySkin.highPolyScale, HighPolySkin.highPolyScale);
				mc.getTextureManager().bindForSetup(highPolySkin.texture);
				
				if(abstractclientplayer.isSleeping()) {
					if(highPolySkin == HighPolySkin.LAXATIVE_DUDE || highPolySkin == HighPolySkin.WEIRD_CLIMBER_DUDE) {
						GlStateManager.translate(0.0f, -3.7f, 0.0f);
					}else if(highPolySkin == HighPolySkin.BABY_WINSTON) {
						GlStateManager.translate(0.0f, -2.4f, 0.0f);
					}else {
						GlStateManager.translate(0.0f, -3.0f, 0.0f);
					}
				}
				
				float var15 = abstractclientplayer.animationSpeedOld + (abstractclientplayer.animationSpeed - abstractclientplayer.animationSpeedOld) * f1;
				float var16 = abstractclientplayer.animationPosition - abstractclientplayer.animationSpeed * (1.0F - f1);
				
				if(highPolySkin == HighPolySkin.LONG_ARMS) {
					GlStateManager.rotate(Mth.sin(var16) * 20f * var15, 0.0f, 1.0f, 0.0f);
					GlStateManager.rotate(Mth.cos(var16) * 7f * var15, 0.0f, 0.0f, 1.0f);
				}else if(highPolySkin == HighPolySkin.WEIRD_CLIMBER_DUDE) {
					GlStateManager.rotate(Mth.sin(var16) * 7f * var15, 0.0f, 1.0f, 0.0f);
					GlStateManager.rotate(Mth.cos(var16) * 3f * var15, 0.0f, 0.0f, 1.0f);
					GlStateManager.rotate(-f3, 0.0f, 1.0f, 0.0f);
					float xd = (float)(abstractclientplayer.getX() - abstractclientplayer.xo);
					GlStateManager.rotate(xd * 70.0f * var15, 0.0f, 0.0f, 1.0f);
					float zd = (float)(abstractclientplayer.getZ() - abstractclientplayer.zo);
					GlStateManager.rotate(zd * 70.0f * var15, 1.0f, 0.0f, 0.0f);
					GlStateManager.rotate(f3, 0.0f, 1.0f, 0.0f);
				}else if(highPolySkin == HighPolySkin.LAXATIVE_DUDE) {
					GlStateManager.rotate(-f3, 0.0f, 1.0f, 0.0f);
					float xd = (float)(abstractclientplayer.getX() - abstractclientplayer.xo);
					GlStateManager.rotate(-xd * 40.0f * var15, 0.0f, 0.0f, 1.0f);
					float zd = (float)(abstractclientplayer.getZ() - abstractclientplayer.zo);
					GlStateManager.rotate(-zd * 40.0f * var15, 1.0f, 0.0f, 0.0f);
					GlStateManager.rotate(f3, 0.0f, 1.0f, 0.0f);
				}else if(highPolySkin == HighPolySkin.BABY_WINSTON) {
					GlStateManager.translate(0.0f, (Mth.cos(f10 % 100000.0f) + 1.0f) * var15 * 0.2f, 0.0f);
					GlStateManager.rotate(Mth.sin(var16) * 5f * var15, 0.0f, 1.0f, 0.0f);
					GlStateManager.rotate(Mth.cos(var16) * 5f * var15, 0.0f, 0.0f, 1.0f);
				}
				
				if (abstractclientplayer.hurtTime > 0 || abstractclientplayer.deathTime > 0) {
					GlStateManager.color(1.2f, 0.8F, 0.8F, 1.0F);
				}
				
				if(highPolySkin.bodyModel != null) {
					EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(highPolySkin.bodyModel));
				}
				float jumpFactor = 0.0f;
				
				if(highPolySkin.headModel != null) {
					if(highPolySkin == HighPolySkin.BABY_CHARLES) {
						long millis = EagRuntime.steadyTimeMillis();
						float partialTicks = (float) ((millis - abstractclientplayer.eaglerHighPolyAnimationTick) * 0.02);
						//long l50 = millis / 50l * 50l;
						//boolean runTick = par1EntityPlayer.eaglerHighPolyAnimationTick < l50 && millis >= l50;
						abstractclientplayer.eaglerHighPolyAnimationTick = millis;
						
						if(partialTicks < 0.0f) {
							partialTicks = 0.0f;
						}
						if(partialTicks > 1.0f) {
							partialTicks = 1.0f;
						}
						
						float jumpFac = (float)(abstractclientplayer.getY() - abstractclientplayer.yo);
						if(jumpFac < 0.0f && !abstractclientplayer.verticalCollision) {
							jumpFac = -jumpFac;
							jumpFac *= 0.1f;
						}
						jumpFac -= 0.05f;
						if(jumpFac > 0.1f && !abstractclientplayer.verticalCollision) {
							jumpFac = 0.1f;
						}else if(jumpFac < 0.0f) {
							jumpFac = 0.0f;
						}else if(jumpFac > 0.1f && abstractclientplayer.verticalCollision) {
							jumpFac = 0.1f;
						}else if(jumpFac > 0.4f) {
							jumpFac = 0.4f;
						}
						jumpFac *= 10.0f;
						
						abstractclientplayer.eaglerHighPolyAnimationFloat3 += (jumpFac / (jumpFac + 1.0f)) * 6.0f * partialTicks;
						
						if(Float.isInfinite(abstractclientplayer.eaglerHighPolyAnimationFloat3)) {
							abstractclientplayer.eaglerHighPolyAnimationFloat3 = 1.0f;
						}else if(abstractclientplayer.eaglerHighPolyAnimationFloat3 > 1.0f) {
							abstractclientplayer.eaglerHighPolyAnimationFloat3 = 1.0f;
						}else if(abstractclientplayer.eaglerHighPolyAnimationFloat3 < -1.0f) {
							abstractclientplayer.eaglerHighPolyAnimationFloat3 = -1.0f;
						}
						
						abstractclientplayer.eaglerHighPolyAnimationFloat2 += abstractclientplayer.eaglerHighPolyAnimationFloat3 * partialTicks;
		
						abstractclientplayer.eaglerHighPolyAnimationFloat5 += partialTicks;
						while(abstractclientplayer.eaglerHighPolyAnimationFloat5 > 0.05f) {
							abstractclientplayer.eaglerHighPolyAnimationFloat5 -= 0.05f;
							abstractclientplayer.eaglerHighPolyAnimationFloat3 *= 0.99f;
							abstractclientplayer.eaglerHighPolyAnimationFloat2 *= 0.9f;
						}
						
						jumpFactor = abstractclientplayer.eaglerHighPolyAnimationFloat2; //(abstractclientplayer.eaglerHighPolyAnimationFloat1 - abstractclientplayer.eaglerHighPolyAnimationFloat2) * partialTicks + abstractclientplayer.eaglerHighPolyAnimationFloat2;
						jumpFactor -= 0.12f;
						if(jumpFactor < 0.0f) {
							jumpFactor = 0.0f;
						}
						jumpFactor = jumpFactor / (jumpFactor + 2.0f);
						if(jumpFactor > 1.0f) {
							jumpFactor = 1.0f;
						}
					}
					if(jumpFactor > 0.0f) {
						GlStateManager.pushMatrix();
						GlStateManager.translate(0.0f, jumpFactor * 3.0f, 0.0f);
					}
					
					if(highPolySkin == HighPolySkin.BACON_HAIR) {
						// Track where the player is looking. f4 is the head yaw relative to the
						// body, which rotateCorpse has already applied. The FNAW heads are static,
						// so nothing upstream does this for us.
						float headPitch = abstractclientplayer.xRotO
								+ (abstractclientplayer.getXRot() - abstractclientplayer.xRotO) * f1;
						float[] neck = HighPolySkin.BACON_HAIR_NECK;
						GlStateManager.pushMatrix();
						GlStateManager.translate(neck[0], neck[1], neck[2]);
						// Negated: the mesh is baked facing the opposite way to the model space
						// these angles assume, so applying them directly looks inverted.
						GlStateManager.rotate(-f4, 0.0f, 1.0f, 0.0f);
						GlStateManager.rotate(-headPitch, 1.0f, 0.0f, 0.0f);
						GlStateManager.translate(-neck[0], -neck[1], -neck[2]);
						EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(highPolySkin.headModel));
						GlStateManager.popMatrix();
					}else {
						EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(highPolySkin.headModel));
					}
					
					if(jumpFactor > 0.0f) {
						GlStateManager.popMatrix();
					}
				}
				
				if(highPolySkin.limbsModel != null && highPolySkin.limbsModel.length > 0) {
					for(int i = 0; i < highPolySkin.limbsModel.length; ++i) {
						float offset = 0.0f;
						if(highPolySkin.limbsOffset != null) {
							if(highPolySkin.limbsOffset.length == 1) {
								offset = highPolySkin.limbsOffset[0];
							}else {
								offset = highPolySkin.limbsOffset[i];
							}
						}
						
						GlStateManager.pushMatrix();
						
						if(offset != 0.0f || highPolySkin.limbsInitialRotation != 0.0f) {
							if(offset != 0.0f) {
								GlStateManager.translate(0.0f, offset, 0.0f);
							}
							if(highPolySkin.limbsInitialRotation != 0.0f) {
								GlStateManager.rotate(highPolySkin.limbsInitialRotation, 1.0f, 0.0f, 0.0f);
							}
						}
						
						if(highPolySkin == HighPolySkin.LONG_ARMS) {
							if(abstractclientplayer.swinging) {
								float var17 = Mth.cos(-abstractclientplayer.getAttackAnim(f1) * (float)Math.PI * 2.0f - 1.2f) - 0.362f;
								var17 *= var17;
								GlStateManager.rotate(-var17 * 20.0f, 1.0f, 0.0f, 0.0f);
							}
						}else if(highPolySkin == HighPolySkin.WEIRD_CLIMBER_DUDE) {
							if(abstractclientplayer.swinging) {
								float var17 = Mth.cos(-abstractclientplayer.getAttackAnim(f1) * (float)Math.PI * 2.0f - 1.2f) - 0.362f;
								var17 *= var17;
								GlStateManager.rotate(var17 * 60.0f, 1.0f, 0.0f, 0.0f);
							}
							GlStateManager.rotate(40.0f * var15, 1.0f, 0.0f, 0.0f);
						}else if(highPolySkin == HighPolySkin.LAXATIVE_DUDE) {
							float fff = (i == 0) ? 1.0f : -1.0f;
							float swing = (Mth.cos(f10 % 100000.0f) * fff + 0.2f) * var15;
							float swing2 = (Mth.cos(f10 % 100000.0f) * fff * 0.5f + 0.0f) * var15;
							GlStateManager.rotate(swing * 25.0f, 1.0f, 0.0f, 0.0f);
							if(abstractclientplayer.swinging) {
								float var17 = Mth.cos(-abstractclientplayer.getAttackAnim(f1) * (float)Math.PI * 2.0f - 1.2f) - 0.362f;
								var17 *= var17;
								GlStateManager.rotate(-var17 * 25.0f, 1.0f, 0.0f, 0.0f);
							}
							
							// shear matrix
							tmpMatrix.setIdentity();
							tmpMatrix.m21 = swing2;
							tmpMatrix.m23 = swing2 * -0.2f;
							GlStateManager.multMatrix(tmpMatrix);
						}else if(highPolySkin == HighPolySkin.BACON_HAIR
								|| highPolySkin == HighPolySkin.AMONG_US) {
							// Plain vanilla limb animation: the exact cosine walk cycle ModelBiped
							// uses, and nothing else. The bespoke jump/fall/climb poses snapped
							// between states instead of easing, which read as jittery.
							float[] pivot = (highPolySkin == HighPolySkin.AMONG_US)
									? HighPolySkin.AMONG_US_PIVOTS[i]
									: HighPolySkin.BACON_HAIR_PIVOTS[i];
							boolean isArm = i < 2;
							// These meshes are baked facing -Z, so their -X side is their LEFT:
							// index 0 left arm, 1 right arm, 2 left leg, 3 right leg. Vanilla
							// offsets right arm and left leg by half a cycle, which lands on
							// indices 1 and 2 here.
							float phase = (i == 1 || i == 2) ? (float)Math.PI : 0.0f;
							float cycle = Mth.cos(var16 * 0.6662f + phase);
							// A crewmate's legs are stubs cut out of one solid shell, so
							// swinging them round a hip opens a gap at the cut. They step by
							// lifting instead, which is also how the character actually walks,
							// and a lift only ever hides the cut further inside the body.
							boolean liftLeg = (highPolySkin == HighPolySkin.AMONG_US && !isArm);
							if(liftLeg) {
								GlStateManager.translate(0.0f, (cycle > 0.0f ? cycle : 0.0f) * 0.35f * var15, 0.0f);
							}
							float amplitude = isArm ? 1.0f : 1.4f;
							float rotX = liftLeg ? 0.0f : (cycle * amplitude * var15 * 57.29578f);
							float rotZ = 0.0f;
							if(highPolySkin == HighPolySkin.AMONG_US && isArm) {
								// ModelBiped's idle arm motion, so the mitts drift instead of
								// hanging dead still when the player is standing. Right arm adds,
								// left arm subtracts, which is index 1 and index 0 here.
								float dir = (i == 1) ? 1.0f : -1.0f;
								rotX += dir * Mth.sin(f10 * 0.067f) * 0.05f * 57.29578f;
								rotZ += dir * (Mth.cos(f10 * 0.09f) * 0.05f + 0.05f) * 57.29578f;
							}
							if(i == 1 && abstractclientplayer.swinging) {
								// ModelBiped's swing, verbatim. The arm's arc is eased by
								// 1-(1-sp)^4; sqrt(sp) is the BODY twist curve, and using it here
								// made the arm jerk at the start and coast at the end. The second
								// term tilts the swing toward wherever the head is pointing, which
								// is what makes mining a block below you look aimed rather than
								// flailing, and the Z tilt tucks the arm across the body.
								float sp = abstractclientplayer.getAttackAnim(f1);
								float ease = 1.0f - sp;
								ease = 1.0f - ease * ease * ease * ease;
								float arc = Mth.sin(ease * (float)Math.PI);
								float pitchRad = (abstractclientplayer.xRotO
										+ (abstractclientplayer.getXRot()
												- abstractclientplayer.xRotO) * f1) * 0.017453292f;
								float aim = Mth.sin(sp * (float)Math.PI) * -(pitchRad - 0.7f) * 0.75f;
								rotX -= (arc * 1.2f + aim) * 57.29578f;
								rotZ = Mth.sin(sp * (float)Math.PI) * -0.4f * 57.29578f;
							}
							if(rotX != 0.0f || rotZ != 0.0f) {
								GlStateManager.translate(pivot[0], pivot[1], pivot[2]);
								GlStateManager.rotate(rotX, 1.0f, 0.0f, 0.0f);
								if(rotZ != 0.0f) {
									GlStateManager.rotate(rotZ, 0.0f, 0.0f, 1.0f);
								}
								GlStateManager.translate(-pivot[0], -pivot[1], -pivot[2]);
							}
						}
						
						if(i != 0) {
							mc.getTextureManager().bindForSetup(highPolySkin.texture);
							if (abstractclientplayer.hurtTime > 0 || abstractclientplayer.deathTime > 0) {
								GlStateManager.color(1.2f, 0.8F, 0.8F, 1.0F);
							}else {
								GlStateManager.color(1.0f, 1.0F, 1.0F, 1.0F);
							}
						}
						EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(highPolySkin.limbsModel[i]));
						
						// Held items hang off the main hand limb. The FNAW models only have one
						// or two limbs so that is index 0 for them, but Bacon Hair has four and
						// its right arm is index 1.
						int mainHandLimb = (highPolySkin == HighPolySkin.BACON_HAIR
								|| highPolySkin == HighPolySkin.AMONG_US) ? 1 : 0;
						if(i == mainHandLimb) {
							GlStateManager.pushMatrix();
		
							GlStateManager.translate(-0.287f, 0.05f, 0.0f);
							
							if(highPolySkin == HighPolySkin.LONG_ARMS) {
								GlStateManager.translate(1.72f, 2.05f, -0.24f);
								ItemStack stk = abstractclientplayer.getMainHandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.22f, 0.8f, 0.6f);
											GlStateManager.rotate(-90.0f, 1.0f, 0.0f, 0.0f);
										}else if(itm instanceof BlockItem && !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.1f, 0.13f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.08f, -0.1f, 0.16f);
										}
									}
								}
							}else if(highPolySkin == HighPolySkin.WEIRD_CLIMBER_DUDE) {
								GlStateManager.translate(-0.029f, 1.2f, -3f);
								GlStateManager.rotate(-5.0f, 0.0f, 1.0f, 0.0f);
								float var17 = -1.2f * var15;
								if(abstractclientplayer.swinging) {
									float vvar17 = Mth.cos(-abstractclientplayer.getAttackAnim(f1) * (float)Math.PI * 2.0f - 1.2f) - 0.362f;
									var17 = vvar17 < var17 ? vvar17 : var17;
								}
								GlStateManager.translate(-0.02f * var17, 0.42f * var17, var17 * 0.35f);
								GlStateManager.rotate(var17 * 30.0f, 1.0f, 0.0f, 0.0f);
								GlStateManager.rotate(110.0f, 1.0f, 0.0f, 0.0f);
								ItemStack stk = abstractclientplayer.getMainHandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.18f, 1.0f, 0.4f);
											GlStateManager.rotate(-95.0f, 1.0f, 0.0f, 0.0f);
										}else if(itm instanceof BlockItem && !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.1f, 0.13f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.08f, -0.1f, 0.16f);
										}
									}
								}
							}else if(highPolySkin == HighPolySkin.LAXATIVE_DUDE) {
								GlStateManager.translate(1.291f, 2.44f, -2.18f);
								GlStateManager.rotate(95.0f, 1.0f, 0.0f, 0.0f);
								ItemStack stk = abstractclientplayer.getMainHandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.08f, 1.5f, 0.1f);
											GlStateManager.rotate(-205.0f, 1.0f, 0.0f, 0.0f);
										}else if (itm == Items.SHIELD) {
											if(!abstractclientplayer.isUsingItem()) {
												GlStateManager.rotate(-90.0f, 0.0f, 1.0f, 0.0f);
												GlStateManager.rotate(9.0f, 0.0f, 0.0f, 1.0f);
												GlStateManager.translate(-0.9, 0.8, -0.3);
											} else {
												GlStateManager.rotate(175.0f, 0.0f, 1.0f, 0.0f);
												GlStateManager.rotate(-95.0f, 1.0f, 0.0f, 0.0f);
												GlStateManager.translate(-0.8f, 0.0f, 0.0f);
											}
										}else if(itm instanceof BlockItem && !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.35f, 0.4f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.1f, -0.1f, 0.16f);
										} else {
											GlStateManager.rotate(-40.0f, 1.0f, 0.0f, 0.0f);
											GlStateManager.translate(-0.08f, -0.2f, -0.25f);
										}
									}
								}
							}else if(highPolySkin == HighPolySkin.AMONG_US) {
								// Same accounting as Bacon Hair below: the limb transform rotates
								// about the hand, it does not move the origin, so this is still
								// model space with the origin between the feet. The crewmate's
								// right hand sits at (1.25, 1.03, -0.15), and the enclosing
								// translate plus renderHeldItem's own offset land the grip
								// (0.40, 0.28, -0.23) short of wherever this translate points.
								// Measured in game rather than derived: renderHeldItem's own offset
								// and the item's first-person transform, run through the rotate and
								// scale below, land the item about (0, +0.71, +0.36) from whatever
								// this translate points at. The crewmate's fist fills
								// x 0.97..1.59, y 0.64..1.75, z -0.66..0.28, so the item is aimed
								// just in front of it - anything inside those bounds disappears
								// into the hand.
								GlStateManager.translate(1.30f, 0.44f, -1.41f);
								GlStateManager.rotate(95.0f, 1.0f, 0.0f, 0.0f);
								// The crewmate's hand is small, so a full-size item reads as a
								// plank. This is the mesh-to-vanilla ratio Bacon Hair uses, scaled
								// down for the smaller fist.
								GlStateManager.scale(1.6f, 1.6f, 1.6f);
								ItemStack stk = abstractclientplayer.getMainHandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.1f, 0.15f, 0.15f);
											GlStateManager.rotate(-80.0f, 1.0f, 0.0f, 0.0f);
										}else if(itm instanceof BlockItem
												&& !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.1f, 0.13f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.08f, -0.1f, 0.16f);
										}
									}
								}
							}else if(highPolySkin == HighPolySkin.BACON_HAIR) {
								// The limb transform is a rotation ABOUT the shoulder, not a move to
								// it, so this is still model space with the origin between the feet.
								// Right hand sits at x=+0.9, y=1.25; the enclosing translates already
								// contribute (-0.397, 0.525, 0.25), so cancel those out.
								// renderHeldItem draws with a FIRST_PERSON_RIGHT_HAND transform that
								// assumes the item lies along the hand, so it needs the same ~95deg
								// X rotation the other mesh skins use; at -15 the pickaxe stood
								// straight up the arm and disappeared behind the shoulder. Rotating
								// also swings renderHeldItem's own (-0.11, 0.475, 0.25) offset, which
								// drops the grip 0.81 and pushes it back 0.33 - the translate below
								// cancels exactly that, so the grip stays in the hand.
								GlStateManager.translate(1.30f, 1.53f, -0.23f);
								GlStateManager.rotate(95.0f, 1.0f, 0.0f, 0.0f);
								// This mesh is roughly 1.7x the height vanilla model space assumes,
								// so an item drawn at its native size reads as a toy. Scale AFTER
								// the placement translate above, or the position scales with it.
								GlStateManager.scale(1.75f, 1.75f, 1.75f);
								ItemStack stk = abstractclientplayer.getMainHandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.1f, 0.15f, 0.15f);
											GlStateManager.rotate(-80.0f, 1.0f, 0.0f, 0.0f);
										}else if(itm == Items.SHIELD) {
											// Held flat along the forearm, then swung across the
											// chest to face forward while actually blocking.
											// NOTE the axes here are the doubly-rotated frame from
											// the rotate(95,X) above plus the rotate(-90,Y) below:
											// local +X ends up pointing DOWN the model, local +Y
											// forward, and local +Z inboard toward the body centre.
											// Signing these as if they were model axes is what left
											// the shield up at head height and sunk into the chest.
											// These are also downstream of the scale(1.75) above, so
											// every offset lands 1.75x further than its number reads.
											GlStateManager.rotate(-90.0f, 0.0f, 1.0f, 0.0f);
											if(!abstractclientplayer.isUsingItem()) {
												GlStateManager.translate(0.28f, -0.07f, -0.07f);
											}else {
												GlStateManager.translate(0.33f, 0.20f, 0.09f);
											}
										}else if(itm instanceof BlockItem
												&& !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.1f, 0.13f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.08f, -0.1f, 0.16f);
										}
									}
								}
							}
							
							renderHeldItem(abstractclientplayer, abstractclientplayer.getMainHandItem(), abstractclientplayer.getMainArm());
							GlStateManager.popMatrix();
							
							
							
							
							GlStateManager.pushMatrix();
							
							GlStateManager.translate(-3.0177f, -0.05f, 0.23f);
							
							if(highPolySkin == HighPolySkin.LONG_ARMS) {
								GlStateManager.translate(1.72f, 2.05f, -0.24f);
								ItemStack stk = abstractclientplayer.getOffhandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.22f, 0.8f, 0.6f);
											GlStateManager.rotate(-90.0f, 1.0f, 0.0f, 0.0f);
										}else if(itm instanceof BlockItem && !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.1f, 0.13f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.08f, -0.1f, 0.16f);
										}
									}
								}
							}else if(highPolySkin == HighPolySkin.WEIRD_CLIMBER_DUDE) {
								GlStateManager.translate(-0.029f, 1.2f, -3f);
								GlStateManager.rotate(-5.0f, 0.0f, 1.0f, 0.0f);
								float var17 = -1.2f * var15;
								if(abstractclientplayer.swinging) {
									float vvar17 = Mth.cos(-abstractclientplayer.getAttackAnim(f1) * (float)Math.PI * 2.0f - 1.2f) - 0.362f;
									var17 = vvar17 < var17 ? vvar17 : var17;
								}
								GlStateManager.translate(-0.02f * var17, 0.42f * var17, var17 * 0.35f);
								GlStateManager.rotate(var17 * 30.0f, 1.0f, 0.0f, 0.0f);
								GlStateManager.rotate(110.0f, 1.0f, 0.0f, 0.0f);
								ItemStack stk = abstractclientplayer.getOffhandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(-0.18f, 1.0f, 0.4f);
											GlStateManager.rotate(-95.0f, 1.0f, 0.0f, 0.0f);
										}else if(itm instanceof BlockItem && !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.1f, 0.13f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(-0.08f, -0.1f, 0.16f);
										}
									}
								}
							}else if(highPolySkin == HighPolySkin.LAXATIVE_DUDE) {
								GlStateManager.translate(1.291f, 2.44f, -2.18f);
								GlStateManager.rotate(95.0f, 1.0f, 0.0f, 0.0f);
								ItemStack stk = abstractclientplayer.getOffhandItem();
								if(stk != null) {
									Item itm = stk.getItem();
									if(itm != null) {
										if(itm == Items.BOW) {
											GlStateManager.translate(0.65f, 0.5f, 0.3f);
											GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f);
											GlStateManager.rotate(25.0f, 1.0f, 0.0f, 0.0f);
											GlStateManager.rotate(-25.0f, 1.0f, 0.0f, 0.0f);
										}else if (itm == Items.SHIELD) {
											if(!abstractclientplayer.isUsingItem()) {
												GlStateManager.rotate(90.0f, 0.0f, 1.0f, 0.0f);
												GlStateManager.rotate(5.0f, 0.0f, 0.0f, 1.0f);
												GlStateManager.translate(0.13f, 0.4f, 0.6f);
											} else {
												GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
												GlStateManager.rotate(-95.0f, 1.0f, 0.0f, 0.0f);
												GlStateManager.rotate(5.0f, 0.0f, 0.0f, 1.0f);
												GlStateManager.translate(-0.85f, 0.32f, -0.299f);
											}
										}else if(itm instanceof BlockItem && !((BlockItem)itm).getBlock().defaultBlockState().canOcclude()) {
											GlStateManager.translate(0.0f, -0.35f, 0.4f);
										}else if(!EaglerItemCompat.isFull3D(itm)) {
											GlStateManager.translate(1.1f, -0.38f, 0.16f);
										}
									}
								}
							}
							
							renderHeldItem(abstractclientplayer, abstractclientplayer.getOffhandItem(), abstractclientplayer.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
							GlStateManager.popMatrix();
						}
		
						GlStateManager.popMatrix();
					}
				}
				
				if(highPolySkin.eyesModel != null) {
					float ff = 0.00416f;
					int brightness = currentPackedLight;
					float blockLight = (brightness % 65536) * ff;
					float skyLight = (brightness / 65536) * ff;
					float sunCurve = (float)((abstractclientplayer.level.getDayTime() + 4000l) % 24000) / 24000.0f;
					sunCurve = Mth.clamp(9.8f - Mth.abs(sunCurve * 5.0f + sunCurve * sunCurve * 45.0f - 14.3f) * 0.7f, 0.0f, 1.0f);
					skyLight = skyLight * (sunCurve * 0.85f + 0.15f);
					blockLight = blockLight * (sunCurve * 0.3f + 0.7f);
					float eyeBrightness = blockLight;
					if(skyLight > eyeBrightness) {
						eyeBrightness = skyLight;
					}
					eyeBrightness += blockLight * 0.2f;
					eyeBrightness = 1.0f - eyeBrightness;
					eyeBrightness = Mth.clamp(eyeBrightness * 1.9f - 1.0f, 0.0f, 1.0f);
					if(eyeBrightness > 0.1f) {
						GlStateManager.enableBlend();
						GlStateManager.blendFunc(GL_ONE, GL_ONE);
						GlStateManager.color(eyeBrightness * 7.0f, eyeBrightness * 7.0f, eyeBrightness * 7.0f, 1.0f);
						if(jumpFactor > 0.0f) {
							GlStateManager.pushMatrix();
							GlStateManager.translate(0.0f, jumpFactor * 3.0f, 0.0f);
						}
						GlStateManager.disableTexture2D();
						GlStateManager.disableLighting();
						GlStateManager.enableCull();
						
						EaglercraftGPU.drawHighPoly(EaglerMeshLoader.getEaglerMesh(highPolySkin.eyesModel));
						
						GlStateManager.enableTexture2D();
						GlStateManager.enableLighting();
						GlStateManager.disableCull();
						if(jumpFactor > 0.0f) {
							GlStateManager.popMatrix();
						}
						GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
						GlStateManager.disableBlend();
					}
				}
			}catch(Throwable t) {
				logger.error("Couldn\'t render entity");
				logger.error(t);
			}
			GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
			GlStateManager.enableTexture2D();
			GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
			GlStateManager.enableCull();
			GlStateManager.popMatrix();
			if (!this.renderOutlines) {
				this.renderName(abstractclientplayer, d0, nameY, d2);
			}
		}
	}

	public void renderRightArm(AbstractClientPlayer clientPlayer) {
		
	}

	public void renderLeftArm(AbstractClientPlayer clientPlayer) {
		
	}
	
	private void renderHeldItem(AbstractClientPlayer clientPlayer, ItemStack itemstack, HumanoidArm hand) {
        if (itemstack != null) {
            Item item = itemstack.getItem();
            Minecraft mc = Minecraft.getInstance();

            GlStateManager.pushMatrix();
            GlStateManager.translate(-0.11F, 0.475F, 0.25F);
			if (clientPlayer.fishing != null) {
				itemstack = new ItemStack(Items.FISHING_ROD, 0);
			}

			if (item instanceof BlockItem && (item instanceof BlockItem ? ((BlockItem)item).getBlock() : null).defaultBlockState().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
				GlStateManager.translate(0.0F, 0.1875F, -0.3125F);
				GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
				GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
			}
						
			if (clientPlayer.isCrouching()) {
				GlStateManager.translate(0.0F, 0.203125F, 0.0F);
			}
                
            mc.getItemRenderer().renderStatic(clientPlayer, itemstack,
                    hand == HumanoidArm.RIGHT ? ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
                            : ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
                    false, currentPoseStack, currentBuffer, clientPlayer.level,
                    currentPackedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    clientPlayer.getId());
            
            GlStateManager.popMatrix();
        }
    }

	public void renderLivingAt(AbstractClientPlayer abstractclientplayer, double d0, double d1, double d2) {
		if (abstractclientplayer.isAlive() && abstractclientplayer.isSleeping()) {
			renderLivingAt(abstractclientplayer, d0 - (double) 0.0D,
					d1 - (double) 0.0D, d2 - (double) 0.0D);
		} else {
			renderLivingAt(abstractclientplayer, d0, d1, d2);
		}
	}

	// ---- 1.12.2 RendererLivingEntity hooks, bridged onto 1.18.2 ----
	// The high-poly renderer calls these on super. 1.18.2 renamed them and routes drawing
	// through the PoseStack/MultiBufferSource held during render(), so they forward to it.

	/** 1.12.2's outline pass flag; 1.18.2 decides this per RenderType. */
	protected boolean renderOutlines = false;

	protected void doRenderSuper(AbstractClientPlayer entity, double x, double y, double z,
			float entityYaw, float partialTicks) {
		super.render(entity, entityYaw, partialTicks, currentPoseStack, currentBuffer,
				currentPackedLight);
	}

	protected void rotateCorpse(AbstractClientPlayer entity, float ageInTicks, float rotationYaw,
			float partialTicks) {
		super.setupRotations(entity, currentPoseStack, ageInTicks, rotationYaw, partialTicks);
	}

	/** 1.12.2's per-frame bob; 1.18.2 calls it getBob. */
	protected float handleRotationFloat(AbstractClientPlayer entity, float partialTicks) {
		return super.getBob(entity, partialTicks);
	}

	/** 1.12.2's pre-model hook; 1.18.2 calls it scale. */
	protected void preRenderCallback(AbstractClientPlayer entity, float partialTicks) {
		super.scale(entity, currentPoseStack, partialTicks);
	}

	/** 1.12.2's name-plate draw; 1.18.2 takes the component and the buffer source. */
	protected void renderName(AbstractClientPlayer entity, double x, double y, double z) {
		super.renderNameTag(entity, entity.getDisplayName(), currentPoseStack, currentBuffer,
				currentPackedLight);
	}
}