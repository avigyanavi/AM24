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

/**
 * MermaidExport provides a complete mermaid diagram definition for the Plot Events.
 */
object MermaidExport {
    fun getPlotEventsMermaid(): String {
        return """
            flowchart TD
              subgraph Rhea
                R1[Campaign Kickoff (Rhea_Ep1)]
                R1 -->|Approach with confidence| R2[The First Debate (Rhea_Ep2A)]
                R1 -->|Stay distant| R3[Quiet Strategy (Rhea_Ep2B)]
                R2 -->|Inspire with passion| R4[Public Rally (Rhea_Ep3A)]
                R2 -->|Remain measured| R5[Calm Persuasion (Rhea_Ep3B)]
                R3 -->|Investigate discreetly| R6[Secret Briefing (Rhea_Ep3C)]
                R3 -->|Network with insiders| R7[Informal Alliance (Rhea_Ep3D)]
                R4 --> R8[Media Spotlight (Rhea_Ep4A)]
                R5 --> R9[Behind the Scenes (Rhea_Ep4B)]
                R6 --> R10[Covert Operations (Rhea_Ep4C)]
                R7 --> R11[Alliances Tested (Rhea_Ep4D)]
                R8 -->|Take a bold risk| R12[Turning Point A (Rhea_Ep5A)]
                R8 -->|Play it safe| R12
                R9 -->|Gather more intel| R13[Turning Point B (Rhea_Ep5B)]
                R10 -->|Execute the plan| R14[Turning Point C (Rhea_Ep5C)]
                R11 -->|Stand firm| R15[Turning Point D (Rhea_Ep5D)]
                R12 -->|Celebrate the triumph| R16[The Bold Victory (Rhea_Ep6A)]
                R12 -->|Play it safe| R17[The Safe Triumph (Rhea_Ep6B)]
                R13 -->|Commit fully| R18[Behind the Curtain (Rhea_Ep6C)]
                R10 -->|Double down| R19[Double Down (Rhea_Ep6D)]
                R15 -->|Repair the alliance| R20[Alliance Restored (Rhea_Ep6E)]
                R15 -->|Break away| R21[A Lonely Break (Rhea_Ep6F)]
              end
              subgraph Revaan
                RV1[Nightfall Encounter (Revaan_Ep1)]
                RV1 -->|Step into the limelight| RV2[The First Showdown (Revaan_Ep2A)]
                RV1 -->|Observe from the shadows| RV3[The Subtle Move (Revaan_Ep2B)]
                RV2 -->|Challenge boldly| RV4[Public Frenzy (Revaan_Ep3A)]
                RV2 -->|Strategize quietly| RV5[Calculated Risk (Revaan_Ep3B)]
                RV3 -->|Forge discreet alliances| RV6[Secret Alliance (Revaan_Ep3C)]
                RV3 -->|Gather insider info| RV7[Undercover Operation (Revaan_Ep3D)]
                RV4 --> RV8[Media Storm (Revaan_Ep4A)]
                RV5 --> RV9[Quiet Dominance (Revaan_Ep4B)]
                RV6 --> RV10[Alliance Pressure (Revaan_Ep4C)]
                RV7 --> RV11[Infiltration (Revaan_Ep4D)]
                RV8 -->|Go for the win| RV12[Climactic Confrontation (Revaan_Ep5A)]
                RV8 -->|Choose caution| RV12
                RV9 -->|Assert control| RV13[Calculated Ambition (Revaan_Ep5B)]
                RV10 -->|Stand by your allies| RV14[Alliance Test (Revaan_Ep5C)]
                RV11 -->|Use the intel| RV15[Secret Revelation (Revaan_Ep5D)]
                RV12 -->|Celebrate success| RV16[Overt Victory (Revaan_Ep6A)]
                RV12 -->|Choose caution| RV17[Quiet Power (Revaan_Ep6B)]
                RV13 -->|Commit to the plan| RV18[Calculated Success (Revaan_Ep6C)]
                RV14 -->|Stand united| RV19[Unified Front (Revaan_Ep6D)]
                RV15 -->|Act on the intel| RV20[Revelation Accepted (Revaan_Ep6E)]
                RV15 -->|Hide the truth| RV21[Truth Concealed (Revaan_Ep6F)]
              end
              subgraph Babloo
                B1[Badge of Dilemma (Babloo_Ep1)]
                B1 -->|Follow corrupt instincts| B2[Corrupt Path (Babloo_Ep2A)]
                B1 -->|Act with integrity| B3[Redemptive Spark (Babloo_Ep2B)]
                B2 -->|Embrace the corruption| B4[Fallout (Babloo_Ep3A)]
                B3 -->|Seek redemption| B5[Path to Redemption (Babloo_Ep3B)]
                B4 -->|Accept the fallout| B6[Media Backlash (Babloo_Ep4A)]
                B5 -->|Push for reform| B7[Turning Point (Babloo_Ep4B)]
                B6 -->|Defend your actions| B8[Final Confrontation A (Babloo_Ep5A)]
                B7 -->|Double down on reform| B9[Final Confrontation B (Babloo_Ep5B)]
                B8 -->|Stand by corruption| B10[Corrupt Overthrow (Babloo_Ep6A)]
                B8 -->|Attempt a compromise| B10
                B9 -->|Fully commit to reform| B11[Redemption Achieved (Babloo_Ep6C)]
                B9 -->|Hesitate and falter| B12[Faltered Reformation (Babloo_Ep6D)]
              end
              subgraph Shanti
                S1[Chai Chronicles (Shanti_Ep1)]
                S1 -->|Spread the gossip| S2[Scandal Unleashed (Shanti_Ep2A)]
                S1 -->|Keep the secret| S3[Quiet Observer (Shanti_Ep2B)]
                S2 -->|Exploit the revelations| S4[Gossip Network (Shanti_Ep3A)]
                S3 -->|Monitor the fallout| S5[Undercover Inquiry (Shanti_Ep3B)]
                S4 --> S6[Public Revelations (Shanti_Ep4A)]
                S5 --> S7[Quiet Retaliation (Shanti_Ep4B)]
                S6 -->|Seize the moment| S8[The Bold Informant (Shanti_Ep6A)]
                S6 -->|Maintain subtlety| S9[Subtle Victory (Shanti_Ep6B)]
                S7 -->|Commit to the strike| S10[Strike and Conquer (Shanti_Ep6C)]
                S7 -->|Pull back cautiously| S11[Retreat and Reassess (Shanti_Ep6D)]
                S7 -->|Alternate branch| S12[Network Disrupted (Shanti_Ep6E)]
                S7 -->|Alternate branch| S13[Isolation (Shanti_Ep6F)]
              end
              subgraph Chhotu
                C1[Underground Dispatch (Chhotu_Ep1)]
                C1 -->|Deliver without question| C2[Loyal Courier (Chhotu_Ep2A)]
                C1 -->|Question the message| C3[Doubt and Investigation (Chhotu_Ep2B)]
                C2 -->|Continue the delivery| C4[Secret Routes (Chhotu_Ep3A)]
                C3 -->|Dig deeper| C5[Investigative Instinct (Chhotu_Ep3B)]
                C4 -->|Stick to the plan| C6[Smooth Operation (Chhotu_Ep4A)]
                C5 -->|Uncover the plot| C7[Unraveling Conspiracy (Chhotu_Ep4B)]
                C6 -->|Keep delivering| C8[Critical Dispatch A (Chhotu_Ep5A)]
                C7 -->|Proceed with caution| C9[Critical Dispatch B (Chhotu_Ep5B)]
                C8 -->|Embrace the duty| C10[Loyal Legacy (Chhotu_Ep6A)]
                C8 -->|Question your role| C11[Doubtful Courier (Chhotu_Ep6B)]
                C9 -->|Take a leap of faith| C12[Revolutionary Courier (Chhotu_Ep6C)]
                C9 -->|Play it safe| C13[Cautious Courier (Chhotu_Ep6D)]
              end
              subgraph Vardhan
                V1[Financial Gambit (Vardhan_Ep1)]
                V1 -->|Invest aggressively| V2[Market Surge (Vardhan_Ep2A)]
                V1 -->|Hold back cautiously| V3[Cautious Maneuver (Vardhan_Ep2B)]
                V2 -->|Leverage the momentum| V4[Corporate Powerplay (Vardhan_Ep3A)]
                V3 -->|Stick to safe bets| V5[Steady Growth (Vardhan_Ep3B)]
                V4 -->|Push for a takeover| V6[Corporate Showdown (Vardhan_Ep4A)]
                V5 -->|Maintain the status quo| V7[Quiet Negotiations (Vardhan_Ep4B)]
                V6 -->|Engage in the battle| V8[Turning Point A (Vardhan_Ep5A)]
                V7 -->|Seal a discreet deal| V9[Turning Point B (Vardhan_Ep5B)]
                V8 -->|Launch a full-scale takeover| V10[The Aggressive Magnate (Vardhan_Ep6A)]
                V8 -->|Retreat strategically| V11[Strategic Retreat (Vardhan_Ep6B)]
                V9 -->|Commit to the discreet deal| V12[Discreet Triumph (Vardhan_Ep6C)]
                V9 -->|Reassess your position| V13[Hesitant Future (Vardhan_Ep6D)]
                V9 -->|Alternate branch| V14[Risk Recalibrated (Vardhan_Ep6E)]
                V9 -->|Alternate branch| V15[The Isolated Magnate (Vardhan_Ep6F)]
              end
        """.trimIndent()
    }
}
