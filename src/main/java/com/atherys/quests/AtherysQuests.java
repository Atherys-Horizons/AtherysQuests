package com.atherys.quests;

import com.atherys.core.command.CommandService;
import com.atherys.core.event.AtherysHibernateConfigurationEvent;
import com.atherys.core.event.AtherysHibernateInitializedEvent;
import com.atherys.quests.api.script.DialogScriptService;
import com.atherys.quests.api.script.QuestScriptService;
import com.atherys.quests.command.dialog.DialogMasterCommand;
import com.atherys.quests.command.dialog.GetUUIDCommand;
import com.atherys.quests.command.npc.NpcMasterCommand;
import com.atherys.quests.command.quest.QuestMasterCommand;
import com.atherys.quests.data.DialogData;
import com.atherys.quests.data.QuestData;
import com.atherys.quests.entity.QuestLocation;
import com.atherys.quests.entity.SimpleAttemptedQuest;
import com.atherys.quests.entity.SimpleQuester;
import com.atherys.quests.event.dialog.DialogRegistrationEvent;
import com.atherys.quests.event.quest.QuestRegistrationEvent;
import com.atherys.quests.facade.DialogFacade;
import com.atherys.quests.facade.NpcFacade;
import com.atherys.quests.facade.QuestFacade;
import com.atherys.quests.facade.QuesterFacade;
import com.atherys.quests.gson.AtherysQuestsRegistry;
import com.atherys.quests.listener.*;
import com.atherys.quests.persistence.AttemptedQuestRepository;
import com.atherys.quests.persistence.QuestLocationRepository;
import com.atherys.quests.persistence.QuesterRepository;
import com.atherys.quests.script.lib.QuestExtension;
import com.atherys.quests.service.*;
import com.atherys.script.ScriptConfig;
import com.atherys.script.groovy.GroovyLibrary;
import com.atherys.script.js.JavaScriptLibrary;
import com.atherys.script.library.event.EventHandlerFunction;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataRegistration;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.item.inventory.InventoryArchetype;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.economy.EconomyService;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import static com.atherys.quests.AtherysQuests.*;

@Plugin(
        id = ID,
        name = NAME,
        description = DESCRIPTION,
        version = VERSION,
        dependencies = {
                @Dependency(id = "atheryscore"),
                @Dependency(id = "atherysscript")
        }
)
public class AtherysQuests {
    public static final String ID = "atherysquests";
    public static final String NAME = "A'therys Quests";
    public static final String DESCRIPTION = "A Quest plugin written for the A'therys Horizons server.";
    public static final String VERSION = "%PLUGIN_VERSION%";

    private static AtherysQuests instance;

    private static boolean init = false;

    @Inject
    PluginContainer container;

    @Inject
    Logger logger;

    @Inject
    Injector injector;
    private Components components;
    private Gson gson;
    private Injector questsInjector;

    public static AtherysQuests getInstance() {
        return instance;
    }

    public static QuestsConfig getConfig() {
        return getInstance().components.config;
    }

    private void init() {
        instance = this;

        components = new Components();

        questsInjector = injector.createChildInjector(new AtherysQuestsModule());
        questsInjector.injectMembers(components);

        components.config.init();
        String scriptType = components.config.SCRIPT_TYPE;

        if (!(   scriptType.equals(ScriptConfig.JAVASCRIPT_TYPE)
              || scriptType.equals(ScriptConfig.GROOVY_TYPE))) {
            logger.error("Script type {} is not supported!", scriptType);
            return;
        }

        init = true;
    }

    private void start() {
        // Create Gson instance
        gson = getGson();

        // Cache entities
        getQuestLocationRepository().initCache();
        getQuesterRepository().initCache();
        getAttemptedQuestRepository().initCache();

        // Register listeners
        Sponge.getEventManager().registerListeners(this, components.gsonListener);
        Sponge.getEventManager().registerListeners(this, components.entityListener);
        Sponge.getEventManager().registerListeners(this, components.inventoryListener);
        Sponge.getEventManager().registerListeners(this, components.internalListener);
        Sponge.getEventManager().registerListeners(this, components.questListener);
        Sponge.getEventManager().registerListeners(this, components.playerListener);

        if (components.config.SCRIPT_TYPE.equals(ScriptConfig.GROOVY_TYPE)) {
            GroovyLibrary.getInstance().extendWith(QuestExtension.getInstance());
        } else if (components.config.SCRIPT_TYPE.equals(ScriptConfig.JAVASCRIPT_TYPE)) {
            JavaScriptLibrary.getInstance().extendWith(QuestExtension.getInstance());
        }

        // Load scripts
        try {
            getQuestScriptService().registerFolder(Paths.get("config/", ID, "/quests"));
            getDialogScriptService().registerFolder(Paths.get("config/", ID, "/dialogs"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Trigger Events
        Sponge.getEventManager().post(new QuestRegistrationEvent(getQuestService()));
        Sponge.getEventManager().post(new DialogRegistrationEvent(getDialogService()));

        // Start emitting quest location particles
        getParticleService().startEmitting();

        // Register commands
        try {
            CommandService.getInstance().register(new DialogMasterCommand(), this);
            CommandService.getInstance().register(new QuestMasterCommand(), this);
            CommandService.getInstance().register(new GetUUIDCommand(), this);
            CommandService.getInstance().register(new NpcMasterCommand(), this);
        } catch (CommandService.AnnotatedCommandException e) {
            e.printStackTrace();
        }
    }

    private void stop() {
        getQuesterRepository().flushCache();
        getQuestLocationRepository().flushCache();
        getAttemptedQuestRepository().flushCache();
        components.config.save();
    }

    private void reload() {
        getDialogScriptService().reloadScripts();
        getQuestScriptService().reloadScripts();
    }

    @Listener
    public void preInit(GamePreInitializationEvent event) {
        QuestKeys.DIALOG_DATA_REGISTRATION = DataRegistration.builder()
                .dataClass(DialogData.class)
                .immutableClass(DialogData.Immutable.class)
                .builder(new DialogData.Builder())
                .dataName("Dialog")
                .manipulatorId("dialog")
                .buildAndRegister(this.container);

        QuestKeys.QUEST_DATA_REGISTRATION = DataRegistration.<QuestData, QuestData.Immutable>builder()
                .dataClass(QuestData.class)
                .immutableClass(QuestData.Immutable.class)
                .builder(new QuestData.Builder())
                .dataName("Quest")
                .manipulatorId("Quest")
                .buildAndRegister(this.container);
    }

    @Listener
    public void onInit(AtherysHibernateInitializedEvent event) {
        init();
    }

    @Listener(order = Order.LATE)
    public void onStart(GameStartedServerEvent event) {
        if (init) start();
    }

    @Listener
    public void onReload(GameReloadEvent event) {
        if (init) reload();
    }

    @Listener
    public void onStop(GameStoppingServerEvent event) {
        if (init) stop();
    }

    @Listener
    public void onHibernateConfiguration(AtherysHibernateConfigurationEvent event) {
        event.registerEntity(SimpleQuester.class);
        event.registerEntity(QuestLocation.class);
        event.registerEntity(SimpleAttemptedQuest.class);
    }

    public Optional<EconomyService> getEconomyService() {
        return Sponge.getServiceManager().provide(EconomyService.class);
    }

    public AtherysQuestsRegistry getAtherysQuestsRegistry() {
        return components.atherysQuestsRegistry;
    }

    public QuesterRepository getQuesterRepository() {
        return components.questerRepository;
    }

    public QuestLocationRepository getQuestLocationRepository() {
        return components.questLocationRepository;
    }

    public AttemptedQuestRepository getAttemptedQuestRepository() {
        return components.attemptedQuestRepository;
    }

    public QuestMessagingService getQuestMessagingService() {
        return components.questMessagingService;
    }

    public InventoryService getInventoryService() {
        return components.inventoryService;
    }

    public QuestService getQuestService() {
        return components.questService;
    }

    public DialogService getDialogService() {
        return components.dialogService;
    }

    public QuestLocationService getQuestLocationService() {
        return components.questLocationService;
    }

    public QuesterService getQuesterService() {
        return components.questerService;
    }

    public QuestAttachmentService getQuestAttachmentService() {
        return components.questAttachmentService;
    }

    public DialogAttachmentService getDialogAttachmentService() {
        return components.dialogAttachmentService;
    }

    public ParticleService getParticleService() {
        return components.particleService;
    }

    public DialogScriptService getDialogScriptService() {
        return components.dialogScriptService;
    }

    public QuestScriptService getQuestScriptService() {
        return components.questScriptService;
    }

    public DialogFacade getDialogFacade() {
        return components.dialogFacade;
    }

    public QuestFacade getQuestFacade() {
        return components.questFacade;
    }

    public QuesterFacade getQuesterFacade() {
        return components.questerFacade;
    }

    public NpcFacade getNpcFacade() {
        return components.npcFacade;
    }

    public Gson getGson() {
        if (components.atherysQuestsRegistry == null) return null;
        if (gson == null) gson = components.atherysQuestsRegistry.getGson();
        return gson;
    }

    public Logger getLogger() {
        return logger;
    }

    private class Components {

        @Inject
        QuestsConfig config;

        @Inject
        AtherysQuestsRegistry atherysQuestsRegistry;

        @Inject
        QuesterRepository questerRepository;

        @Inject
        QuestLocationRepository questLocationRepository;

        @Inject
        AttemptedQuestRepository attemptedQuestRepository;

        @Inject
        QuestMessagingService questMessagingService;

        @Inject
        InventoryService inventoryService;

        @Inject
        QuestService questService;

        @Inject
        DialogService dialogService;

        @Inject
        QuestLocationService questLocationService;

        @Inject
        QuesterService questerService;

        @Inject
        QuestAttachmentService questAttachmentService;

        @Inject
        DialogAttachmentService dialogAttachmentService;

        @Inject
        ParticleService particleService;

        @Inject
        DialogScriptService dialogScriptService;

        @Inject
        QuestScriptService questScriptService;

        @Inject
        DialogFacade dialogFacade;

        @Inject
        QuestFacade questFacade;

        @Inject
        QuesterFacade questerFacade;

        @Inject
        NpcFacade npcFacade;

        @Inject
        EntityListener entityListener;

        @Inject
        GsonListener gsonListener;

        @Inject
        InventoryListener inventoryListener;

        @Inject
        InternalListener internalListener;

        @Inject
        QuestListener questListener;

        @Inject
        PlayerListener playerListener;
    }
}
