package usdot.v2x.app.api.models.etx.registration;

/**
 * Lifecycle events recorded in registration_events.
 */
public enum RegistrationEventType {
    REGISTER,
    REGISTER_FAILED,
    PUT,
    PUT_FAILED,
    CONNECT,
    CONNECT_FAILED,
    /** Deleted from ETX to make room under the per-user limit. */
    EVICT_USER_LIMIT,
    /** Deleted from ETX to make room under the vendor-wide limit. */
    EVICT_VENDOR_LIMIT,
    /** Deleted from ETX because the certificate expired. */
    EXPIRE,
    /** An ETX delete call failed; the local row was left active. */
    DELETE_FAILED,
    /** Local row marked inactive because ETX no longer reports the device. */
    RECONCILED_MISSING,
    /** Device registered in ETX with no matching local row. */
    UNTRACKED_IN_ETX,
}
