package gg.grounds.proxy.velocity.motd

/**
 * Where the network's MOTD is kept.
 *
 * An interface with one production implementation ([MotdConfigStore]) because the behaviour worth
 * testing in [MotdManager] is what it does when this fails — and a store that fails on demand is
 * the only way to write that test.
 */
interface MotdStore {

    /** The stored MOTD, or null when none is set. */
    fun read(): MotdDocument?

    /** Stores [document], replacing whatever was there. */
    fun write(document: MotdDocument, updatedBy: String)

    /** Removes the stored MOTD. Returns true when there was one to remove. */
    fun clear(deletedBy: String): Boolean
}
