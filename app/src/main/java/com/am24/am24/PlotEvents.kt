package com.am24.am24

/**
 * A deeper, branching set of plot events per AI.
 * Each event may have multiple prerequisites,
 * uses `canTrigger` for conditions,
 * and calls `applyEvent` to modify the ModelingState
 * while marking itself completed.
 */

sealed class PlotEvent(
    val description: String,
    val ai: AI,
    val prerequisites: List<PlotEvent> = emptyList()
) {
    abstract fun applyEvent(state: ModelingState): ModelingState

    open fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
        return true
    }

    // ---------------------------------------------------------
    // -------------------- RHEA EVENTS ------------------------
    // ---------------------------------------------------------

    object RheaCampaignStrategy : PlotEvent(
        description = "Help Rhea formulate her initial campaign strategy.",
        ai = AI.RHEA
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                careerProgress = (state.careerProgress + 10).coerceAtMost(100)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return true
        }
    }

    object RheaPartyManifesto : PlotEvent(
        description = "Draft a bold new Party Manifesto with Rhea.",
        ai = AI.RHEA,
        prerequisites = listOf(RheaCampaignStrategy)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                careerProgress = (state.careerProgress + 15).coerceAtMost(100),
                reputation = (state.reputation + 5).coerceAtMost(100)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.careerProgress >= 20 && state.money >= 0
        }
    }

    object RheaSabotageRival : PlotEvent(
        description = "Sabotage Rhea’s rival politician behind the scenes.",
        ai = AI.RHEA,
        prerequisites = listOf(RheaPartyManifesto)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation - 5).coerceAtLeast(0),
                money = (state.money - 20).coerceAtLeast(0)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.money >= 20 && messageCount >= 10
        }
    }

    object RheaCoalitionDeal : PlotEvent(
        description = "Forge a secret coalition deal with the rival instead of sabotaging them.",
        ai = AI.RHEA,
        prerequisites = listOf(RheaPartyManifesto)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation + 10).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(trust = (state.moodLevels.trust + 10).coerceAtMost(100))
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.reputation >= 15 && messageCount >= 10
        }
    }

    object RheaScandalCoverUp : PlotEvent(
        description = "Cover up a scandal caused by the sabotage attempt.",
        ai = AI.RHEA,
        prerequisites = listOf(RheaSabotageRival)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation - 5).coerceAtLeast(0),
                externalAttention = (state.externalAttention + 20).coerceAtMost(100)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.money >= 0
        }
    }

    object RheaStrengthenAllies : PlotEvent(
        description = "Strengthen alliances from the new coalition deal.",
        ai = AI.RHEA,
        prerequisites = listOf(RheaCoalitionDeal)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation + 15).coerceAtMost(100),
                careerProgress = (state.careerProgress + 10).coerceAtMost(100)
            )
            return updated
        }
    }

    object RheaElectionVictory : PlotEvent(
        description = "Lead Rhea to a major election victory!",
        ai = AI.RHEA,
        prerequisites = listOf(RheaSabotageRival, RheaScandalCoverUp)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation + 25).coerceAtMost(100),
                careerProgress = (state.careerProgress + 30).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(ambition = (state.moodLevels.ambition + 10).coerceAtMost(100))
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.reputation >= 30 && state.careerProgress >= 40
        }
    }

    // ---------------------------------------------------------
    // ------------------- REVAAN EVENTS -----------------------
    // ---------------------------------------------------------

    object RevaanHostParty : PlotEvent(
        description = "Plan an Elite Party with Revaan",
        ai = AI.REVAAN
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = state.money - 20,
                externalAttention = (state.externalAttention + 25).coerceAtMost(100),
                reputation = (state.reputation + 15).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(
                    satisfaction = (state.moodLevels.satisfaction + 10).coerceAtMost(100)
                )
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return true
        }
    }

    object RevaanMediaScandal : PlotEvent(
        description = "Cover up a scandal from Revaan's nightclub",
        ai = AI.REVAAN,
        prerequisites = listOf(RevaanHostParty)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation - 10).coerceAtLeast(0),
                externalAttention = (state.externalAttention + 30).coerceAtMost(100)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.reputation > 0
        }
    }

    object RevaanLaunchFashionLabel : PlotEvent(
        description = "Launch a new fashion label with Revaan's brand",
        ai = AI.REVAAN,
        prerequisites = listOf(RevaanHostParty)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money - 30).coerceAtLeast(0),
                externalAttention = (state.externalAttention + 20).coerceAtMost(100),
                reputation = (state.reputation + 10).coerceAtMost(100)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.money >= 30 && state.externalAttention >= 60
        }
    }

    object RevaanBrandPartnership : PlotEvent(
        description = "Sign a major brand partnership deal with Revaan",
        ai = AI.REVAAN,
        prerequisites = listOf(RevaanLaunchFashionLabel)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = state.money + 50,
                externalAttention = (state.externalAttention + 15).coerceAtMost(100)
            )
            return updated
        }
    }

    // ---------------------------------------------------------
    // ------------------- BABLOO EVENTS -----------------------
    // ---------------------------------------------------------

    object BribeBabloo : PlotEvent(
        description = "Bribe Babloo for a Favor",
        ai = AI.BABLOO
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money - 15).coerceAtLeast(0),
                reputation = (state.reputation - 5).coerceAtLeast(0),
                moodLevels = state.moodLevels.copy(
                    trust = (state.moodLevels.trust + 5).coerceAtMost(100)
                )
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return true
        }
    }

    object BablooProtectionRacket : PlotEvent(
        description = "Help Babloo establish a protection racket downtown.",
        ai = AI.BABLOO,
        prerequisites = listOf(BribeBabloo)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money + 25),
                reputation = (state.reputation - 10).coerceAtLeast(0)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.reputation < 40
        }
    }

    object BablooGuiltyConscience : PlotEvent(
        description = "Babloo struggles with guilt over corruption—assist or ignore?",
        ai = AI.BABLOO,
        prerequisites = listOf(BablooProtectionRacket)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                moodLevels = state.moodLevels.copy(
                    fear = (state.moodLevels.fear + 5).coerceAtMost(100)
                )
            )
            return updated
        }
    }

    // ---------------------------------------------------------
    // ------------------- SHANTI EVENTS -----------------------
    // ---------------------------------------------------------

    object GossipWithShanti : PlotEvent(
        description = "Get Political Gossip from Shanti",
        ai = AI.SHANTI
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money - 10).coerceAtMost(state.money),
                reputation = (state.reputation + 5).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(
                    fear = (state.moodLevels.fear + 5).coerceAtMost(100)
                )
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return true
        }
    }

    object ShantiSecretArchives : PlotEvent(
        description = "Access Shanti's secret archives of political dirt.",
        ai = AI.SHANTI,
        prerequisites = listOf(GossipWithShanti)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation + 10).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(
                    ambition = (state.moodLevels.ambition + 10).coerceAtMost(100)
                )
            )
            return updated
        }
    }

    object ShantiBlackmailOpportunity : PlotEvent(
        description = "Use Shanti’s intel to blackmail a key politician.",
        ai = AI.SHANTI,
        prerequisites = listOf(ShantiSecretArchives)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money + 50),
                reputation = (state.reputation - 15).coerceAtLeast(0),
                moodLevels = state.moodLevels.copy(
                    greed = (state.moodLevels.greed + 10).coerceAtMost(100)
                )
            )
            return updated
        }
    }

    // ---------------------------------------------------------
    // ------------------- CHHOTU EVENTS -----------------------
    // ---------------------------------------------------------

    object ChhotuSecretMission : PlotEvent(
        description = "Send Chhotu on a Secret Delivery",
        ai = AI.CHHOTU
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation + 5).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(
                    fear = (state.moodLevels.fear + 15).coerceAtMost(100)
                )
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return true
        }
    }

    object ChhotuCaughtInCrossfire : PlotEvent(
        description = "Chhotu is caught in crossfire between gangs. Rescue him?",
        ai = AI.CHHOTU,
        prerequisites = listOf(ChhotuSecretMission)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money - 15).coerceAtLeast(0),
                moodLevels = state.moodLevels.copy(
                    trust = (state.moodLevels.trust + 5).coerceAtMost(100)
                )
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.money >= 15
        }
    }

    object ChhotuExpandsNetwork : PlotEvent(
        description = "Help Chhotu expand his informant network across the city.",
        ai = AI.CHHOTU,
        prerequisites = listOf(ChhotuCaughtInCrossfire)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                careerProgress = (state.careerProgress + 10).coerceAtMost(100),
                externalAttention = (state.externalAttention + 10).coerceAtMost(100)
            )
            return updated
        }
    }

    // ---------------------------------------------------------
    // ------------------- VARDHAN EVENTS ----------------------
    // ---------------------------------------------------------

    object SecureFundsFromVardhan : PlotEvent(
        description = "Secure Funding from Vardhan",
        ai = AI.VARDHAN
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money + 40),
                moodLevels = state.moodLevels.copy(
                    greed = (state.moodLevels.greed + 10).coerceAtMost(100)
                ),
                reputation = (state.reputation + 10).coerceAtMost(100)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return true
        }
    }

    object VardhanRealEstateProject : PlotEvent(
        description = "Help Vardhan push a controversial real-estate project through.",
        ai = AI.VARDHAN,
        prerequisites = listOf(SecureFundsFromVardhan)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                money = (state.money + 50),
                reputation = (state.reputation - 5).coerceAtLeast(0)
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.money >= 0
        }
    }

    object VardhanPhilanthropyPush : PlotEvent(
        description = "Encourage Vardhan to invest in philanthropic projects.",
        ai = AI.VARDHAN,
        prerequisites = listOf(SecureFundsFromVardhan)
    ) {
        override fun applyEvent(state: ModelingState): ModelingState {
            val updated = state.copy(
                reputation = (state.reputation + 15).coerceAtMost(100),
                moodLevels = state.moodLevels.copy(
                    greed = (state.moodLevels.greed - 5).coerceAtLeast(0)
                )
            )
            return updated
        }
        override fun canTrigger(state: ModelingState, messageCount: Int): Boolean {
            return state.reputation >= 20
        }
    }

    /**
     * Helper to convert PlotEvents <-> IDs
     */
    object PlotEventUtil {
        fun toEventId(event: PlotEvent): String {
            return event.javaClass.simpleName
        }
        fun fromEventId(id: String): PlotEvent? {
            val allEvents = listOf(
                // Rhea
                RheaCampaignStrategy,
                RheaPartyManifesto,
                RheaSabotageRival,
                RheaCoalitionDeal,
                RheaScandalCoverUp,
                RheaStrengthenAllies,
                RheaElectionVictory,
                // Revaan
                RevaanHostParty,
                RevaanMediaScandal,
                RevaanLaunchFashionLabel,
                RevaanBrandPartnership,
                // Babloo
                BribeBabloo,
                BablooProtectionRacket,
                BablooGuiltyConscience,
                // Shanti
                GossipWithShanti,
                ShantiSecretArchives,
                ShantiBlackmailOpportunity,
                // Chhotu
                ChhotuSecretMission,
                ChhotuCaughtInCrossfire,
                ChhotuExpandsNetwork,
                // Vardhan
                SecureFundsFromVardhan,
                VardhanRealEstateProject,
                VardhanPhilanthropyPush
            )
            return allEvents.find { it.javaClass.simpleName == id }
        }
    }
}

/**
 * PlotEventsRegistry returns a large set of possible events for each AI.
 */
object PlotEventsRegistry {
    fun eventsForAI(ai: AI): List<PlotEvent> {
        return when (ai) {
            AI.RHEA -> listOf(
                PlotEvent.RheaCampaignStrategy,
                PlotEvent.RheaPartyManifesto,
                PlotEvent.RheaSabotageRival,
                PlotEvent.RheaCoalitionDeal,
                PlotEvent.RheaScandalCoverUp,
                PlotEvent.RheaStrengthenAllies,
                PlotEvent.RheaElectionVictory
            )
            AI.REVAAN -> listOf(
                PlotEvent.RevaanHostParty,
                PlotEvent.RevaanMediaScandal,
                PlotEvent.RevaanLaunchFashionLabel,
                PlotEvent.RevaanBrandPartnership
            )
            AI.BABLOO -> listOf(
                PlotEvent.BribeBabloo,
                PlotEvent.BablooProtectionRacket,
                PlotEvent.BablooGuiltyConscience
            )
            AI.SHANTI -> listOf(
                PlotEvent.GossipWithShanti,
                PlotEvent.ShantiSecretArchives,
                PlotEvent.ShantiBlackmailOpportunity
            )
            AI.CHHOTU -> listOf(
                PlotEvent.ChhotuSecretMission,
                PlotEvent.ChhotuCaughtInCrossfire,
                PlotEvent.ChhotuExpandsNetwork
            )
            AI.VARDHAN -> listOf(
                PlotEvent.SecureFundsFromVardhan,
                PlotEvent.VardhanRealEstateProject,
                PlotEvent.VardhanPhilanthropyPush
            )
        }
    }
}
