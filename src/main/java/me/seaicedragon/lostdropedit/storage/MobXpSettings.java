package me.seaicedragon.lostdropedit.storage;

/**
 * Per-mob experience reward settings. XP is dropped as vanilla experience orbs at
 * the mob's death location when the roll passes {@code xpChancePerMillion}.
 */
public record MobXpSettings(int xpMin, int xpMax, int xpChancePerMillion) {

    public boolean grantsXp() {
        return xpMax > 0 && xpChancePerMillion > 0;
    }

    public MobXpSettings withXpMin(int updatedMin) {
        return new MobXpSettings(updatedMin, xpMax, xpChancePerMillion);
    }

    public MobXpSettings withXpMax(int updatedMax) {
        return new MobXpSettings(xpMin, updatedMax, xpChancePerMillion);
    }

    public MobXpSettings withXpChancePerMillion(int updatedChance) {
        return new MobXpSettings(xpMin, xpMax, updatedChance);
    }
}
