package com.smartlms.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to convert various YouTube URL formats to standard embed URLs.
 */
public class YouTubeUtil {

    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|v\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
    );

    public static String toEmbedUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }
        Matcher matcher = YOUTUBE_PATTERN.matcher(url.trim());
        if (matcher.find()) {
            String videoId = matcher.group(1);
            return "https://www.youtube.com/embed/" + videoId;
        }
        return url.trim();
    }
}
