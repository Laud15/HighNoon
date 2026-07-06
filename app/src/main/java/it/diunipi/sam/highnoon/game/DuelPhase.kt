package it.diunipi.sam.highnoon.game

//Idle: you are connected to pre-duel'room
//Waiting: when the western music is playing
//Draw: when the player have to pick up the phone
//Result: win, lose and photo phase
enum class DuelPhase { IDLE, WAITING, DRAW, RESOLVING, RESULT }

// Final result of a networked duel, decided by the Group Owner (the referee).
enum class Outcome { WIN, LOSE, DRAW }

//the state of the handshake before the duel
//Outgoing: challenge sent, wait
//Incoming: received challenge, show the dialog
//accepted: both players have accepted
enum class ChallengeState { NONE, OUTGOING, INCOMING, ACCEPTED }