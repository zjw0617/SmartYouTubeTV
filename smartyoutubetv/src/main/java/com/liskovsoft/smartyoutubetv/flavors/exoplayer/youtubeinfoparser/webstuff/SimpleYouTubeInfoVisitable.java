package com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.webstuff;

import android.net.Uri;
import com.liskovsoft.browser.Browser;
import com.liskovsoft.smartyoutubetv.misc.Helpers;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.tmp.CipherUtils;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.SimpleYouTubeGenericInfo;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.SimpleYouTubeMediaItem;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.YouTubeGenericInfo;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.YouTubeMediaItem;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.webstuff.events.DecipherSignaturesDoneEvent;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.youtubeinfoparser.webstuff.events.DecipherSignaturesEvent;
import com.squareup.otto.Subscribe;
import okhttp3.Response;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimpleYouTubeInfoVisitable implements YouTubeInfoVisitable {
    private final String mContent;
    private YouTubeInfoVisitor mVisitor;
    private List<YouTubeMediaItem> mMediaItems;

    public SimpleYouTubeInfoVisitable(String content) {
        mContent = content;
        Browser.getBus().register(this);
    }

    @Override
    public void accept(YouTubeInfoVisitor visitor) {
        mVisitor = visitor;

        YouTubeGenericInfo info = obtainGenericInfo();
        mVisitor.onGenericInfo(info);

        Uri uri = extractHLSUrl();
        if (uri != null) {
            mVisitor.onLiveItem(uri);
            // this is live so other items is useless
            return;
        }

        List<YouTubeMediaItem> items = parseToMediaItems();
        assert items != null;
        mMediaItems = items;

        decipherSignaturesAndDoCallback();
    }

    private Uri extractHLSUrl() {
        Uri videoInfo = Uri.parse("http://example.com?" + mContent);
        String hlsUrl = videoInfo.getQueryParameter("hlsvp");
        if (hlsUrl != null) {
            return Uri.parse(hlsUrl);
        }
        return null;
    }

    private InputStream extractRawMPD() {
        Uri videoInfo = Uri.parse("http://example.com?" + mContent);
        String dashmpdUrl = videoInfo.getQueryParameter("dashmpd");
        if (dashmpdUrl != null) {
            Response response = Helpers.doOkHttpRequest(dashmpdUrl);
            return response.body().byteStream();
        }
        return null;
    }

    private void decipherSignaturesAndDoCallback() {
        Browser.getBus().post(new DecipherSignaturesEvent(mMediaItems));
    }

    @Subscribe
    public void decipherSignaturesDone(DecipherSignaturesDoneEvent doneEvent) {
        Browser.getBus().unregister(this);

        List<YouTubeMediaItem> items = doneEvent.getMediaItems();
        for (YouTubeMediaItem item : items) {
            mVisitor.onMediaItem(item);
        }
        mVisitor.doneVisiting();
    }

    private List<YouTubeMediaItem> parseToMediaItems() {
        List<YouTubeMediaItem> list = new ArrayList<>();
        List<String> items = splitContent(mContent);
        for (String item : items) {
            list.add(createMediaItem(item));
        }
        return list;
    }

    private YouTubeMediaItem createMediaItem(String content) {
        Uri mediaUrl = Uri.parse("http://example.com?" + content);
        SimpleYouTubeMediaItem mediaItem = new SimpleYouTubeMediaItem();
        mediaItem.setBitrate(mediaUrl.getQueryParameter(YouTubeMediaItem.BITRATE));
        mediaItem.setUrl(mediaUrl.getQueryParameter(YouTubeMediaItem.URL));
        mediaItem.setITag(mediaUrl.getQueryParameter(YouTubeMediaItem.ITAG));
        mediaItem.setType(mediaUrl.getQueryParameter(YouTubeMediaItem.TYPE));
        mediaItem.setS(mediaUrl.getQueryParameter(YouTubeMediaItem.S));
        mediaItem.setClen(mediaUrl.getQueryParameter(YouTubeMediaItem.CLEN));
        mediaItem.setFps(mediaUrl.getQueryParameter(YouTubeMediaItem.FPS));
        mediaItem.setIndex(mediaUrl.getQueryParameter(YouTubeMediaItem.INDEX));
        mediaItem.setInit(mediaUrl.getQueryParameter(YouTubeMediaItem.INIT));
        mediaItem.setSize(mediaUrl.getQueryParameter(YouTubeMediaItem.SIZE));
        //decipherSignature(mediaItem);
        return mediaItem;
    }

    private void decipherSignature(SimpleYouTubeMediaItem mediaItem) {
        String sig = mediaItem.getS();
        if (sig != null) {
            String url = mediaItem.getUrl();
            String newSig = CipherUtils.decipherSignature(sig);
            mediaItem.setUrl(String.format("%s&signature=%s", url, newSig));
        }
    }

    private YouTubeGenericInfo obtainGenericInfo() {
        YouTubeGenericInfo info = new SimpleYouTubeGenericInfo();
        Uri videoInfo = Uri.parse("http://example.com?" + mContent);
        info.setLengthSeconds(videoInfo.getQueryParameter(YouTubeGenericInfo.LENGTH_SECONDS));
        info.setTitle(videoInfo.getQueryParameter(YouTubeGenericInfo.TITLE));
        info.setAuthor(videoInfo.getQueryParameter(YouTubeGenericInfo.AUTHOR));
        info.setViewCount(videoInfo.getQueryParameter(YouTubeGenericInfo.VIEW_COUNT));
        info.setTimestamp(videoInfo.getQueryParameter(YouTubeGenericInfo.TIMESTAMP));
        return info;
    }

    private List<String> splitContent(String content) {
        List<String> list = new ArrayList<>();
        Uri videoInfo = Uri.parse("http://example.com?" + content);
        String adaptiveFormats = videoInfo.getQueryParameter("adaptive_fmts");
        // stream may not contain dash formats
        if (adaptiveFormats != null) {
            String[] fmts = adaptiveFormats.split(",");
            list.addAll(Arrays.asList(fmts));
        }

        String regularFormats = videoInfo.getQueryParameter("url_encoded_fmt_stream_map");
        if (regularFormats != null) {
            String[] fmts = regularFormats.split(",");
            list.addAll(Arrays.asList(fmts));
        }
        return list;
    }
}
