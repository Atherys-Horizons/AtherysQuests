alter table atherysquests.attempted_quest
add column if not exists attemptedBy uuid not null;