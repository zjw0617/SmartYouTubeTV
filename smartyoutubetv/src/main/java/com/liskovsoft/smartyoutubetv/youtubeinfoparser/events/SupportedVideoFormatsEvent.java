package com.liskovsoft.smartyoutubetv.youtubeinfoparser.events;

public class SupportedVideoFormatsEvent {
    private final String mFormats;

    public SupportedVideoFormatsEvent(String formats) {
        mFormats = formats;
    }

    public String getFormats() {
        return mFormats;
    }
}
