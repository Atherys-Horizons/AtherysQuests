package com.atherys.quests.persistence;

import com.atherys.core.db.CachedHibernateRepository;
import com.atherys.quests.entity.SimpleQuester;
import com.google.inject.Singleton;

import javax.persistence.Query;
import java.util.UUID;

@Singleton
public class QuesterRepository extends CachedHibernateRepository<SimpleQuester, UUID> {
    QuesterRepository() {
        super(SimpleQuester.class);
    }

    public void fetchAndCachePlayerCharacter(UUID playerUUID) {
        querySingleAsync(
                "SELECT q FROM SimpleQuester q WHERE q.id = :uuid",
                SimpleQuester.class,
                (Query q) -> {
                    q.setParameter("uuid", playerUUID);
                },
                (result) -> {
                    if (result.isPresent()) {
                        cache.set(playerUUID, result.get());
                    } else {
                        cache.removeById(playerUUID);
                    }
                }
        );
    }
}
