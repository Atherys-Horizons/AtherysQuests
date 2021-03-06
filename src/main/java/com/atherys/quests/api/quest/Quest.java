package com.atherys.quests.api.quest;

import com.atherys.quests.api.base.Observer;
import com.atherys.quests.api.base.Prototype;
import com.atherys.quests.api.objective.Objective;
import com.atherys.quests.api.quest.modifiers.DeliveryComponent;
import com.atherys.quests.api.quest.modifiers.RepeatableComponent;
import com.atherys.quests.api.quest.modifiers.TimeComponent;
import com.atherys.quests.api.quester.Quester;
import com.atherys.quests.api.requirement.Requirement;
import com.atherys.quests.api.reward.Reward;
import com.atherys.quests.views.QuestView;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * Common interface for all Quest classes. <br>
 * A Quest represents a series of tasks ( {@link Objective}s ), which once completed will reward the player with {@link Reward}(s).
 * A Quest may optionally contain {@link Requirement}s which the player must fulfill prior to starting work on the objectives.
 *
 * @param <T>
 */
public interface Quest<T extends Quest> extends Prototype<Quest<T>>, Observer<Event> {

    /**
     * @return The unique String ID of this Quest
     */
    String getId();

    /**
     * @return The formatted name of this Quest
     */
    Text getName();

    /**
     * @return The formatted description of this Quest
     */
    Text getDescription();

    /**
     * @return The List of {@link Requirement}s this Quest will check the {@link Quester} for
     */
    List<Requirement> getRequirements();

    /**
     * @return The List of {@link Objective}s this Quest requires the {@link Quester} to complete prior to being eligible for reward.
     */
    List<Objective> getObjectives();

    /**
     * @return The List of {@link Reward}s this Quest will award to the {@link Quester} once all {@link Objective}s have been fulfilled.
     */
    List<Reward> getRewards();

    /**
     * Checks whether or not the SimpleQuester meets the requirements of this Quest.
     *
     * @param quester The SimpleQuester to be checked
     * @return Whether or not the SimpleQuester meets the requirements
     */
    boolean meetsRequirements(Quester quester);

    /**
     * Notifies the {@link Quest} of any {@link Event} relevant to the given {@link Quester}
     *
     * @param event   The event which was triggered
     * @param quester The quester responsible for triggering it
     */
    void notify(Event event, Quester quester);

    /**
     * Awards the SimpleQuester for having completed all Objectives of this Quest.
     *
     * @param quester The quester to be awarded
     */
    void award(Quester quester);

    /**
     * @return Whether or not this Quest has been started, i.e. if any progress has been made on it's completion.
     */
    boolean isStarted();

    /**
     * @return Whether or not this Quest has been completed.
     */
    boolean isComplete();

    /**
     * @return The version of this Quest.
     */
    int getVersion();

    /**
     * @return The deliverable component. If present, the quest is deliverable.
     */
    Optional<DeliveryComponent> getDeliveryComponent();

    /**
     * Makes the quest deliverable.
     */
    void makeDeliverable(DeliveryComponent deliveryComponent);

    /**
     * @return The time component. If present, the quest is timed.
     */
    Optional<TimeComponent> getTimedComponent();

    /**
     * Makes the quest timed.
     */
    void makeTimed(TimeComponent timeableComponent);

    /**
     * @return The repeatable component. If present, this quest can be repeated.
     */
    Optional<RepeatableComponent> getRepeatComponent();

    /**
     * Makes the quest repeatable.
     */
    void makeRepeatable(RepeatableComponent repeatableComponent);

    QuestView createView();
}
