package net.lax1dude.eaglercraft.profile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.lax1dude.eaglercraft.compat.EaglerClientState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.ImageData;
import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.lax1dude.eaglercraft.socket.protocol.pkt.client.CPacketGetOtherCapeEAG;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.texture.TextureManager;
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
public class ServerCapeCache {

	private static final Logger logger = LogManager.getLogger("ServerCapeCache");

	public class CapeCacheEntry {
		
		protected final boolean isPresetCape;
		protected final int presetCapeId;
		protected final CacheCustomCape customCape;
		
		protected long lastCacheHit = EagRuntime.steadyTimeMillis();

		/** Fork addition: set for animated HD capes worn by other players. */
		protected HDCapeAnimation animation;
		
		protected CapeCacheEntry(EaglerSkinTexture textureInstance, ResourceLocation resourceLocation) {
			this.isPresetCape = false;
			this.presetCapeId = -1;
			this.customCape = new CacheCustomCape(textureInstance, resourceLocation);
			ServerCapeCache.this.textureManager.register(resourceLocation, textureInstance);
		}
		
		/**
		 * Use only for the constant for the client player
		 */
		protected CapeCacheEntry(ResourceLocation resourceLocation) {
			this.isPresetCape = false;
			this.presetCapeId = -1;
			this.customCape = new CacheCustomCape(null, resourceLocation);
		}
		
		protected CapeCacheEntry(int presetSkinId) {
			this.isPresetCape = true;
			this.presetCapeId = presetSkinId;
			this.customCape = null;
		}
		
		public ResourceLocation getResourceLocation() {
			if(animation != null && customCape != null) {
				animation.tick(customCape.textureInstance);
			}
			if(isPresetCape) {
				return DefaultCapes.getCapeFromId(presetCapeId).location;
			}else {
				if(customCape != null) {
					return customCape.resourceLocation;
				}else {
					return null;
				}
			}
		}
		
		protected void free() {
			if(!isPresetCape && customCape.resourceLocation != null) {
				ServerCapeCache.this.textureManager.release(customCape.resourceLocation);
			}
		}

	}

	protected static class CacheCustomCape {
		
		protected final EaglerSkinTexture textureInstance;
		protected final ResourceLocation resourceLocation;
		
		protected CacheCustomCape(EaglerSkinTexture textureInstance, ResourceLocation resourceLocation) {
			this.textureInstance = textureInstance;
			this.resourceLocation = resourceLocation;
		}

	}

	private final CapeCacheEntry defaultCacheEntry = new CapeCacheEntry(0);
	private final Map<EaglercraftUUID, CapeCacheEntry> capesCache = new HashMap<>();
	private final Map<EaglercraftUUID, Long> waitingCapes = new HashMap<>();
	private final Map<EaglercraftUUID, Long> evictedCapes = new HashMap<>();

	private final ClientPacketListener netHandler;
	protected final TextureManager textureManager;
	
	private final EaglercraftUUID clientPlayerId;
	private CapeCacheEntry clientPlayerCacheEntry;

	private long lastFlush = EagRuntime.steadyTimeMillis();
	private long lastFlushReq = EagRuntime.steadyTimeMillis();
	private long lastFlushEvict = EagRuntime.steadyTimeMillis();

	private static int texId = 0;
	public static boolean needReloadClientCape = false;

	public ServerCapeCache(ClientPacketListener netHandler, TextureManager textureManager) {
		this.netHandler = netHandler;
		this.textureManager = textureManager;
		this.clientPlayerId = EaglerProfile.getPlayerUUID();
		reloadClientPlayerCape();
	}
	
	public void reloadClientPlayerCape() {
		needReloadClientCape = false;
		this.clientPlayerCacheEntry = new CapeCacheEntry(EaglerProfile.getActiveCapeResourceLocation());
	}

	public CapeCacheEntry getClientPlayerCape() {
		return clientPlayerCacheEntry;
	}

	/**
	 * True if this UUID is us. clientPlayerId comes from the local SESSION profile,
	 * but player list lookups use the profile the SERVER assigned, and in offline
	 * mode those are derived differently and need not match. When they diverge the
	 * session-only check misses and we render our own cape from the server's low
	 * resolution copy instead of the local HD texture.
	 */
	private boolean isClientPlayer(EaglercraftUUID player) {
		if(player.equals(clientPlayerId)) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if(mc != null && mc.player != null && player.equals(mc.player.getUUID())) {
			return true;
		}
		return false;
	}

	public CapeCacheEntry getCape(EaglercraftUUID player) {
		if(isClientPlayer(player)) {
			// Fork fix: this entry used to be rebuilt ONLY when the server forced a
			// cape, so changing your own cape in the editor never updated the cape you
			// actually wear in the world. The editor previews the live CustomCape while
			// the world kept binding a stale texture, which looks exactly like the HD
			// cape failing. Resolve it live instead.
			// active is null when no cape is selected, so compare null-safely -
			// calling equals() on it threw every frame once the deferred shadow
			// pass started rendering the local player.
			ResourceLocation active = EaglerProfile.getActiveCapeResourceLocation();
			if(clientPlayerCacheEntry == null || !java.util.Objects.equals(active,
					clientPlayerCacheEntry.getResourceLocation())) {
				reloadClientPlayerCape();
			}
			return clientPlayerCacheEntry;
		}
		CapeCacheEntry etr = capesCache.get(player);
		if(etr == null) {
			if(!waitingCapes.containsKey(player) && !evictedCapes.containsKey(player)) {
				waitingCapes.put(player, EagRuntime.steadyTimeMillis());
				EaglerClientState.get(netHandler).sendEaglerMessage(new CPacketGetOtherCapeEAG(player.msb, player.lsb));
			}
			return defaultCacheEntry;
		}else {
			etr.lastCacheHit = EagRuntime.steadyTimeMillis();
			return etr;
		}
	}

	public void cacheCapePreset(EaglercraftUUID player, int presetId) {
		if(waitingCapes.remove(player) != null) {
			CapeCacheEntry etr = capesCache.remove(player);
			if(etr != null) {
				etr.free();
			}
			capesCache.put(player, new CapeCacheEntry(presetId));
		}else {
			logger.error("Unsolicited cape response recieved for \"{}\"! (preset {})", player, presetId);
		}
	}

	public void cacheCapeCustom(EaglercraftUUID player, byte[] pixels) {
		if(waitingCapes.remove(player) != null) {
			CapeCacheEntry etr = capesCache.remove(player);
			if(etr != null) {
				etr.free();
			}
			byte[] pixels32x32 = new byte[4096];
			SkinConverter.convertCape23x17RGBto32x32RGBA(pixels, pixels32x32);
			try {
				etr = new CapeCacheEntry(new EaglerSkinTexture(pixels32x32, 32, 32),
						new ResourceLocation("eagler:capes/multiplayer/tex_" + texId++));
			}catch(Throwable t) {
				etr = new CapeCacheEntry(0);
				logger.error("Could not process custom skin packet for \"{}\"!", player);
				logger.error(t);
			}
			capesCache.put(player, etr);
		}else {
			logger.error("Unsolicited skin response recieved for \"{}\"!", player);
		}
	}

	/**
	 * Fork addition: another player's HD cape. The payload is their original
	 * encoded image, decoded here by the platform decoder and composed into a
	 * high resolution cape texture that keeps the stock cape UV layout.
	 */
	public void cacheCapeCustomHD(EaglercraftUUID player, int offsetX, int offsetY, int scale, byte[] imageData) {
		if(waitingCapes.remove(player) != null) {
			CapeCacheEntry etr = capesCache.remove(player);
			if(etr != null) {
				etr.free();
			}
			try {
				HDCapeAnimation anim = HDCapeAnimation.create(imageData, offsetX, offsetY, scale);
				if(anim != null) {
					EaglerSkinTexture animatedTex = new EaglerSkinTexture(
							new byte[HDCapeAnimation.ANIM_TEX_SIZE * HDCapeAnimation.ANIM_TEX_SIZE * 4],
							HDCapeAnimation.ANIM_TEX_SIZE, HDCapeAnimation.ANIM_TEX_SIZE);
					anim.tick(animatedTex);
					etr = new CapeCacheEntry(animatedTex,
							new ResourceLocation("eagler:capes/multiplayer/tex_" + texId++));
					etr.animation = anim;
					capesCache.put(player, etr);
					return;
				}
				ImageData img = ImageData.loadImageFile(imageData, HDCapeAnimation.sniffMime(imageData));
				if(img == null) {
					throw new IllegalArgumentException("HD cape image could not be decoded");
				}
				byte[] composed = HDCapeComposer.compose(img, offsetX, offsetY, scale);
				etr = new CapeCacheEntry(
						new EaglerSkinTexture(composed, HDCapeComposer.TEX_SIZE, HDCapeComposer.TEX_SIZE),
						new ResourceLocation("eagler:capes/multiplayer/tex_" + texId++));
			}catch(Throwable t) {
				etr = new CapeCacheEntry(0);
				logger.error("Could not process HD cape packet for \"{}\"!", player);
				logger.error(t);
			}
			capesCache.put(player, etr);
		}else {
			logger.error("Unsolicited HD cape response recieved for \"{}\"!", player);
		}
	}

	public void flush() {
		long millis = EagRuntime.steadyTimeMillis();
		if(millis - lastFlushReq > 5000l) {
			lastFlushReq = millis;
			if(!waitingCapes.isEmpty()) {
				Iterator<Long> waitingItr = waitingCapes.values().iterator();
				while(waitingItr.hasNext()) {
					if(millis - waitingItr.next().longValue() > 30000l) {
						waitingItr.remove();
					}
				}
			}
		}
		if(millis - lastFlushEvict > 1000l) {
			lastFlushEvict = millis;
			if(!evictedCapes.isEmpty()) {
				Iterator<Long> evictItr = evictedCapes.values().iterator();
				while(evictItr.hasNext()) {
					if(millis - evictItr.next().longValue() > 3000l) {
						evictItr.remove();
					}
				}
			}
		}
		if(millis - lastFlush > 60000l) {
			lastFlush = millis;
			if(!capesCache.isEmpty()) {
				Iterator<CapeCacheEntry> entryItr = capesCache.values().iterator();
				while(entryItr.hasNext()) {
					CapeCacheEntry etr = entryItr.next();
					if(millis - etr.lastCacheHit > 900000l) { // 15 minutes
						entryItr.remove();
						etr.free();
					}
				}
			}
		}
		if(needReloadClientCape) {
			reloadClientPlayerCape();
		}
	}

	public void destroy() {
		Iterator<CapeCacheEntry> entryItr = capesCache.values().iterator();
		while(entryItr.hasNext()) {
			entryItr.next().free();
		}
		capesCache.clear();
		waitingCapes.clear();
		evictedCapes.clear();
	}

	public void evictCape(EaglercraftUUID uuid) {
		evictedCapes.put(uuid, Long.valueOf(EagRuntime.steadyTimeMillis()));
		CapeCacheEntry etr = capesCache.remove(uuid);
		if(etr != null) {
			etr.free();
		}
	}

	public void handleInvalidate(EaglercraftUUID uuid) {
		CapeCacheEntry etr = capesCache.remove(uuid);
		if(etr != null) {
			etr.free();
		}
	}

}
