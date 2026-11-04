package com.fashionrental.common.exception;

public class StaleUpdateException extends ConflictException {

    // Must match STALE_VERSION_MESSAGE in frontend/src/utils/staleVersion.ts — the frontend
    // recognises a stale-save 409 by this exact text.
    public static final String MESSAGE =
            "This record was changed by someone else since you opened it. Reload to see the latest version.";

    public StaleUpdateException() {
        super(MESSAGE);
    }
}
