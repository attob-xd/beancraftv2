package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldStem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

public class WorldSelectionList extends ObjectSelectionList<WorldSelectionList.WorldListEntry> {
   static final Logger LOGGER = LogUtils.getLogger();
   static final DateFormat DATE_FORMAT = new SimpleDateFormat();
   static final ResourceLocation ICON_MISSING = new ResourceLocation("textures/misc/unknown_server.png");
   static final ResourceLocation ICON_OVERLAY_LOCATION = new ResourceLocation("textures/gui/world_selection.png");
   static final Component FROM_NEWER_TOOLTIP_1 = new TranslatableComponent("selectWorld.tooltip.fromNewerVersion1").withStyle(ChatFormatting.RED);
   static final Component FROM_NEWER_TOOLTIP_2 = new TranslatableComponent("selectWorld.tooltip.fromNewerVersion2").withStyle(ChatFormatting.RED);
   static final Component SNAPSHOT_TOOLTIP_1 = new TranslatableComponent("selectWorld.tooltip.snapshot1").withStyle(ChatFormatting.GOLD);
   static final Component SNAPSHOT_TOOLTIP_2 = new TranslatableComponent("selectWorld.tooltip.snapshot2").withStyle(ChatFormatting.GOLD);
   static final Component WORLD_LOCKED_TOOLTIP = new TranslatableComponent("selectWorld.locked").withStyle(ChatFormatting.RED);
   static final Component WORLD_REQUIRES_CONVERSION = new TranslatableComponent("selectWorld.conversion.tooltip").withStyle(ChatFormatting.RED);
   private final SelectWorldScreen screen;
   @Nullable
   private List<LevelSummary> cachedList;

   public WorldSelectionList(
      SelectWorldScreen var1, Minecraft var2, int var3, int var4, int var5, int var6, int var7, Supplier<String> var8, @Nullable WorldSelectionList var9
   ) {
      super(var2, var3, var4, var5, var6, var7);
      this.screen = var1;
      if (var9 != null) {
         this.cachedList = var9.cachedList;
      }

      this.refreshList(var8, false);
   }

   public void refreshList(Supplier<String> var1, boolean var2) {
      this.clearEntries();
      LevelStorageSource var3 = this.minecraft.getLevelSource();
      if (this.cachedList == null || var2) {
         try {
            this.cachedList = var3.getLevelList();
         } catch (LevelStorageException var7) {
            LOGGER.error("Couldn't load level list", var7);
            this.minecraft.setScreen(new ErrorScreen(new TranslatableComponent("selectWorld.unable_to_load"), new TextComponent(var7.getMessage())));
            return;
         }

         Collections.sort(this.cachedList);
      }

      if (this.cachedList.isEmpty()) {
         this.minecraft.setScreen(CreateWorldScreen.createFresh(null));
      } else {
         String var4 = ((String)var1.get()).toLowerCase(Locale.ROOT);

         for (LevelSummary var6 : this.cachedList) {
            if (var6.getLevelName().toLowerCase(Locale.ROOT).contains(var4) || var6.getLevelId().toLowerCase(Locale.ROOT).contains(var4)) {
               this.addEntry(new WorldSelectionList.WorldListEntry(this, var6));
            }
         }
      }
   }

   @Override
   protected int getScrollbarPosition() {
      return super.getScrollbarPosition() + 20;
   }

   @Override
   public int getRowWidth() {
      return super.getRowWidth() + 50;
   }

   @Override
   protected boolean isFocused() {
      return this.screen.getFocused() == this;
   }

   public void setSelected(@Nullable WorldSelectionList.WorldListEntry var1) {
      super.setSelected(var1);
      this.screen.updateButtonStatus(var1 != null && !var1.summary.isDisabled());
   }

   @Override
   protected void moveSelection(AbstractSelectionList.SelectionDirection var1) {
      this.moveSelection(var1, var0 -> !var0.summary.isDisabled());
   }

   public Optional<WorldSelectionList.WorldListEntry> getSelectedOpt() {
      return Optional.ofNullable(this.getSelected());
   }

   public SelectWorldScreen getScreen() {
      return this.screen;
   }

   public final class WorldListEntry extends ObjectSelectionList.Entry<WorldSelectionList.WorldListEntry> implements AutoCloseable {
      private static final int ICON_WIDTH = 32;
      private static final int ICON_HEIGHT = 32;
      private static final int ICON_OVERLAY_X_JOIN = 0;
      private static final int ICON_OVERLAY_X_JOIN_WITH_NOTIFY = 32;
      private static final int ICON_OVERLAY_X_WARNING = 64;
      private static final int ICON_OVERLAY_X_ERROR = 96;
      private static final int ICON_OVERLAY_Y_UNSELECTED = 0;
      private static final int ICON_OVERLAY_Y_SELECTED = 32;
      private final Minecraft minecraft;
      private final SelectWorldScreen screen;
      final LevelSummary summary;
      private final ResourceLocation iconLocation;
      @Nullable
      private File iconFile;
      @Nullable
      private final DynamicTexture icon;
      private long lastClickTime;

      public WorldListEntry(WorldSelectionList var2, LevelSummary var3) {
         this.screen = var2.getScreen();
         this.summary = var3;
         this.minecraft = Minecraft.getInstance();
         String var4 = var3.getLevelId();
         this.iconLocation = new ResourceLocation(
            "minecraft", "worlds/" + Util.sanitizeName(var4, ResourceLocation::validPathChar) + "/" + Hashing.sha1().hashUnencodedChars(var4) + "/icon"
         );
         this.iconFile = var3.getIcon();
         if (!this.iconFile.isFile()) {
            this.iconFile = null;
         }

         this.icon = this.loadServerIcon();
      }

      @Override
      public Component getNarration() {
         TranslatableComponent var1 = new TranslatableComponent(
            "narrator.select.world",
            this.summary.getLevelName(),
            new Date(this.summary.getLastPlayed()),
            this.summary.isHardcore()
               ? new TranslatableComponent("gameMode.hardcore")
               : new TranslatableComponent("gameMode." + this.summary.getGameMode().getName()),
            this.summary.hasCheats() ? new TranslatableComponent("selectWorld.cheats") : TextComponent.EMPTY,
            this.summary.getWorldVersionName()
         );
         Object var2;
         if (this.summary.isLocked()) {
            var2 = CommonComponents.joinForNarration(var1, WorldSelectionList.WORLD_LOCKED_TOOLTIP);
         } else {
            var2 = var1;
         }

         return new TranslatableComponent("narrator.select", var2);
      }

      @Override
      public void render(PoseStack var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, boolean var9, float var10) {
         String var11 = this.summary.getLevelName();
         String var12 = this.summary.getLevelId() + " (" + WorldSelectionList.DATE_FORMAT.format(new Date(this.summary.getLastPlayed())) + ")";
         if (StringUtils.isEmpty(var11)) {
            var11 = I18n.get("selectWorld.world") + " " + (var2 + 1);
         }

         Component var13 = this.summary.getInfo();
         this.minecraft.font.draw(var1, var11, (float)(var4 + 32 + 3), (float)(var3 + 1), 16777215);
         this.minecraft.font.draw(var1, var12, (float)(var4 + 32 + 3), (float)(var3 + 9 + 3), 8421504);
         this.minecraft.font.draw(var1, var13, (float)(var4 + 32 + 3), (float)(var3 + 9 + 9 + 3), 8421504);
         RenderSystem.setShader(GameRenderer::getPositionTexShader);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.setShaderTexture(0, this.icon != null ? this.iconLocation : WorldSelectionList.ICON_MISSING);
         RenderSystem.enableBlend();
         GuiComponent.blit(var1, var4, var3, 0.0F, 0.0F, 32, 32, 32, 32);
         RenderSystem.disableBlend();
         if (this.minecraft.options.touchscreen || var9) {
            RenderSystem.setShaderTexture(0, WorldSelectionList.ICON_OVERLAY_LOCATION);
            GuiComponent.fill(var1, var4, var3, var4 + 32, var3 + 32, -1601138544);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int var14 = var7 - var4;
            boolean var15 = var14 < 32;
            int var16 = var15 ? 32 : 0;
            if (this.summary.isLocked()) {
               GuiComponent.blit(var1, var4, var3, 96.0F, var16, 32, 32, 256, 256);
               if (var15) {
                  this.screen.setToolTip(this.minecraft.font.split(WorldSelectionList.WORLD_LOCKED_TOOLTIP, 175));
               }
            } else if (this.summary.requiresManualConversion()) {
               GuiComponent.blit(var1, var4, var3, 96.0F, var16, 32, 32, 256, 256);
               if (var15) {
                  this.screen.setToolTip(this.minecraft.font.split(WorldSelectionList.WORLD_REQUIRES_CONVERSION, 175));
               }
            } else if (this.summary.markVersionInList()) {
               GuiComponent.blit(var1, var4, var3, 32.0F, var16, 32, 32, 256, 256);
               if (this.summary.askToOpenWorld()) {
                  GuiComponent.blit(var1, var4, var3, 96.0F, var16, 32, 32, 256, 256);
                  if (var15) {
                     this.screen
                        .setToolTip(
                           ImmutableList.of(
                              WorldSelectionList.FROM_NEWER_TOOLTIP_1.getVisualOrderText(), WorldSelectionList.FROM_NEWER_TOOLTIP_2.getVisualOrderText()
                           )
                        );
                  }
               } else if (!SharedConstants.getCurrentVersion().isStable()) {
                  GuiComponent.blit(var1, var4, var3, 64.0F, var16, 32, 32, 256, 256);
                  if (var15) {
                     this.screen
                        .setToolTip(
                           ImmutableList.of(
                              WorldSelectionList.SNAPSHOT_TOOLTIP_1.getVisualOrderText(), WorldSelectionList.SNAPSHOT_TOOLTIP_2.getVisualOrderText()
                           )
                        );
                  }
               }
            } else {
               GuiComponent.blit(var1, var4, var3, 0.0F, var16, 32, 32, 256, 256);
            }
         }
      }

      @Override
      public boolean mouseClicked(double var1, double var3, int var5) {
         if (this.summary.isDisabled()) {
            return true;
         } else {
            WorldSelectionList.this.setSelected(this);
            this.screen.updateButtonStatus(WorldSelectionList.this.getSelectedOpt().isPresent());
            if (var1 - WorldSelectionList.this.getRowLeft() <= 32.0) {
               this.joinWorld();
               return true;
            } else if (Util.getMillis() - this.lastClickTime < 250L) {
               this.joinWorld();
               return true;
            } else {
               this.lastClickTime = Util.getMillis();
               return false;
            }
         }
      }

      public void joinWorld() {
         if (!this.summary.isDisabled()) {
            LevelSummary.BackupStatus var1 = this.summary.backupStatus();
            if (var1.shouldBackup()) {
               String var2 = "selectWorld.backupQuestion." + var1.getTranslationKey();
               String var3 = "selectWorld.backupWarning." + var1.getTranslationKey();
               TranslatableComponent var4 = new TranslatableComponent(var2);
               if (var1.isSevere()) {
                  var4.withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
               }

               TranslatableComponent var5 = new TranslatableComponent(var3, this.summary.getWorldVersionName(), SharedConstants.getCurrentVersion().getName());
               this.minecraft.setScreen(new BackupConfirmScreen(this.screen, (var1x, var2x) -> {
                  if (var1x) {
                     String var3x = this.summary.getLevelId();

                     try (LevelStorageSource.LevelStorageAccess var4x = this.minecraft.getLevelSource().createAccess(var3x)) {
                        EditWorldScreen.makeBackupAndShowToast(var4x);
                     } catch (IOException var9) {
                        SystemToast.onWorldAccessFailure(this.minecraft, var3x);
                        WorldSelectionList.LOGGER.error("Failed to backup level {}", var3x, var9);
                     }
                  }

                  this.loadWorld();
               }, var4, var5, false));
            } else if (this.summary.askToOpenWorld()) {
               this.minecraft
                  .setScreen(
                     new ConfirmScreen(
                        var1x -> {
                           if (var1x) {
                              try {
                                 this.loadWorld();
                              } catch (Exception var3x) {
                                 WorldSelectionList.LOGGER.error("Failure to open 'future world'", var3x);
                                 this.minecraft
                                    .setScreen(
                                       new AlertScreen(
                                          () -> this.minecraft.setScreen(this.screen),
                                          new TranslatableComponent("selectWorld.futureworld.error.title"),
                                          new TranslatableComponent("selectWorld.futureworld.error.text")
                                       )
                                    );
                              }
                           } else {
                              this.minecraft.setScreen(this.screen);
                           }
                        },
                        new TranslatableComponent("selectWorld.versionQuestion"),
                        new TranslatableComponent("selectWorld.versionWarning", this.summary.getWorldVersionName()),
                        new TranslatableComponent("selectWorld.versionJoinButton"),
                        CommonComponents.GUI_CANCEL
                     )
                  );
            } else {
               this.loadWorld();
            }
         }
      }

      public void deleteWorld() {
         this.minecraft
            .setScreen(
               new ConfirmScreen(
                  var1 -> {
                     if (var1) {
                        this.minecraft.setScreen(new ProgressScreen(true));
                        this.doDeleteWorld();
                     }

                     this.minecraft.setScreen(this.screen);
                  },
                  new TranslatableComponent("selectWorld.deleteQuestion"),
                  new TranslatableComponent("selectWorld.deleteWarning", this.summary.getLevelName()),
                  new TranslatableComponent("selectWorld.deleteButton"),
                  CommonComponents.GUI_CANCEL
               )
            );
      }

      public void doDeleteWorld() {
         LevelStorageSource var1 = this.minecraft.getLevelSource();
         String var2 = this.summary.getLevelId();

         try (LevelStorageSource.LevelStorageAccess var3 = var1.createAccess(var2)) {
            var3.deleteLevel();
         } catch (IOException var8) {
            SystemToast.onWorldDeleteFailure(this.minecraft, var2);
            WorldSelectionList.LOGGER.error("Failed to delete world {}", var2, var8);
         }

         WorldSelectionList.this.refreshList(() -> this.screen.searchBox.getValue(), true);
      }

      public void editWorld() {
         String var1 = this.summary.getLevelId();

         try {
            LevelStorageSource.LevelStorageAccess var2 = this.minecraft.getLevelSource().createAccess(var1);
            this.minecraft.setScreen(new EditWorldScreen(var3x -> {
               try {
                  var2.close();
               } catch (IOException var5) {
                  WorldSelectionList.LOGGER.error("Failed to unlock level {}", var1, var5);
               }

               if (var3x) {
                  WorldSelectionList.this.refreshList(() -> this.screen.searchBox.getValue(), true);
               }

               this.minecraft.setScreen(this.screen);
            }, var2));
         } catch (IOException var3) {
            SystemToast.onWorldAccessFailure(this.minecraft, var1);
            WorldSelectionList.LOGGER.error("Failed to access level {}", var1, var3);
            WorldSelectionList.this.refreshList(() -> this.screen.searchBox.getValue(), true);
         }
      }

      public void recreateWorld() {
         this.queueLoadScreen();

         try (
            LevelStorageSource.LevelStorageAccess var1 = this.minecraft.getLevelSource().createAccess(this.summary.getLevelId());
            WorldStem var2 = this.minecraft.makeWorldStem(var1, false);
         ) {
            WorldGenSettings var3 = var2.worldData().worldGenSettings();
            Path var4 = CreateWorldScreen.createTempDataPackDirFromExistingWorld(var1.getLevelPath(LevelResource.DATAPACK_DIR), this.minecraft);
            if (var3.isOldCustomizedWorld()) {
               this.minecraft
                  .setScreen(
                     new ConfirmScreen(
                        var3x -> this.minecraft.setScreen((Screen)(var3x ? CreateWorldScreen.createFromExisting(this.screen, var2, var4) : this.screen)),
                        new TranslatableComponent("selectWorld.recreate.customized.title"),
                        new TranslatableComponent("selectWorld.recreate.customized.text"),
                        CommonComponents.GUI_PROCEED,
                        CommonComponents.GUI_CANCEL
                     )
                  );
            } else {
               this.minecraft.setScreen(CreateWorldScreen.createFromExisting(this.screen, var2, var4));
            }
         } catch (Exception var9) {
            WorldSelectionList.LOGGER.error("Unable to recreate world", var9);
            this.minecraft
               .setScreen(
                  new AlertScreen(
                     () -> this.minecraft.setScreen(this.screen),
                     new TranslatableComponent("selectWorld.recreate.error.title"),
                     new TranslatableComponent("selectWorld.recreate.error.text")
                  )
               );
         }
      }

      /**
       * Opens the world on the integrated-server worker instead of in this page.
       *
       * <p>Vanilla's {@code minecraft.loadLevel} runs {@code MinecraftServer.spin}, and on
       * TeaVM that thread is a green one sharing the single core with rendering - the server
       * and the renderer then starve each other by turns, and a world never finishes loading.
       * EaglercraftX puts the server in a Web Worker, which is a real thread; see
       * {@link SingleplayerLaunch} for the whole story and for the two-screen handover.
       *
       * <p>The {@code levelExists} guard is vanilla's and is kept: the worker reads the same
       * IndexedDB world database this list was built from, so a name that is missing here would
       * be missing there too.
       */
      private void loadWorld() {
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         if (this.minecraft.getLevelSource().levelExists(this.summary.getLevelId())) {
            this.queueLoadScreen();
            net.lax1dude.eaglercraft.sp.SingleplayerLaunch.loadWorld(
               this.summary.getLevelId(), this.summary.getLevelName());
         }
      }

      private void queueLoadScreen() {
         this.minecraft.forceSetScreen(new GenericDirtMessageScreen(new TranslatableComponent("selectWorld.data_read")));
      }

      @Nullable
      private DynamicTexture loadServerIcon() {
         boolean var1 = this.iconFile != null && this.iconFile.isFile();
         if (var1) {
            try {
               DynamicTexture var5;
               try (FileInputStream var2 = new FileInputStream(this.iconFile)) {
                  NativeImage var3 = NativeImage.read(var2);
                  Validate.validState(var3.getWidth() == 64, "Must be 64 pixels wide", new Object[0]);
                  Validate.validState(var3.getHeight() == 64, "Must be 64 pixels high", new Object[0]);
                  DynamicTexture var4 = new DynamicTexture(var3);
                  this.minecraft.getTextureManager().register(this.iconLocation, var4);
                  var5 = var4;
               }

               return var5;
            } catch (Throwable var8) {
               WorldSelectionList.LOGGER.error("Invalid icon for world {}", this.summary.getLevelId(), var8);
               this.iconFile = null;
               return null;
            }
         } else {
            this.minecraft.getTextureManager().release(this.iconLocation);
            return null;
         }
      }

      @Override
      public void close() {
         if (this.icon != null) {
            this.icon.close();
         }
      }

      public String getLevelName() {
         return this.summary.getLevelName();
      }
   }
}
