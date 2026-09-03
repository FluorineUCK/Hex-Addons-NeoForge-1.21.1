package org.eu.net.pool.hexic.ducks;

/**
 * Carries the extra material mode from OpEdifySapling.execute into Hex
 * Casting's private rendered-spell implementation.
 */
public interface EdifySpellDuck {
    void hexic$setEdifyMode(int mode);

    int hexic$getEdifyMode();
}
