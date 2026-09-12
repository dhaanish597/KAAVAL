package app.vaakku.domain.reconcile

import app.vaakku.domain.model.ClaimType
import app.vaakku.domain.model.Observation

/** Inputs to the reconciler state machine — build plan §5.7. */
sealed interface ReconcilerEvent {
    data class SpokenObserved(val observation: Observation) : ReconcilerEvent
    data class WrittenObserved(val observation: Observation) : ReconcilerEvent
    data object DocumentScanCompleted : ReconcilerEvent
    data class UserRecheck(val type: ClaimType) : ReconcilerEvent
    data object Reset : ReconcilerEvent
}
