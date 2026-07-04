package it.diunipi.sam.highnoon.game

enum class DuelPhase {
    IDLE, WAITING, DRAW, RESOLVING, RESULT
}

// Final result of a networked duel, decided by the Group Owner (the referee).
enum class Outcome { WIN, LOSE, DRAW }

enum class ChallengeState { NONE, OUTGOING, INCOMING, ACCEPTED }