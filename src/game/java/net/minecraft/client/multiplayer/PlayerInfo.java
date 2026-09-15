package net.minecraft.client.multiplayer;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import java.util.Map;
import javax.annotation.Nullable;
import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.lax1dude.eaglercraft.compat.EaglerClientState;
import net.lax1dude.eaglercraft.profile.SkinModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

public class PlayerInfo {
   private final GameProfile profile;
   private final Map<Type, ResourceLocation> textureLocations = Maps.newEnumMap(Type.class);
   private GameType gameMode;
   private int latency;
   private boolean pendingTextures;
   @Nullable
   private String skinModel;
   @Nullable
   private Component tabListDisplayName;
   private int lastHealth;
   private int displayHealth;
   private long lastHealthTime;
   private long healthBlinkTime;
   private long renderVisibilityId;

   public PlayerInfo(ClientboundPlayerInfoPacket.PlayerUpdate var1) {
      this.profile = var1.getProfile();
      this.gameMode = var1.getGameMode();
      this.latency = var1.getLatency();
      this.tabListDisplayName = var1.getDisplayName();
   }

   public GameProfile getProfile() {
      return this.profile;
   }

   @Nullable
   public GameType getGameMode() {
      return this.gameMode;
   }

   protected void setGameMode(GameType var1) {
      this.gameMode = var1;
   }

   public int getLatency() {
      return this.latency;
   }

   protected void setLatency(int var1) {
      this.latency = var1;
   }

   public boolean isCapeLoaded() {
      return this.getCapeLocation() != null;
   }

   public boolean isSkinLoaded() {
      return this.getSkinLocation() != null;
   }

   /*
    * Skins and capes come from the server over EaglercraftX's own protocol, not from Mojang.
    *
    * Vanilla answers all three of these out of `textureLocations`, a map filled in by
    * registerTextures() below, which hands the GameProfile to SkinManager. SkinManager asks
    * the session service for the player's texture properties and then downloads each one with
    * an HttpTexture - that is, java.net.URL.openConnection(Proxy), which TeaVM does not have.
    * So in a page that path cannot work at all: before this change every player wore the
    * default Steve or Alex, no custom skin ever appeared, and no cape ever did.
    *
    * EaglercraftX solved this years ago and this port already carried the solution across
    * without connecting it. A server sends skins as plugin messages (CPacketGetOtherSkinEAG
    * and friends); ServerSkinCache and ServerCapeCache request one per unknown player, hold
    * the decoded pixels, register them with the texture manager and hand back a
    * ResourceLocation - returning the default in the meantime and swapping it for the real one
    * when the reply arrives. That is what 1.12.2's NetworkPlayerInfo returned here, and it is
    * what these now return.
    *
    * The caches hang off the connection through EaglerClientState, so before a connection
    * exists - the profile preview on the title screen, for instance - there is nothing to ask
    * and the vanilla default is the honest answer.
    */
   public String getModelName() {
      SkinModel var1 = this.getEaglerSkinModel();
      return var1 == null ? DefaultPlayerSkin.getSkinModelName(this.profile.getId()) : var1.profileSkinType;
   }

   public ResourceLocation getSkinLocation() {
      EaglerClientState var1 = EaglerClientState.current();
      if (var1 == null || this.profile.getId() == null) {
         return DefaultPlayerSkin.getDefaultSkin(this.profile.getId());
      }
      // Looked up by UUID, not by GameProfile. The GameProfile overload reads
      // profile.getTextures(), which is a method on lax1dude's GameProfile but not on the
      // authlib GameProfile that actually wins on this classpath - so it is a
      // NoSuchMethodError waiting for the first player to come into view. The UUID overload
      // answers from the same cache and needs nothing Mojang-specific, which is all this
      // build can honour anyway: there are no Mojang texture properties in a browser.
      return (ResourceLocation)MoreObjects.firstNonNull(
         var1.getSkinCache().getSkin(EaglercraftUUID.fromJavaUUID(this.profile.getId())).getResourceLocation(),
         DefaultPlayerSkin.getDefaultSkin(this.profile.getId()));
   }

   @Nullable
   public ResourceLocation getCapeLocation() {
      EaglerClientState var1 = EaglerClientState.current();
      if (var1 == null || this.profile.getId() == null) {
         return null;
      }
      return var1.getCapeCache().getCape(EaglercraftUUID.fromJavaUUID(this.profile.getId())).getResourceLocation();
   }

   /**
    * Elytra textures are a Mojang-hosted texture type with no EaglercraftX equivalent - the
    * protocol carries a skin and a cape and nothing else. Answering null is what vanilla does
    * for a player who has no custom elytra texture, and ElytraLayer then falls back to the
    * player's cape and finally to the stock wings, so an elytra still renders correctly.
    */
   @Nullable
   public ResourceLocation getElytraLocation() {
      return null;
   }

   @Nullable
   public PlayerTeam getTeam() {
      return Minecraft.getInstance().level.getScoreboard().getPlayersTeam(this.profile.getName());
   }

   /**
    * Vanilla downloads this player's textures from Mojang here, on first use. Nothing calls it
    * any more - see the accessors above for where the textures come from instead - and it is
    * deliberately not merely unused but empty: SkinManager.registerSkins reaches
    * URL.openConnection(Proxy), so leaving a live body behind would be a crash waiting for
    * whichever vanilla class calls this next.
    */
   protected void registerTextures() {
   }

   public void setTabListDisplayName(@Nullable Component var1) {
      this.tabListDisplayName = var1;
   }

   @Nullable
   public Component getTabListDisplayName() {
      return this.tabListDisplayName;
   }

   public int getLastHealth() {
      return this.lastHealth;
   }

   public void setLastHealth(int var1) {
      this.lastHealth = var1;
   }

   public int getDisplayHealth() {
      return this.displayHealth;
   }

   public void setDisplayHealth(int var1) {
      this.displayHealth = var1;
   }

   public long getLastHealthTime() {
      return this.lastHealthTime;
   }

   public void setLastHealthTime(long var1) {
      this.lastHealthTime = var1;
   }

   public long getHealthBlinkTime() {
      return this.healthBlinkTime;
   }

   public void setHealthBlinkTime(long var1) {
      this.healthBlinkTime = var1;
   }

   public long getRenderVisibilityId() {
      return this.renderVisibilityId;
   }

   public void setRenderVisibilityId(long var1) {
      this.renderVisibilityId = var1;
   }

   /**
    * EaglercraftX: the player mesh the server told us this player uses. The skin cache
    * hangs off the connection via EaglerClientState rather than off the packet handler,
    * so this goes through that; before a connection exists everyone is STEVE.
    */
   public SkinModel getEaglerSkinModel() {
      EaglerClientState var1 = EaglerClientState.current();
      return var1 == null || this.profile.getId() == null ? SkinModel.STEVE
         : var1.getSkinCache().getSkin(EaglercraftUUID.fromJavaUUID(this.profile.getId())).getSkinModel();
   }
}
