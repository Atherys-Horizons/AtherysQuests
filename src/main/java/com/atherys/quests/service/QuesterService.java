package com.atherys.quests.service;

import com.atherys.core.utils.UserUtils;
import com.atherys.quests.AtherysQuests;
import com.atherys.quests.api.base.Observer;
import com.atherys.quests.api.exception.QuestRequirementsException;
import com.atherys.quests.api.quest.AttemptedQuest;
import com.atherys.quests.api.quest.Quest;
import com.atherys.quests.api.quest.modifiers.RepeatableComponent;
import com.atherys.quests.api.quester.Quester;
import com.atherys.quests.entity.SimpleAttemptedQuest;
import com.atherys.quests.entity.SimpleQuester;
import com.atherys.quests.event.quest.QuestCompletedEvent;
import com.atherys.quests.event.quest.QuestTurnedInEvent;
import com.atherys.quests.persistence.QuesterRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Event;

import java.util.Optional;
import java.util.UUID;

@Singleton
public class QuesterService implements Observer<Event> {

    @Inject
    QuesterRepository repository;

    @Inject
    QuestMessagingService questMsg;

    @Inject
    QuestService questService;

    QuesterService() {
    }

    public SimpleQuester getOrCreateQuester(UUID uuid) {
        Optional<SimpleQuester> questerById = repository.findById(uuid);

        if (questerById.isPresent()) {
            return questerById.get();
        } else {
            SimpleQuester quester = new SimpleQuester();
            quester.setId(uuid);

            repository.saveOne(quester);

            return quester;
        }
    }

    public SimpleQuester getQuester(User user) {
        return getOrCreateQuester(user.getUniqueId());
    }

    public Player getCachedPlayer(Quester quester) {
        return ((SimpleQuester) quester).getCachedPlayer();
    }

    public Player getPlayer(Quester quester) {
        if (quester instanceof SimpleQuester) {
            return ((SimpleQuester) quester).getCachedPlayer();
        }

        return Sponge.getServer().getPlayer(quester.getUniqueId()).orElse(null);
    }

    @Override
    public void notify(Event event, Quester quester) {

        if (quester == null) {
            throw new IllegalStateException("Quester cannot be null. Possibly failed repository lookup.");
        }

        for (Quest quest : quester.getOngoingQuests()) {
            if (!quest.isComplete()) {

                quest.notify(event, quester);

                if (quest.isComplete()) {
                    questMsg.info(quester, "You have completed the quest \"", quest.getName(), "\". You may now turn it in.");

                    QuestCompletedEvent qsEvent = new QuestCompletedEvent(quest, quester);
                    Sponge.getEventManager().post(qsEvent);
                }
            }
        }
    }

    public <T extends Quest> boolean pickupQuest(Quester quester, Quest<T> quest) throws QuestRequirementsException {

        if (!quest.meetsRequirements(quester)) {
            throw new QuestRequirementsException("Quester does not meet quest requirements");
        }

        if (quester.hasQuest(quest)) {
            return false;
        }

        // If quest is timed and quester has timed quest, false
        if (quest.getTimedComponent().isPresent() && quester.getTimedQuest().isPresent()) {
            return false;
        }

        boolean repeatable = quest.getRepeatComponent().isPresent();
        // If quest is repeatable, check
        if (repeatable && !checkRepeatableQuest(quester, quest)) {
            return false;
        }

        AtherysQuests.getInstance().getLogger().info("Repeatable: {}", quest.getRepeatComponent().isPresent());

        // If the quest is repeatable, or if the quester has never done the quest
        if (repeatable || !questerHasTurnedInQuest(quester, quest.getId())) {
            quester.addQuest(quest);
            return true;
        }

        return false;
    }

    public <T extends Quest> boolean checkRepeatableQuest(Quester quester, Quest<T> quest) {
        RepeatableComponent component = quest.getRepeatComponent().get();
        int timesCompleted = quester.getAttemptedQuest(quest.getId()).map(AttemptedQuest::getTimesCompleted).orElse(0);
        if (component.getLimit() > 0 && timesCompleted == component.getLimit()) {
            return false;
        }

        long now = System.currentTimeMillis();
        // The latest timestamp from the quester, or 0 if they have not completed the quest
        long completionTime = quester.getAttemptedQuest(quest.getId()).map(AttemptedQuest::getTimestamp).orElse(0L);
        long cooldown = component.getCooldown().toMillis();

        return now > (completionTime + cooldown);
    }

    public <T extends Quest> boolean turnInQuest(Quester quester, Quest<T> quest) {
        if (questerHasCompletedQuest(quester, quest)) {

            quester.removeQuest(quest);

            addAttemptedQuest(quester, quest, true);

            quest.award(quester);

            QuestTurnedInEvent qsEvent = new QuestTurnedInEvent(quest, quester);
            Sponge.getEventManager().post(qsEvent);

            return true;
        } else {
            return false;
        }
    }

    public Optional<Quest> getQuesterQuest(Quester quester, String questId) {
        return quester.getOngoingQuests().stream()
                .filter(quest -> quest.getId().equals(questId))
                .findFirst();
    }

    public boolean questerHasTurnedInQuest(Quester quester, String questId) {
        return quester.getAttemptedQuest(questId)
                .map(attemptedQuest -> attemptedQuest.getTimesCompleted() > 0)
                .orElse(false);
    }

    /**
     * Checks if a given quest has been completed. This is different from if the quest has been turned in.
     * @return Whether the quest has been completed.
     */
    public boolean questerHasCompletedQuest(Quester quester, Quest quest) {
        if (quester.hasQuest(quest)) {
            return quester.getOngoingQuests().stream()
                    .anyMatch(q -> q.equals(quest) && q.isComplete());
        }
        return false;
    }

    public Optional<? extends User> getUser(Quester quester) {
        return UserUtils.getUser(quester.getUniqueId());
    }

    public void addAttemptedQuest(Quester quester, Quest quest, boolean completed) {
        AttemptedQuest attemptedQuest = quester.getAttemptedQuest(quest.getId()).orElse(new SimpleAttemptedQuest(quest.getId()));
        long timestamp = System.currentTimeMillis();

        attemptedQuest.setTimestamp(timestamp);

        if (completed) {
            // If player hasn't completed quest, set the first time
            if (attemptedQuest.getTimesCompleted() == 0) {
                attemptedQuest.setFirstTimestamp(timestamp);
            }
            attemptedQuest.setTimesCompleted(attemptedQuest.getTimesCompleted() + 1);
        }

        quester.addAttemptedQuest(quest.getId(), attemptedQuest);
    }

    public void storeCachedPlayerData(SimpleQuester quester) {
        repository.saveOneAsync(quester);
    }

    public void fetchAndCachePlayerData(UUID playerUUID) {
        repository.fetchAndCachePlayerCharacter(playerUUID);
    }
}