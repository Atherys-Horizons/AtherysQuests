package com.atherys.quests.facade;

import com.atherys.core.utils.Question;
import com.atherys.quests.api.exception.QuestCommandExceptions;
import com.atherys.quests.api.exception.QuestRequirementsException;
import com.atherys.quests.api.quest.Quest;
import com.atherys.quests.api.quest.modifiers.TimeComponent;
import com.atherys.quests.api.quester.Quester;
import com.atherys.quests.entity.SimpleQuester;
import com.atherys.quests.service.*;
import com.atherys.quests.views.TakeQuestView;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.text.BookView;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.spongepowered.api.text.format.TextColors.DARK_GREEN;
import static org.spongepowered.api.text.format.TextColors.DARK_RED;
import static org.spongepowered.api.text.format.TextStyles.BOLD;
import static org.spongepowered.api.text.format.TextStyles.STRIKETHROUGH;

@Singleton
public class QuesterFacade {

    @Inject
    QuesterService questerService;

    @Inject
    QuestService questService;

    @Inject
    QuestLocationService questLocationService;

    @Inject
    QuestMessagingService questMsg;

    @Inject
    TimedQuestService timedQuestService;

    QuesterFacade() {
    }

    public <T extends Quest> boolean pickupQuest(Player player, final Quest<T> quest) {
        Quest<T> copy = quest.copy();
        Quester quester = questerService.getQuester(player);

        if (quest.getTimedComponent().isPresent() && quester.getTimedQuest().isPresent()) {
            questMsg.error(quester, "You are already doing a timed quest!");
            return false;
        }

        try {
            boolean result = questerService.pickupQuest(quester, copy);

            if (result) {
                questMsg.info(quester, "You have started the quest \"", copy.getName(), "\"");
            } else {
                questMsg.error(quester, "You are either already doing this quest, or have done it before in the past.");
            }

            return result;
        } catch (QuestRequirementsException e) {
            Text.Builder reqText = Text.builder();
            reqText.append(Text.of(QuestMessagingService.MSG_PREFIX, " You do not meet the requirements for this quest."));
            reqText.append(quest.createView().getFormattedRequirements());

            questMsg.noformat(quester, reqText.build());

            return false;
        }
    }

    public <T extends Quest> boolean turnInQuest(Player player, Quest<T> quest) {
        Quester quester = questerService.getQuester(player);

        boolean result = questerService.turnInQuest(quester, quest);

        if (result) questMsg.info(quester, "You have turned in the quest \"", quest.getName(), "\"");

        return result;
    }

    public void onPlayerMoveToQuestRadius(Location<World> fromLocation, Location<World> toLocation, Player player) {
        if (questLocationService.getByRadius(fromLocation).isPresent()) return;

        questLocationService.getByRadius(toLocation).ifPresent(questLocation -> {
            Quest quest = questService.getQuest(questLocation.getQuestId()).get();

            if (questerService.getQuester(player).hasQuest(quest)) return;

            Question question = Question.of(Text.of("You have found the quest \"", quest.getName(), "\", would you like to take it?"))
                    .addAnswer(Question.Answer.of(Text.of(BOLD, DARK_GREEN, "Yes"), quester -> {
                        if (questLocationService.checkContain(questLocation, player.getLocation())) {
                            new TakeQuestView(quest).show(quester);
                        } else {
                            questMsg.error(quester, "You have left the quest area.");
                        }
                    }))
                    .addAnswer(Question.Answer.of(Text.of(BOLD, DARK_RED, "No"), quester -> {
                        questMsg.error(quester, "You have declined the quest \"", quest.getName(), "\".");
                    }))
                    .build();

            question.pollChat(player);
        });
    }

    public void showQuestLog(Player player) {
        Quester quester = questerService.getQuester(player);

        BookView.Builder log = BookView.builder();

        List<Text> pages = new ArrayList<>();
        Text.Builder lastPage = Text.builder();

        lastPage.append(Text.of("Quest Log:\n"));

        int i = 1;
        for (Quest quest : quester.getOngoingQuests()) {
            Text.Builder questView = Text.builder();
            questView.append(Text.of("[", i, "] "));
            if (quest.isComplete()) {
                questView.append(Text.of(STRIKETHROUGH, quest.getName(), TextStyles.NONE));
            } else {
                questView.append(Text.of(quest.getName()));
            }
            questView.onHover(TextActions.showText(Text.of("Click to view more details.")));
            questView.onClick(TextActions.executeCallback(src -> quest.createView().show(player)));

            if (i % 7 == 0) {
                pages.add(lastPage.build());
                lastPage = Text.builder();
            } else {
                lastPage.append(Text.of(questView.build(), "\n"));
            }

            i++;
        }

        pages.add(lastPage.build());

        log.addPages(pages);

        player.sendBookView(log.build());
    }

    /**
     * Removes a quest from the player.
     * @param finished whether to also try removing from the player's finished quest.
     */
    public void removeQuestFromPlayer(CommandSource source, Player player, String questId, boolean finished) throws CommandException {
        Quester quester = questerService.getQuester(player);
        Optional<Quest> quest = questService.getQuest(questId);

        if (!quest.isPresent()) {
            throw QuestCommandExceptions.invalidQuestId();
        }

        boolean removed = quester.removeQuest(quest.get());
        if (removed || (finished && quester.removeAttemptedQuest(questId))) {
            questMsg.info(source, "Successfully removed quest.");
        } else {
            questMsg.error(source, "Quest not found on player.");
        }
    }

    public boolean validatePlayerObject(Player player) {
        if (player == null) {
            throw new RuntimeException("Player object must not be null to validate");
        }

        if (!player.isOnline()) {
            throw new RuntimeException("Player must be online to validate");
        }

        return !player.isRemoved() && player.isLoaded();
    }

    public void notifyAndUpdateCachedPlayer(Event event, Player player) {
        // If the player object is invalid, get a new Player reference from the server and re-call the method
        if (!validatePlayerObject(player)) {
            player = Sponge.getServer().getPlayer(player.getUniqueId()).orElse(null);
            notifyAndUpdateCachedPlayer(event, player);
        }

        SimpleQuester quester = questerService.getQuester(player);
        quester.setCachedPlayer(player);

        questerService.notify(event, quester);
    }

    public void updateCachedPlayer(Player player) {
        // If the player object is invalid, get a new Player reference from the server and re-call the method
        if (!validatePlayerObject(player)) {
            player = Sponge.getServer().getPlayer(player.getUniqueId()).orElse(null);
            updateCachedPlayer(player);
        }

        SimpleQuester quester = questerService.getQuester(player);
        quester.setCachedPlayer(player);
    }

    public void onStartTimedQuest(Quester quester, Quest<?> quest) {
        quester.setTimedQuest(quest);
        quest.getTimedComponent().get().startTiming();
    }

    public void onCompleteTimedQuest(Quester quester) {
        timedQuestService.stopDisplayingTimer(quester);
        quester.removeTimedQuest();
    }

    public void resetTimedQuestOnLogin(Player player) {
        questerService.getQuester(player).getOngoingQuests().forEach(quest -> {
            quest.getTimedComponent().ifPresent(timedComponent -> ((TimeComponent) timedComponent).startTiming());
        });
    }

    public void storePlayerData(Player player) {
        questerService.storeCachedPlayerData(questerService.getOrCreateQuester(player.getUniqueId()));
    }

    public void fetchPlayerData(Player player) {
        questerService.fetchAndCachePlayerData(player.getUniqueId());
    }

}
