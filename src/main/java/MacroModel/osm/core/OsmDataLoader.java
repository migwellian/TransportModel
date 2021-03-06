package MacroModel.osm.core;

import MacroModel.osm.BoundingBox;
import MacroModel.roads.Logger;
import MacroModel.utils.FileUtils;

import javax.xml.stream.XMLEventReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class OsmDataLoader {

    private static long cacheValidForMillis = 60 * 24 * 60 * 60 * 1000;
    private static CacheDirectory cache = new CacheDirectory("cache/osm/", ".xml", cacheValidForMillis);
    private static List<String> osmEndpoints = new ArrayList<>();

    static {
        osmEndpoints.add("http://overpass-api.de/api/");
        osmEndpoints.add("http://overpass.preprocessors.osm.rambler.ru/cgi/");
    }

    public static Optional<OsmCachedXmlData> getData(BoundingBox boundingBox) {
        Logger.info("OsmDataLoader: Loading OSM data for bounding box " + boundingBox +
                " (cache file base name " + boundingBox.getCacheFileBaseName() + ")");

        tryUpdateCacheIfNecessary(boundingBox);

        if (cache.contains(boundingBox.getCacheFileBaseName())) {
            return loadDataFromCache(boundingBox.getCacheFileBaseName());
        } else {
            Logger.error("OsmDataLoader: Could not load data from cache or web.");
            return Optional.empty();
        }
    }

    private static void tryUpdateCacheIfNecessary(BoundingBox boundingBox) {
        String cacheFileBaseName = boundingBox.getCacheFileBaseName();
        if (!cache.contains(cacheFileBaseName) || cache.isOutOfDate(cacheFileBaseName)) {
            Logger.info("OsmDataLoader: Downloading data.");
            downloadAndCacheData(boundingBox);
        } else {
            Logger.info("OsmDataLoader: Recently cached OSM data can be loaded for the study area.");
        }
    }

    private static void downloadAndCacheData(BoundingBox boundingBox) {
        boolean success = false;
        for (String osmEndpoint : osmEndpoints) {
            success = downloadRawDataFromEndpoint(osmEndpoint, boundingBox);
            if (success) {
                break;
            }
        }

        if (success) {
            Logger.info("OsmDataLoader: OSM data successfully downloaded and cached");
        } else {
            Logger.error("OsmDataLoader: Couldn't download OSM data. Are you connected to the internet?");
        }
    }

    private static boolean downloadRawDataFromEndpoint(String osmEndpoint, BoundingBox boundingBox) {
        // For information on the query syntax, visit http://wiki.openstreetmap.org/wiki/Overpass_API/Language_Guide
        String urlStr = osmEndpoint + "interpreter?data=(node(" + boundingBox + ");<;);out%20body;";

        try {
            Logger.info("OsmDataLoader: Attempting to download OSM data from: " + urlStr);
            String cacheFilePath = cache.makeCacheFilePath(boundingBox.getCacheFileBaseName(), new Date().getTime());
            URL url = new URL(urlStr);
            ReadableByteChannel in = Channels.newChannel(url.openStream());
            FileOutputStream out = new FileOutputStream(cacheFilePath);
            out.getChannel().transferFrom(in, 0, Long.MAX_VALUE);
            in.close();
            out.close();
            cache.refresh();
            return true;
        } catch (MalformedURLException ex) {
            Logger.error("OsmDataLoader: The URL was malformed: " + urlStr + " (" + ex.getMessage() + ")");
        } catch (IOException ex) {
            Logger.error("OsmDataLoader: Could not download data from " + urlStr + " (" + ex.getMessage() + ")");
        }

        return false;
    }

    private static Optional<OsmCachedXmlData> loadDataFromCache(String cacheFileBaseName) {
        String filePath = cache.getExistingCachedFilePath(cacheFileBaseName);
        long timestamp = cache.getExistingCachedFileTimestamp(cacheFileBaseName);

        try {
            XMLEventReader xmlReader = FileUtils.streamXmlFile(filePath);
            return Optional.of(new OsmCachedXmlData(xmlReader, timestamp));
        } catch (IOException ex) {
            Logger.error("OsmDataLoader: Could not read cached OSM file: " + ex.getMessage());
        } catch (Exception ex) {
            Logger.error("OsmDataLoader: Could not parse cached OSM file as XML: " + ex.getMessage());
        }

        return Optional.empty();
    }
}
