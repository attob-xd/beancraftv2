package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class CreateWorldScreen extends Screen {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String TEMP_WORLD_PREFIX = "mcworld-";
   private static final Component GAME_MODEL_LABEL = new TranslatableComponent("selectWorld.gameMode");
   private static final Component SEED_LABEL = new TranslatableComponent("selectWorld.enterSeed");
   private static final Component SEED_INFO = new TranslatableComponent("selectWorld.seedInfo");
   private static final Component NAME_LABEL = new TranslatableComponent("selectWorld.enterName");
   private static final Component OUTPUT_DIR_INFO = new TranslatableComponent("selectWorld.resultFolder");
   private static final Component COMMANDS_INFO = new TranslatableComponent("selectWorld.allowCommands.info");
   @Nullable
   private final Screen lastScreen;
   private EditBox nameEdit;
   String resultFolder;
   private CreateWorldScreen.SelectedGameMode gameMode = CreateWorldScreen.SelectedGameMode.SURVIVAL;
   @Nullable
   private CreateWorldScreen.SelectedGameMode oldGameMode;
   private Difficulty difficulty = Difficulty.NORMAL;
   private boolean commands;
   private boolean commandsChanged;
   public boolean hardCore;
   protected DataPackConfig dataPacks;
   @Nullable
   private Path tempDataPackDir;
   @Nullable
   private PackRepository tempDataPackRepository;
   private boolean worldGenSettingsVisible;
   private Button createButton;
   private CycleButton<CreateWorldScreen.SelectedGameMode> modeButton;
   private CycleButton<Difficulty> difficultyButton;
   private Button moreOptionsButton;
   private Button gameRulesButton;
   private Button dataPacksButton;
   private CycleButton<Boolean> commandsButton;
   private Component gameModeHelp1;
   private Component gameModeHelp2;
   private String initName;
   private GameRules gameRules = new GameRules();
   public final WorldGenSettingsComponent worldGenSettingsComponent;

   public static CreateWorldScreen createFresh(@Nullable Screen var0) {
      RegistryAccess.Frozen var1 = RegistryAccess.BUILTIN.get();
      return new CreateWorldScreen(
         var0,
         DataPackConfig.DEFAULT,
         new WorldGenSettingsComponent(var1, WorldGenSettings.makeDefault(var1), Optional.of(WorldPreset.NORMAL), OptionalLong.empty())
      );
   }

   public static CreateWorldScreen createFromExisting(@Nullable Screen var0, WorldStem var1, @Nullable Path var2) {
      WorldData var3 = var1.worldData();
      LevelSettings var4 = var3.getLevelSettings();
      WorldGenSettings var5 = var3.worldGenSettings();
      RegistryAccess.Frozen var6 = var1.registryAccess();
      DataPackConfig var7 = var4.getDataPackConfig();
      CreateWorldScreen var8 = new CreateWorldScreen(var0, var7, new WorldGenSettingsComponent(var6, var5, WorldPreset.of(var5), OptionalLong.of(var5.seed())));
      var8.initName = var4.levelName();
      var8.commands = var4.allowCommands();
      var8.commandsChanged = true;
      var8.difficulty = var4.difficulty();
      var8.gameRules.assignFrom(var4.gameRules(), null);
      if (var4.hardcore()) {
         var8.gameMode = CreateWorldScreen.SelectedGameMode.HARDCORE;
      } else if (var4.gameType().isSurvival()) {
         var8.gameMode = CreateWorldScreen.SelectedGameMode.SURVIVAL;
      } else if (var4.gameType().isCreative()) {
         var8.gameMode = CreateWorldScreen.SelectedGameMode.CREATIVE;
      }

      var8.tempDataPackDir = var2;
      return var8;
   }

   private CreateWorldScreen(@Nullable Screen var1, DataPackConfig var2, WorldGenSettingsComponent var3) {
      super(new TranslatableComponent("selectWorld.create"));
      this.lastScreen = var1;
      this.initName = I18n.get("selectWorld.newWorld");
      this.dataPacks = var2;
      this.worldGenSettingsComponent = var3;
   }

   @Override
   public void tick() {
      this.nameEdit.tick();
      this.worldGenSettingsComponent.tick();
   }

   @Override
   protected void init() {
      this.minecraft.keyboardHandler.setSendRepeatsToGui(true);
      this.nameEdit = new EditBox(this.font, this.width / 2 - 100, 60, 200, 20, new TranslatableComponent("selectWorld.enterName")) {
         @Override
         protected MutableComponent createNarrationMessage() {
            return CommonComponents.joinForNarration(super.createNarrationMessage(), new TranslatableComponent("selectWorld.resultFolder"))
               .append(" ")
               .append(CreateWorldScreen.this.resultFolder);
         }
      };
      this.nameEdit.setValue(this.initName);
      this.nameEdit.setResponder(var1x -> {
         this.initName = var1x;
         this.createButton.active = !this.nameEdit.getValue().isEmpty();
         this.updateResultFolder();
      });
      this.addWidget(this.nameEdit);
      int var1 = this.width / 2 - 155;
      int var2 = this.width / 2 + 5;
      this.modeButton = this.addRenderableWidget(
         CycleButton.builder(CreateWorldScreen.SelectedGameMode::getDisplayName)
            .withValues(CreateWorldScreen.SelectedGameMode.SURVIVAL, CreateWorldScreen.SelectedGameMode.HARDCORE, CreateWorldScreen.SelectedGameMode.CREATIVE)
            .withInitialValue(this.gameMode)
            .withCustomNarration(
               var1x -> AbstractWidget.wrapDefaultNarrationMessage(var1x.getMessage())
                  .append(CommonComponents.NARRATION_SEPARATOR)
                  .append(this.gameModeHelp1)
                  .append(" ")
                  .append(this.gameModeHelp2)
            )
            .create(var1, 100, 150, 20, GAME_MODEL_LABEL, (var1x, var2x) -> this.setGameMode(var2x))
      );
      this.difficultyButton = this.addRenderableWidget(
         CycleButton.builder(Difficulty::getDisplayName)
            .withValues(Difficulty.values())
            .withInitialValue(this.getEffectiveDifficulty())
            .create(var2, 100, 150, 20, new TranslatableComponent("options.difficulty"), (var1x, var2x) -> this.difficulty = var2x)
      );
      this.commandsButton = this.addRenderableWidget(
         CycleButton.onOffBuilder(this.commands && !this.hardCore)
            .withCustomNarration(
               var0 -> CommonComponents.joinForNarration(var0.createDefaultNarrationMessage(), new TranslatableComponent("selectWorld.allowCommands.info"))
            )
            .create(var1, 151, 150, 20, new TranslatableComponent("selectWorld.allowCommands"), (var1x, var2x) -> {
               this.commandsChanged = true;
               this.commands = var2x;
            })
      );
      this.dataPacksButton = this.addRenderableWidget(
         new Button(var2, 151, 150, 20, new TranslatableComponent("selectWorld.dataPacks"), var1x -> this.openDataPackSelectionScreen())
      );
      this.gameRulesButton = this.addRenderableWidget(
         new Button(
            var1,
            185,
            150,
            20,
            new TranslatableComponent("selectWorld.gameRules"),
            var1x -> this.minecraft.setScreen(new EditGameRulesScreen(this.gameRules.copy(), var1xx -> {
               this.minecraft.setScreen(this);
               var1xx.ifPresent(var1xxx -> this.gameRules = var1xxx);
            }))
         )
      );
      this.worldGenSettingsComponent.init(this, this.minecraft, this.font);
      this.moreOptionsButton = this.addRenderableWidget(
         new Button(var2, 185, 150, 20, new TranslatableComponent("selectWorld.moreWorldOptions"), var1x -> this.toggleWorldGenSettingsVisibility())
      );
      this.createButton = this.addRenderableWidget(
         new Button(var1, this.height - 28, 150, 20, new TranslatableComponent("selectWorld.create"), var1x -> this.onCreate())
      );
      this.createButton.active = !this.initName.isEmpty();
      this.addRenderableWidget(new Button(var2, this.height - 28, 150, 20, CommonComponents.GUI_CANCEL, var1x -> this.popScreen()));
      this.refreshWorldGenSettingsVisibility();
      this.setInitialFocus(this.nameEdit);
      this.setGameMode(this.gameMode);
      this.updateResultFolder();
   }

   private Difficulty getEffectiveDifficulty() {
      return this.gameMode == CreateWorldScreen.SelectedGameMode.HARDCORE ? Difficulty.HARD : this.difficulty;
   }

   private void updateGameModeHelp() {
      this.gameModeHelp1 = new TranslatableComponent("selectWorld.gameMode." + this.gameMode.name + ".line1");
      this.gameModeHelp2 = new TranslatableComponent("selectWorld.gameMode." + this.gameMode.name + ".line2");
   }

   private void updateResultFolder() {
      this.resultFolder = this.nameEdit.getValue().trim();
      if (this.resultFolder.isEmpty()) {
         this.resultFolder = "World";
      }

      try {
         this.resultFolder = FileUtil.findAvailableName(this.minecraft.getLevelSource().getBaseDir(), this.resultFolder, "");
      } catch (Exception var4) {
         this.resultFolder = "World";

         try {
            this.resultFolder = FileUtil.findAvailableName(this.minecraft.getLevelSource().getBaseDir(), this.resultFolder, "");
         } catch (Exception var3) {
            throw new RuntimeException("Could not create save folder", var3);
         }
      }
   }

   @Override
   public void removed() {
      this.minecraft.keyboardHandler.setSendRepeatsToGui(false);
   }

   /**
    * Creates the world on the integrated-server worker rather than in this page.
    *
    * <p>Vanilla's {@code minecraft.createLevel} generates the world on a
    * {@code MinecraftServer.spin} thread, which TeaVM makes a green thread sharing the one core
    * with rendering; see {@link net.lax1dude.eaglercraft.sp.SingleplayerLaunch}. The worker is a
    * real thread, so generation stops competing with the renderer.
    *
    * <p>The settings are translated into EaglercraftX's {@code EaglerWorldSettings} because
    * that is what crosses the IPC boundary - {@code IPCPacket02InitWorld} carries seed, game
    * type, terrain type, generator options and the three flags, not a {@code WorldGenSettings}
    * object graph, which could not be serialised to a worker in any case. Everything vanilla
    * does before this - the data-pack copy and temp-resource cleanup - is left exactly as it
    * was, since it prepares files the worker then reads.
    */
   private void onCreate() {
      this.minecraft.forceSetScreen(new GenericDirtMessageScreen(new TranslatableComponent("createWorld.preparing")));
      if (this.copyTempDataPackDirToNewWorld()) {
         this.cleanupTempResources();
         WorldGenSettings var1 = this.worldGenSettingsComponent.makeSettings(this.hardCore);
         LevelSettings var2 = this.createLevelSettings(var1.isDebug());

         net.lax1dude.eaglercraft.compat.EaglerWorldSettings var3 =
            new net.lax1dude.eaglercraft.compat.EaglerWorldSettings(
               var1.seed(), var2.gameType(), var1.generateFeatures(), var2.hardcore());
         if (var2.allowCommands()) {
            var3 = var3.enableCommands();
         }
         if (var1.generateBonusChest()) {
            var3 = var3.enableBonusChest();
         }

         net.lax1dude.eaglercraft.sp.SingleplayerLaunch.createWorld(
            this.resultFolder, var2.levelName(), var3);
      }
   }

   private LevelSettings createLevelSettings(boolean var1) {
      String var2 = this.nameEdit.getValue().trim();
      if (var1) {
         GameRules var3 = new GameRules();
         var3.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
         return new LevelSettings(var2, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, var3, DataPackConfig.DEFAULT);
      } else {
         return new LevelSettings(
            var2, this.gameMode.gameType, this.hardCore, this.getEffectiveDifficulty(), this.commands && !this.hardCore, this.gameRules, this.dataPacks
         );
      }
   }

   private void toggleWorldGenSettingsVisibility() {
      this.setWorldGenSettingsVisible(!this.worldGenSettingsVisible);
   }

   private void setGameMode(CreateWorldScreen.SelectedGameMode var1) {
      if (!this.commandsChanged) {
         this.commands = var1 == CreateWorldScreen.SelectedGameMode.CREATIVE;
         this.commandsButton.setValue(this.commands);
      }

      if (var1 == CreateWorldScreen.SelectedGameMode.HARDCORE) {
         this.hardCore = true;
         this.commandsButton.active = false;
         this.commandsButton.setValue(false);
         this.worldGenSettingsComponent.switchToHardcore();
         this.difficultyButton.setValue(Difficulty.HARD);
         this.difficultyButton.active = false;
      } else {
         this.hardCore = false;
         this.commandsButton.active = true;
         this.commandsButton.setValue(this.commands);
         this.worldGenSettingsComponent.switchOutOfHardcode();
         this.difficultyButton.setValue(this.difficulty);
         this.difficultyButton.active = true;
      }

      this.gameMode = var1;
      this.updateGameModeHelp();
   }

   public void refreshWorldGenSettingsVisibility() {
      this.setWorldGenSettingsVisible(this.worldGenSettingsVisible);
   }

   private void setWorldGenSettingsVisible(boolean var1) {
      this.worldGenSettingsVisible = var1;
      this.modeButton.visible = !var1;
      this.difficultyButton.visible = !var1;
      if (this.worldGenSettingsComponent.isDebug()) {
         this.dataPacksButton.visible = false;
         this.modeButton.active = false;
         if (this.oldGameMode == null) {
            this.oldGameMode = this.gameMode;
         }

         this.setGameMode(CreateWorldScreen.SelectedGameMode.DEBUG);
         this.commandsButton.visible = false;
      } else {
         this.modeButton.active = true;
         if (this.oldGameMode != null) {
            this.setGameMode(this.oldGameMode);
         }

         this.commandsButton.visible = !var1;
         this.dataPacksButton.visible = !var1;
      }

      this.worldGenSettingsComponent.setVisibility(var1);
      this.nameEdit.setVisible(!var1);
      if (var1) {
         this.moreOptionsButton.setMessage(CommonComponents.GUI_DONE);
      } else {
         this.moreOptionsButton.setMessage(new TranslatableComponent("selectWorld.moreWorldOptions"));
      }

      this.gameRulesButton.visible = !var1;
   }

   @Override
   public boolean keyPressed(int var1, int var2, int var3) {
      if (super.keyPressed(var1, var2, var3)) {
         return true;
      } else if (var1 != 257 && var1 != 335) {
         return false;
      } else {
         this.onCreate();
         return true;
      }
   }

   @Override
   public void onClose() {
      if (this.worldGenSettingsVisible) {
         this.setWorldGenSettingsVisible(false);
      } else {
         this.popScreen();
      }
   }

   public void popScreen() {
      this.minecraft.setScreen(this.lastScreen);
      this.cleanupTempResources();
   }

   private void cleanupTempResources() {
      if (this.tempDataPackRepository != null) {
         this.tempDataPackRepository.close();
      }

      this.removeTempDataPackDir();
   }

   @Override
   public void render(PoseStack var1, int var2, int var3, float var4) {
      this.renderBackground(var1);
      drawCenteredString(var1, this.font, this.title, this.width / 2, 20, -1);
      if (this.worldGenSettingsVisible) {
         drawString(var1, this.font, SEED_LABEL, this.width / 2 - 100, 47, -6250336);
         drawString(var1, this.font, SEED_INFO, this.width / 2 - 100, 85, -6250336);
         this.worldGenSettingsComponent.render(var1, var2, var3, var4);
      } else {
         drawString(var1, this.font, NAME_LABEL, this.width / 2 - 100, 47, -6250336);
         drawString(var1, this.font, new TextComponent("").append(OUTPUT_DIR_INFO).append(" ").append(this.resultFolder), this.width / 2 - 100, 85, -6250336);
         this.nameEdit.render(var1, var2, var3, var4);
         drawString(var1, this.font, this.gameModeHelp1, this.width / 2 - 150, 122, -6250336);
         drawString(var1, this.font, this.gameModeHelp2, this.width / 2 - 150, 134, -6250336);
         if (this.commandsButton.visible) {
            drawString(var1, this.font, COMMANDS_INFO, this.width / 2 - 150, 172, -6250336);
         }
      }

      super.render(var1, var2, var3, var4);
   }

   @Override
   protected <T extends GuiEventListener & NarratableEntry> T addWidget(T var1) {
      return super.addWidget((T)var1);
   }

   @Override
   protected <T extends GuiEventListener & Widget & NarratableEntry> T addRenderableWidget(T var1) {
      return super.addRenderableWidget((T)var1);
   }

   @Nullable
   protected Path getTempDataPackDir() {
      if (this.tempDataPackDir == null) {
         try {
            this.tempDataPackDir = Files.createTempDirectory("mcworld-");
         } catch (IOException var2) {
            LOGGER.warn("Failed to create temporary dir", var2);
            SystemToast.onPackCopyFailure(this.minecraft, this.resultFolder);
            this.popScreen();
         }
      }

      return this.tempDataPackDir;
   }

   private void openDataPackSelectionScreen() {
      Pair var1 = this.getDataPackSelectionSettings();
      if (var1 != null) {
         this.minecraft
            .setScreen(
               new PackSelectionScreen(
                  this, (PackRepository)var1.getSecond(), this::tryApplyNewDataPacks, (File)var1.getFirst(), new TranslatableComponent("dataPack.title")
               )
            );
      }
   }

   private void tryApplyNewDataPacks(PackRepository var1) {
      ImmutableList var2 = ImmutableList.copyOf(var1.getSelectedIds());
      List var3 = var1.getAvailableIds().stream().filter(var1x -> !var2.contains(var1x)).collect(ImmutableList.toImmutableList());
      DataPackConfig var4 = new DataPackConfig(var2, var3);
      if (var2.equals(this.dataPacks.getEnabled())) {
         this.dataPacks = var4;
      } else {
         this.minecraft.tell(() -> this.minecraft.setScreen(new GenericDirtMessageScreen(new TranslatableComponent("dataPack.validation.working"))));
         WorldStem.load(
               new WorldStem.InitConfig(var1, Commands.CommandSelection.INTEGRATED, 2, false),
               () -> var4,
               (var1x, var2x) -> {
                  RegistryAccess var3x = this.worldGenSettingsComponent.registryHolder();
                  RegistryAccess.Writable var4x = RegistryAccess.builtinCopy();
                  RegistryOps var5 = RegistryOps.create(JsonOps.INSTANCE, var3x);
                  RegistryOps var6 = RegistryOps.createAndLoad(JsonOps.INSTANCE, var4x, var1x);
                  DataResult var7 = WorldGenSettings.CODEC
                     .encodeStart(var5, this.worldGenSettingsComponent.makeSettings(this.hardCore))
                     .flatMap(var1xx -> WorldGenSettings.CODEC.parse(var6, var1xx));
                  WorldGenSettings var8 = (WorldGenSettings)var7.getOrThrow(
                     false, Util.prefix("Error parsing worldgen settings after loading data packs: ", LOGGER::error)
                  );
                  LevelSettings var9 = this.createLevelSettings(var8.isDebug());
                  return Pair.of(new PrimaryLevelData(var9, var8, var7.lifecycle()), var4x.freeze());
               },
               Util.backgroundExecutor(),
               this.minecraft
            )
            .thenAcceptAsync(var2x -> {
               this.dataPacks = var4;
               this.worldGenSettingsComponent.updateDataPacks(var2x);
               var2x.close();
            }, this.minecraft)
            .handle(
               (var1x, var2x) -> {
                  if (var2x != null) {
                     LOGGER.warn("Failed to validate datapack", var2x);
                     this.minecraft
                        .tell(
                           () -> this.minecraft
                              .setScreen(
                                 new ConfirmScreen(
                                    var1xx -> {
                                       if (var1xx) {
                                          this.openDataPackSelectionScreen();
                                       } else {
                                          this.dataPacks = DataPackConfig.DEFAULT;
                                          this.minecraft.setScreen(this);
                                       }
                                    },
                                    new TranslatableComponent("dataPack.validation.failed"),
                                    TextComponent.EMPTY,
                                    new TranslatableComponent("dataPack.validation.back"),
                                    new TranslatableComponent("dataPack.validation.reset")
                                 )
                              )
                        );
                  } else {
                     this.minecraft.tell(() -> this.minecraft.setScreen(this));
                  }

                  return null;
               }
            );
      }
   }

   private void removeTempDataPackDir() {
      if (this.tempDataPackDir != null) {
         try (Stream<Path> var1 = Files.walk(this.tempDataPackDir)) {
            var1.sorted(Comparator.reverseOrder()).forEach(var0 -> {
               try {
                  Files.delete(var0);
               } catch (IOException var2) {
                  LOGGER.warn("Failed to remove temporary file {}", var0, var2);
               }
            });
         } catch (IOException var6) {
            LOGGER.warn("Failed to list temporary dir {}", this.tempDataPackDir);
         }

         this.tempDataPackDir = null;
      }
   }

   private static void copyBetweenDirs(Path var0, Path var1, Path var2) {
      try {
         Util.copyBetweenDirs(var0, var1, var2);
      } catch (IOException var4) {
         LOGGER.warn("Failed to copy datapack file from {} to {}", var2, var1);
         throw new CreateWorldScreen.OperationFailedException(var4);
      }
   }

   private boolean copyTempDataPackDirToNewWorld() {
      if (this.tempDataPackDir != null) {
         try (
            LevelStorageSource.LevelStorageAccess var1 = this.minecraft.getLevelSource().createAccess(this.resultFolder);
            Stream<Path> var2 = Files.walk(this.tempDataPackDir);
         ) {
            Path var3 = var1.getLevelPath(LevelResource.DATAPACK_DIR);
            Files.createDirectories(var3);
            var2.filter(var1x -> !var1x.equals(this.tempDataPackDir)).forEach(var2x -> copyBetweenDirs(this.tempDataPackDir, var3, var2x));
         } catch (CreateWorldScreen.OperationFailedException | IOException var9) {
            LOGGER.warn("Failed to copy datapacks to world {}", this.resultFolder, var9);
            SystemToast.onPackCopyFailure(this.minecraft, this.resultFolder);
            this.popScreen();
            return false;
         }
      }

      return true;
   }

   @Nullable
   public static Path createTempDataPackDirFromExistingWorld(Path var0, Minecraft var1) {
      MutableObject var2 = new MutableObject();

      try (Stream<Path> var3 = Files.walk(var0)) {
         var3.filter(var1x -> !var1x.equals(var0)).forEach(var2x -> {
            Path var3x = (Path)var2.getValue();
            if (var3x == null) {
               try {
                  var3x = Files.createTempDirectory("mcworld-");
               } catch (IOException var5) {
                  LOGGER.warn("Failed to create temporary dir");
                  throw new CreateWorldScreen.OperationFailedException(var5);
               }

               var2.setValue(var3x);
            }

            copyBetweenDirs(var0, var3x, var2x);
         });
      } catch (CreateWorldScreen.OperationFailedException | IOException var8) {
         LOGGER.warn("Failed to copy datapacks from world {}", var0, var8);
         SystemToast.onPackCopyFailure(var1, var0.toString());
         return null;
      }

      return (Path)var2.getValue();
   }

   @Nullable
   private Pair<File, PackRepository> getDataPackSelectionSettings() {
      Path var1 = this.getTempDataPackDir();
      if (var1 != null) {
         File var2 = var1.toFile();
         if (this.tempDataPackRepository == null) {
            this.tempDataPackRepository = new PackRepository(
               PackType.SERVER_DATA, new ServerPacksSource(), new FolderRepositorySource(var2, PackSource.DEFAULT)
            );
            this.tempDataPackRepository.reload();
         }

         this.tempDataPackRepository.setSelected(this.dataPacks.getEnabled());
         return Pair.of(var2, this.tempDataPackRepository);
      } else {
         return null;
      }
   }

   static class OperationFailedException extends RuntimeException {
      public OperationFailedException(Throwable var1) {
         super(var1);
      }
   }

   static enum SelectedGameMode {
      SURVIVAL("survival", GameType.SURVIVAL),
      HARDCORE("hardcore", GameType.SURVIVAL),
      CREATIVE("creative", GameType.CREATIVE),
      DEBUG("spectator", GameType.SPECTATOR);

      final String name;
      final GameType gameType;
      private final Component displayName;

      private SelectedGameMode(String var3, GameType var4) {
         this.name = var3;
         this.gameType = var4;
         this.displayName = new TranslatableComponent("selectWorld.gameMode." + var3);
      }

      public Component getDisplayName() {
         return this.displayName;
      }
   }
}
