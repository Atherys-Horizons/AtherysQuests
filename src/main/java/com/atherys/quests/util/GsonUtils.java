package com.atherys.quests.util;

import com.atherys.core.utils.RuntimeTypeAdapterFactory;
import com.atherys.quests.api.objective.Objective;
import com.atherys.quests.api.quest.Quest;
import com.atherys.quests.api.requirement.Requirement;
import com.atherys.quests.api.reward.Reward;
import com.atherys.quests.events.AtherysQuestsGsonBuildEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.spongepowered.api.Sponge;

public final class GsonUtils {

    private static RuntimeTypeAdapterFactory<Quest> questRuntimeTypeAdapterFactory = RuntimeTypeAdapterFactory.of(Quest.class, "questType");
    private static RuntimeTypeAdapterFactory<Objective> objectiveTypeAdapterFactory = RuntimeTypeAdapterFactory.of(Objective.class, "objectiveType");
    private static RuntimeTypeAdapterFactory<Reward> rewardRuntimeTypeAdapterFactory = RuntimeTypeAdapterFactory.of(Reward.class, "rewardType");
    private static RuntimeTypeAdapterFactory<Requirement> requirementRuntimeTypeAdapterFactory = RuntimeTypeAdapterFactory.of(Requirement.class, "requirementType");

    public static RuntimeTypeAdapterFactory<Requirement> getRequirementRuntimeTypeAdapterFactory() {
        return requirementRuntimeTypeAdapterFactory;
    }

    public static RuntimeTypeAdapterFactory<Objective> getObjectiveTypeAdapterFactory() {
        return objectiveTypeAdapterFactory;
    }

    public static RuntimeTypeAdapterFactory<Reward> getRewardRuntimeTypeAdapterFactory() {
        return rewardRuntimeTypeAdapterFactory;
    }

    public static RuntimeTypeAdapterFactory<Quest> getQuestRuntimeTypeAdapterFactory() {
        return questRuntimeTypeAdapterFactory;
    }

    public static <T extends Quest> void registerQuestType(Class<T> quest) {
        questRuntimeTypeAdapterFactory.registerSubtype(quest);
    }

    public static <T extends Requirement> void registerRequirementType(Class<T> requirement) {
        requirementRuntimeTypeAdapterFactory.registerSubtype(requirement);
    }

    public static <T extends Objective> void registerObjectiveType(Class<T> objective) {
        objectiveTypeAdapterFactory.registerSubtype(objective);
    }

    public static <T extends Reward> void registerRewardType(Class<T> reward) {
        rewardRuntimeTypeAdapterFactory.registerSubtype(reward);
    }

    public static Gson getGson() {

        GsonBuilder builder = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation();

        AtherysQuestsGsonBuildEvent event = new AtherysQuestsGsonBuildEvent(builder);
        Sponge.getEventManager().post(event);

        builder.registerTypeAdapterFactory(requirementRuntimeTypeAdapterFactory)
                .registerTypeAdapterFactory(objectiveTypeAdapterFactory)
                .registerTypeAdapterFactory(rewardRuntimeTypeAdapterFactory);

        return builder.create();
    }
}
