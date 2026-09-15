package com.mojang.blaze3d.audio;

import com.mojang.math.Vector3f;

import net.lax1dude.eaglercraft.internal.PlatformAudio;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces blaze3d's OpenAL listener.
 *
 * Same state as vanilla - position, orientation, master gain - pushed to EaglercraftX's Web
 * Audio listener instead of to AL10.alListener*.
 *
 * The one real conversion is the orientation. OpenAL and Web Audio both take a forward vector
 * and an up vector; EaglercraftX's setListener takes Minecraft's own pitch and yaw in degrees,
 * because that is what the 1.12.2 sound system had to hand. So the vector is turned back into
 * those angles using Minecraft's convention: yaw 0 faces -Z and increases clockwise, and
 * positive pitch looks down, which is why the signs below are what they are. Getting this
 * wrong does not fail, it just puts sounds on the wrong side of the player.
 *
 * Gain is stored here and applied per sound rather than to the listener, because
 * PlatformAudio has no master-gain control and Channel already multiplies it in - which is
 * also how EaglercraftX's own sound manager did it.
 */
public class Listener {

	private float gain = 1.0F;
	private Vec3 position = Vec3.ZERO;
	private float pitchDegrees;
	private float yawDegrees;

	public void setListenerPosition(Vec3 position) {
		this.position = position;
		push();
	}

	public Vec3 getListenerPosition() {
		return position;
	}

	public void setListenerOrientation(Vector3f look, Vector3f up) {
		float x = look.x();
		float y = look.y();
		float z = look.z();
		// look.y is -sin(pitch) and the horizontal components are (-sin(yaw), cos(yaw)); see
		// the class comment for why Minecraft's convention is the one being inverted here.
		float clamped = y < -1.0F ? -1.0F : (y > 1.0F ? 1.0F : y);
		this.pitchDegrees = (float) Math.toDegrees(-Math.asin(clamped));
		this.yawDegrees = (float) Math.toDegrees(Math.atan2(-x, z));
		push();
	}

	/**
	 * Guarded because PlatformAudio.setListener dereferences the audio context without
	 * checking it, and a page that was never granted one would fail here with a bare NPE
	 * rather than through Library.init, which reports the real reason.
	 */
	private void push() {
		if (!PlatformAudio.available()) {
			return;
		}
		PlatformAudio.setListener((float) position.x, (float) position.y, (float) position.z,
				pitchDegrees, yawDegrees);
	}

	public void setGain(float gain) {
		this.gain = gain;
	}

	public float getGain() {
		return gain;
	}

	public void reset() {
		this.position = Vec3.ZERO;
		this.pitchDegrees = 0.0F;
		this.yawDegrees = 0.0F;
		push();
	}
}
