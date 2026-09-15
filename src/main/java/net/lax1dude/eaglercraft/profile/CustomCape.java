package net.lax1dude.eaglercraft.profile;

import net.lax1dude.eaglercraft.opengl.ImageData;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
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
public class CustomCape {

	public final String name;
	public final byte[] texture;

	// Fork addition: the original encoded image (PNG/GIF/...) behind an HD cape,
	// plus how the wearer positioned it. null/empty means this is a stock cape.
	public byte[] hdImageData;
	public int hdOffsetX;
	public int hdOffsetY;
	public int hdScale = HDCapeComposer.SCALE_ONE;

	public boolean hasHD() {
		return hdImageData != null && hdImageData.length > 0;
	}

	private EaglerSkinTexture textureInstance;
	private ResourceLocation resourceLocation;

	private static int texId = 0;

	/** Fork addition: non-null when this cape's image is an animated GIF. */
	private HDCapeAnimation animation;

	public CustomCape(String name, byte[] texture) {
		this.name = name;
		this.texture = texture;
		byte[] texture2 = new byte[4096];
		SkinConverter.convertCape23x17RGBto32x32RGBA(texture, texture2);
		this.textureInstance = new EaglerSkinTexture(texture2, 32, 32);
		this.resourceLocation = null;
	}

	/**
	 * Fork addition: a cape backed by an arbitrary image of any size. texture is
	 * still the stock 1173 byte fallback so this cape works on unpatched servers.
	 */
	public CustomCape(String name, byte[] texture, byte[] hdImageData, int hdOffsetX, int hdOffsetY, int hdScale) {
		this.name = name;
		this.texture = texture;
		this.hdImageData = hdImageData;
		this.hdOffsetX = hdOffsetX;
		this.hdOffsetY = hdOffsetY;
		this.hdScale = hdScale <= 0 ? HDCapeComposer.SCALE_ONE : hdScale;
		this.textureInstance = buildTexture();
		this.resourceLocation = null;
	}

	private EaglerSkinTexture buildTexture() {
		if(hasHD()) {
			animation = HDCapeAnimation.create(hdImageData, hdOffsetX, hdOffsetY, hdScale);
			if(animation != null) {
				EaglerSkinTexture animated = new EaglerSkinTexture(
						new byte[HDCapeAnimation.ANIM_TEX_SIZE * HDCapeAnimation.ANIM_TEX_SIZE * 4],
						HDCapeAnimation.ANIM_TEX_SIZE, HDCapeAnimation.ANIM_TEX_SIZE);
				animation.tick(animated);
				return animated;
			}
			// Must pass the real type. The single arg overload assumes image/png, which
			// can fail outright for a JPEG or WEBP and silently drop us to the low res
			// fallback below.
			ImageData img = ImageData.loadImageFile(hdImageData, HDCapeAnimation.sniffMime(hdImageData));
			if(img != null) {
				return new EaglerSkinTexture(HDCapeComposer.compose(img, hdOffsetX, hdOffsetY, hdScale),
						HDCapeComposer.TEX_SIZE, HDCapeComposer.TEX_SIZE);
			}
		}
		byte[] fallback = new byte[4096];
		SkinConverter.convertCape23x17RGBto32x32RGBA(texture, fallback);
		return new EaglerSkinTexture(fallback, 32, 32);
	}

	/** Fork addition: re-render in place after the wearer moved or zoomed the image. */
	public void updateTransform(int offsetX, int offsetY, int scale) {
		this.hdOffsetX = offsetX;
		this.hdOffsetY = offsetY;
		this.hdScale = scale <= 0 ? HDCapeComposer.SCALE_ONE : scale;
		if(!hasHD()) {
			return;
		}
		if(animation != null) {
			animation = HDCapeAnimation.create(hdImageData, this.hdOffsetX, this.hdOffsetY, this.hdScale);
			if(animation != null) {
				animation.tick(textureInstance);
				return;
			}
		}
		ImageData img = ImageData.loadImageFile(hdImageData, HDCapeAnimation.sniffMime(hdImageData));
		if(img == null) {
			return;
		}
		textureInstance.copyPixelsIn(HDCapeComposer.compose(img, this.hdOffsetX, this.hdOffsetY, this.hdScale));
	}
	
	public void load() {
		if(resourceLocation == null) {
			resourceLocation = new ResourceLocation("eagler:capes/custom/tex_" + texId++);
			Minecraft.getInstance().getTextureManager().register(resourceLocation, textureInstance);
		}
	}
	
	public ResourceLocation getResource() {
		// Fork addition: animated capes advance lazily here, so a cape nobody is
		// looking at costs nothing.
		if(animation != null) {
			animation.tick(textureInstance);
		}
		return resourceLocation;
	}
	
	public void delete() {
		if(resourceLocation != null) {
			Minecraft.getInstance().getTextureManager().release(resourceLocation);
			resourceLocation = null;
		}
	}

}
