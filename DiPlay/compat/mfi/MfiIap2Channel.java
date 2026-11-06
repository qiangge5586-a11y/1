package com.shilapi.xcertplay.compat.mfi;

/** Logical transport scope; control and type-130 tunnel authentication never share state. */
public enum MfiIap2Channel {
    RFCOMM_CONTROL,
    AIRPLAY_TYPE130_TUNNEL
}
