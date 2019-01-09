package com.atherys.quests.model;

import com.atherys.core.utils.UserUtils;
import com.atherys.core.views.Viewable;
import com.atherys.quests.AtherysQuests;
import com.atherys.quests.api.quest.Quest;
import com.atherys.quests.api.quester.Quester;
import com.atherys.quests.event.quest.QuestCompletedEvent;
import com.atherys.quests.event.quest.QuestTurnedInEvent;
import com.atherys.quests.service.QuestMessagingService;
import com.atherys.quests.views.QuestLog;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.text.Text;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Entity
public class SimpleQuester implements Quester, Viewable<QuestLog> {

    @Id
    private UUID player; // Retrieve player from this. 100% Reliable.

    private Player cachedPlayer; // Used for performance optimizations. When quick access to the player object is crucial.

    private Map<String, Quest> quests = new HashMap<>();

    private Map<String, Long> completedQuests = new HashMap<>();

    public SimpleQuester(UUID uuid) {
        this.player = uuid;
    }

    public SimpleQuester(Player player) {
        this.player = player.getUniqueId();
        this.cachedPlayer = player;
    }

    public void notify(Event event, Player player) {
        if (!this.player.equals(player.getUniqueId())) return;

        this.cachedPlayer = player;

        for (Quest quest : quests.values()) {
            if (!quest.isComplete()) {
                quest.notify(event, this);
                if (quest.isComplete()) {
                    AtherysQuests.getInstance().getQuestMessagingService().info(this, "You have completed the completedQuest \"", quest.getName(), "\". You may now turn it in.");

                    QuestCompletedEvent qsEvent = new QuestCompletedEvent(quest, this);
                    Sponge.getEventManager().post(qsEvent);
                }
            }
        }
    }

    public boolean pickupQuest(Quest quest) {
        if (!quest.meetsRequirements(this)) {
            Text.Builder reqText = Text.builder();
            reqText.append(Text.of(QuestMessagingService.MSG_PREFIX, " You do not meet the requirements for this completedQuest."));
            reqText.append(quest.createView().getFormattedRequirements());
            AtherysQuests.getInstance().getQuestMessagingService().noformat(this, reqText.build());

            return false;
        }

        if (!completedQuests.containsKey(quest.getId()) && !quests.containsKey(quest.getId())) {
            quests.put(quest.getId(), (Quest) quest.copy());
            AtherysQuests.getInstance().getQuestMessagingService().info(this, "You have started the completedQuest \"", quest.getName(), "\"");
            return true;
        } else {
            AtherysQuests.getInstance().getQuestMessagingService().error(this, "You are either already doing this completedQuest, or have done it before in the past.");
            return false;
        }
    }

    public void removeQuest(Quest quest) {
        quests.remove(quest.getId());
    }

    public void turnInQuest(Quest quest) {
        removeQuest(quest);
        completedQuests.put(quest.getId(), System.currentTimeMillis());

        quest.award(this);

        AtherysQuests.getInstance().getQuestMessagingService().info(this, "You have turned in the completedQuest \"", quest.getName(), "\"");

        QuestTurnedInEvent qsEvent = new QuestTurnedInEvent(quest, this);
        Sponge.getEventManager().post(qsEvent);
    }

    public Optional<? extends User> getUser() {
        return UserUtils.getUser(this.player);
    }

    @Nullable
    public Player getCachedPlayer() {
        return cachedPlayer;
    }

    public boolean hasQuestWithId(String id) {
        return quests.containsKey(id);
    }

    public boolean hasQuest(Quest quest) {
        return quests.containsKey(quest.getId());
    }

    public Map<String, Long> getCompletedQuests() {
        return completedQuests;
    }

    public Map<String, Quest> getQuests() {
        return quests;
    }

    public boolean hasCompletedQuest(String questId) {
        return completedQuests.containsKey(questId);
    }

    @Override
    public QuestLog createView() {
        return new QuestLog(this);
    }

    @Nonnull
    @Override
    public UUID getId() {
        return player;
    }

    @Override
    public Set<Quest> getFinishedQuests() {
        return null;
    }

    @Override
    public void addFinishedQuest(Quest quest) {

    }

    @Override
    public boolean hasFinishedQuest(Quest quest) {
        return false;
    }

    @Override
    public void removeFinishedQuest(Quest quest) {

    }

    @Override
    public Set<Quest> getOngoingQuests() {
        return null;
    }

    @Override
    public void addQuest(Quest quest) {

    }
}
