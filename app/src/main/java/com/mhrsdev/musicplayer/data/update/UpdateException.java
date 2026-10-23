package com.mhrsdev.musicplayer.data.update;

import com.mhrsdev.musicplayer.domain.model.UpdateState.ErrorKind;

/** A failed check or download, already classified for the UI. */
class UpdateException extends Exception {
    final ErrorKind kind;

    UpdateException(ErrorKind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    UpdateException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }
}
