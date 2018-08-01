package com.atherys.quests.managers;

import com.atherys.core.database.api.DBObject;
import com.atherys.core.database.mongo.AbstractMongoDatabaseManager;
import com.atherys.quests.AtherysQuests;
import com.atherys.quests.api.quest.Quest;
import com.atherys.quests.api.quest.QuestLocationType;
import com.atherys.quests.db.QuestsDatabase;
import org.bson.Document;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Optional;
import java.util.UUID;

public final class LocationManager extends AbstractMongoDatabaseManager<LocationManager.QuestLocation> {

    private static LocationManager instance = new LocationManager();

    private LocationManager() {
        super(AtherysQuests.getInstance().getLogger(), QuestsDatabase.getInstance(), QuestLocation.class);
    }

    public static LocationManager getInstance() {
        return instance;
    }

    public Optional<QuestLocation> getByLocation(Location<World> location) {
        for (QuestLocation ql : getCache().values()) {
            if (ql.contains(location)) {
                return Optional.of(ql);
            }
        }
        return Optional.empty();
    }

    public Optional<QuestLocation> getByBlock(Location<World> location) {
        for (QuestLocation ql : getCache().values()) {
            if (ql.sameBlockAs(location)){
                return Optional.of(ql);
            }
        }
        return Optional.empty();
    }

    public void saveAll() {
        saveAll(getCache().values());
    }

    public boolean addQuestLocation(Location<World> location, String questId, double radius, QuestLocationType type) {
        Optional<QuestLocation> questLocation = AtherysQuests.getQuestService().getQuest(questId).map(quest -> {
           return  new QuestLocation(location, quest, radius, type);
        });

        if (questLocation.isPresent()) {
            QuestLocation questLoc = questLocation.get();
            if (questLoc.getType() == QuestLocationType.RADIUS) {
                for (QuestLocation ql : this.getCache().values()) {
                    if (questLoc.overlaps(ql)) return false;
                }
            } else if(questLoc.getType() == QuestLocationType.BLOCK){
                for (QuestLocation ql: this.getCache().values()) {
                    if (questLoc.sameBlockAs(ql.getLocation())) return false;
                }
            }

            this.save(questLoc);

            return true;
        } else return false;
    }

    @Override
    protected Optional<Document> toDocument(QuestLocation questLocation) {
        Document document = new Document();

        document.append("location", AtherysQuests.getGson().toJson(questLocation.getLocation()));
        document.append("questId", questLocation.getQuestId());
        document.append("radius", questLocation.getRadius());
        document.append("type", questLocation.getType().name());

        return Optional.of(document);
    }

    @Override
    protected Optional<QuestLocation> fromDocument(Document document) {
        Location location = AtherysQuests.getGson().fromJson((String) document.get("location"), Location.class);
        Optional<Quest> quest = AtherysQuests.getQuestService().getQuest(document.getString("questId"));
        QuestLocationType type = QuestLocationType.valueOf(document.getString("type"));

        return quest.map(quest1 -> new QuestLocation(location, quest1, document.getDouble("radius"), type));
    }

    public static class QuestLocation implements DBObject {

        private UUID uuid;

        private Location<World> location;
        private double radius;
        private QuestLocationType type;

        private String questId;

        private boolean overlaps(QuestLocation questLocation){
            return (this.location.getPosition().distanceSquared(questLocation.location.getPosition())
                    < Math.pow(this.radius + questLocation.radius, 2));
        }

        private boolean sameBlockAs(Location<World> location){
            return (this.location.getExtent().equals(location.getExtent()) &&
                    this.location.getBlockPosition().equals(location.getBlockPosition()));
        }

        public boolean contains(Location<World> loc){
            if(loc.getExtent().equals(location.getExtent())){
                return(loc.getPosition().distanceSquared(location.getPosition()) <= Math.pow(radius, 2));
            } else return false;
        }

        private QuestLocation(Location<World> location, Quest quest, double radius, QuestLocationType type) {
            this.uuid = UUID.randomUUID();
            this.location = location;
            this.radius = radius;
            this.questId = quest.getId();
            this.type = type;
        }

        public double getRadius() {
            return radius;
        }

        public Location getLocation() {
            return location;
        }

        public String getQuestId() {
            return questId;
        }

        public QuestLocationType getType() {
            return type;
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }
    }
}