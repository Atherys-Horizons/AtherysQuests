package com.atherys.quests;

import com.atherys.core.command.CommandService;
import com.atherys.core.event.AtherysHibernateConfigurationEvent;
import com.atherys.core.event.AtherysHibernateInitializedEvent;
import com.atherys.quests.api.script.DialogScriptService;
import com.atherys.quests.api.script.QuestScriptService;
import com.atherys.quests.command.dialog.DialogMasterCommand;
import com.atherys.quests.command.dialog.GetUUIDCommand;
import com.atherys.quests.command.quest.QuestMasterCommand;
import com.atherys.quests.data.DialogData;
import com.atherys.quests.data.QuestData;
import com.atherys.quests.facade.DialogFacade;
import com.atherys.quests.facade.QuestFacade;
import com.atherys.quests.facade.QuesterFacade;
import com.atherys.quests.gson.AtherysQuestsRegistry;
import com.atherys.quests.listener.EntityListener;
import com.atherys.quests.listener.GsonListener;
import com.atherys.quests.listener.InventoryListener;
import com.atherys.quests.listener.MasterEventListener;
import com.atherys.quests.entity.QuestLocation;
import com.atherys.quests.entity.SimpleQuester;
import com.atherys.quests.persistence.QuestLocationRepository;
import com.atherys.quests.persistence.QuesterRepository;
import com.atherys.quests.script.lib.QuestExtension;
import com.atherys.quests.service.*;
import com.atherys.script.js.JavaScriptLibrary;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataRegistration;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.economy.EconomyService;

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
    public static final String DESCRIPTION = "A completedQuest plugin written for the A'therys Horizons server.";
    public static final String VERSION = "1.0.0b";

    private static AtherysQuests instance;

    private static boolean init = false;

    @Inject
    PluginContainer container;

    @Inject
    Logger logger;

    @Inject
    Injector injector;

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
        EntityListener entityListener;

        @Inject
        GsonListener gsonListener;

        @Inject
        InventoryListener inventoryListener;

        @Inject
        MasterEventListener masterEventListener;
    }

    private Components components;

    private Gson gson;

    private Injector questsInjector;

    private void init() {
        instance = this;

        components = new Components();

        questsInjector = injector.createChildInjector(new AtherysQuestsModule());
        questsInjector.injectMembers(components);

        components.config.init();

        init = true;
    }

    private void start() {

        gson = getGson();

        components.questLocationRepository.cacheAll();
        components.questerRepository.cacheAll();

        Sponge.getEventManager().registerListeners(this, components.gsonListener);
        Sponge.getEventManager().registerListeners(this, components.entityListener);
        Sponge.getEventManager().registerListeners(this, components.inventoryListener);
        Sponge.getEventManager().registerListeners(this, components.masterEventListener);

        JavaScriptLibrary.getInstance().extendWith(QuestExtension.getInstance());

//        dialogScriptService = SimpleDialogScriptService.getInstance();
//        questScriptService = SimpleQuestScriptService.getInstance();
//
//        try {
//            questScriptService.registerFolder(new File("config/" + ID + "/quests"));
//            dialogScriptService.registerFolder(new File("config/" + ID + "/dialogs"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        questService = QuestService.getInstance();
//        dialogService = DialogService.getInstance();
//
//        questAttachmentService= QuestAttachmentService.getInstance();
//        dialogAttachmentService = DialogAttachmentService.getInstance();

//        locationManager = LocationManager.getInstance();
//        particleEmitter = ParticleService.getInstance();

//        questerManager = QuesterManager.getInstance();

//        inventoryService = InventoryService.getInstance();

//        QuesterManager.getInstance().loadAll();
//        LocationManager.getInstance().loadAll();

        try {
            CommandService.getInstance().register(new DialogMasterCommand(), this);
            CommandService.getInstance().register(new QuestMasterCommand(), this);
            CommandService.getInstance().register(new GetUUIDCommand(), this);
        } catch (CommandService.AnnotatedCommandException e) {
            e.printStackTrace();
        }
//        particleEmitter.startEmitting();
    }

    private void stop() {
        components.questerRepository.flushCache();
        components.questLocationRepository.flushCache();
//        QuesterManager.getInstance().saveAll();
//        LocationManager.getInstance().saveAll();
    }

    private void reload() {
        // TODO
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
                .manipulatorId("completedQuest")
                .buildAndRegister(this.container);
    }

    @Listener
    public void onInit(AtherysHibernateInitializedEvent event) {
        init();
    }

    @Listener
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
    }

    public static AtherysQuests getInstance() {
        return instance;
    }

    public static QuestsConfig getConfig() {
        return getInstance().components.config;
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

    public Gson getGson() {
        if (components.atherysQuestsRegistry == null) return null;
        if (gson == null) gson = components.atherysQuestsRegistry.getGson();
        return gson;
    }

    public Logger getLogger() {
        return logger;
    }
}
