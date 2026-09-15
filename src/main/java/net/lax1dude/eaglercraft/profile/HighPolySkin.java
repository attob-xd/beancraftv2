package net.lax1dude.eaglercraft.profile;

import net.minecraft.resources.ResourceLocation;

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
public enum HighPolySkin {

	LONG_ARMS(
			new ResourceLocation("eagler:mesh/longarms.png"),
			new ResourceLocation("eagler:mesh/longarms0.mdl"),
			null,
			new ResourceLocation("eagler:mesh/longarms2.mdl"),
			new ResourceLocation[] {
				new ResourceLocation("eagler:mesh/longarms1.mdl")
			},
			new float[] {
				1.325f
			},
			0.0f,
			new ResourceLocation("eagler:mesh/longarms.fallback.png")
	),
	
	WEIRD_CLIMBER_DUDE(
			new ResourceLocation("eagler:mesh/weirdclimber.png"),
			new ResourceLocation("eagler:mesh/weirdclimber0.mdl"),
			null,
			new ResourceLocation("eagler:mesh/weirdclimber2.mdl"),
			new ResourceLocation[] {
				new ResourceLocation("eagler:mesh/weirdclimber1.mdl")
			},
			new float[] {
				2.62f
			},
			-90.0f,
			new ResourceLocation("eagler:mesh/weirdclimber.fallback.png")
	),
	
	LAXATIVE_DUDE(
			new ResourceLocation("eagler:mesh/laxativedude.png"),
			new ResourceLocation("eagler:mesh/laxativedude0.mdl"),
			null,
			new ResourceLocation("eagler:mesh/laxativedude3.mdl"),
			new ResourceLocation[] {
				new ResourceLocation("eagler:mesh/laxativedude1.mdl"),
				new ResourceLocation("eagler:mesh/laxativedude2.mdl")
			},
			new float[] {
				2.04f
			},
			0.0f,
			new ResourceLocation("eagler:mesh/laxativedude.fallback.png")
	),
	
	BABY_CHARLES(
			new ResourceLocation("eagler:mesh/charles.png"),
			new ResourceLocation("eagler:mesh/charles0.mdl"),
			new ResourceLocation("eagler:mesh/charles1.mdl"),
			new ResourceLocation("eagler:mesh/charles2.mdl"),
			new ResourceLocation[] {},
			new float[] {},
			0.0f,
			new ResourceLocation("eagler:mesh/charles.fallback.png")
	),
	
	BABY_WINSTON(
			new ResourceLocation("eagler:mesh/winston.png"),
			new ResourceLocation("eagler:mesh/winston0.mdl"),
			null,
			new ResourceLocation("eagler:mesh/winston1.mdl"),
			new ResourceLocation[] {},
			new float[] {},
			0.0f,
			new ResourceLocation("eagler:mesh/winston.fallback.png")
	),

	BACON_HAIR(
			new ResourceLocation("eagler:mesh/baconhair.png"),
			new ResourceLocation("eagler:mesh/baconhair_body.mdl"),
			new ResourceLocation("eagler:mesh/baconhair_head.mdl"),
			null,
			new ResourceLocation[] {
				new ResourceLocation("eagler:mesh/baconhair_larm.mdl"),
				new ResourceLocation("eagler:mesh/baconhair_rarm.mdl"),
				new ResourceLocation("eagler:mesh/baconhair_lleg.mdl"),
				new ResourceLocation("eagler:mesh/baconhair_rleg.mdl")
			},
			new float[] { 0.0f, 0.0f, 0.0f, 0.0f },
			0.0f,
			new ResourceLocation("eagler:mesh/baconhair.fallback.png")
	),

	/**
	 * Among Us crewmate, converted from among_us_rig.glb. The crewmate has no neck,
	 * so there is no separate head model - the visor is part of the body and the
	 * whole shell turns with the player, which is how the character reads.
	 */
	AMONG_US(
			new ResourceLocation("eagler:mesh/amongus.png"),
			new ResourceLocation("eagler:mesh/amongus_body.mdl"),
			null,
			null,
			new ResourceLocation[] {
				new ResourceLocation("eagler:mesh/amongus_larm.mdl"),
				new ResourceLocation("eagler:mesh/amongus_rarm.mdl"),
				new ResourceLocation("eagler:mesh/amongus_lleg.mdl"),
				new ResourceLocation("eagler:mesh/amongus_rleg.mdl")
			},
			new float[] { 0.0f, 0.0f, 0.0f, 0.0f },
			0.0f,
			new ResourceLocation("eagler:mesh/amongus.fallback.png")
	);


	/** Neck joint, so the head can track where the player is looking. */
	public static final float[] BACON_HAIR_NECK = new float[] { -0.014f, 2.699f, 0.000f };

	/**
	 * Pivots for AMONG_US: left arm, right arm, left leg, right leg. The arm
	 * entries are the rig's own hand bones (Mano.L/R), which is what drives a
	 * crewmate's whole mitt. The leg entries are the centres of the cut the mesh
	 * converter makes at the crotch; the legs are animated by lifting rather than
	 * by rotating, so they are recorded for reference and not rotated about.
	 */
	public static final float[][] AMONG_US_PIVOTS = new float[][] {
		{ -1.278f, 1.705f, -0.059f },
		{  1.278f, 1.692f, -0.068f },
		{ -0.527f, 0.800f,  0.000f },
		{  0.511f, 0.800f,  0.000f }
	};

	/** Shoulder and hip pivots for BACON_HAIR: left arm, right arm, left leg, right leg. */
	public static final float[][] BACON_HAIR_PIVOTS = new float[][] {
		{ -0.898f, 2.650f, -0.060f },
		{  0.898f, 2.650f, -0.060f },
		{ -0.270f, 1.683f, -0.097f },
		{  0.270f, 1.683f, -0.097f }
	};



	/**
	 * The FNAW skins are hidden behind a setting because they are joke models.
	 * Bacon Hair is a normal character, so it must not disappear when that toggle
	 * is off.
	 */
	public boolean requiresFNAWSetting() {
		// BACON_HAIR and AMONG_US are not FNAW joke skins, so they are always
		// available and are never hidden behind the FNAW skins toggle.
		return this != BACON_HAIR && this != AMONG_US;
	}

	public static float highPolyScale = 0.5f;

	public final ResourceLocation texture;
	public final ResourceLocation bodyModel;
	public final ResourceLocation headModel;
	public final ResourceLocation eyesModel;
	public final ResourceLocation[] limbsModel;
	public final float[] limbsOffset;
	public final float limbsInitialRotation;
	public final ResourceLocation fallbackTexture;
	
	HighPolySkin(ResourceLocation texture, ResourceLocation bodyModel, ResourceLocation headModel, ResourceLocation eyesModel,
			ResourceLocation[] limbsModel, float[] limbsOffset, float limbsInitialRotation, ResourceLocation fallbackTexture) {
		this.texture = texture;
		this.bodyModel = bodyModel;
		this.headModel = headModel;
		this.eyesModel = eyesModel;
		this.limbsModel = limbsModel;
		this.limbsOffset = limbsOffset;
		this.limbsInitialRotation = limbsInitialRotation;
		this.fallbackTexture = fallbackTexture;
	}

}
