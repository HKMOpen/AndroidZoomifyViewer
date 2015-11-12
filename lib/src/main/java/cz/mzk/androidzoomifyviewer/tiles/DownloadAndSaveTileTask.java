package cz.mzk.androidzoomifyviewer.tiles;

import android.graphics.Bitmap;

import cz.mzk.androidzoomifyviewer.CacheManager;
import cz.mzk.androidzoomifyviewer.ConcurrentAsyncTask;
import cz.mzk.androidzoomifyviewer.Logger;
import cz.mzk.androidzoomifyviewer.tiles.TilesDownloader.ImageServerResponseException;
import cz.mzk.androidzoomifyviewer.tiles.TilesDownloader.InvalidDataException;
import cz.mzk.androidzoomifyviewer.tiles.TilesDownloader.OtherIOException;
import cz.mzk.androidzoomifyviewer.tiles.TilesDownloader.TooManyRedirectionsException;

/**
 * @author Martin Řehánek
 */
public class DownloadAndSaveTileTask extends ConcurrentAsyncTask<Void, Void, Bitmap> {

    // private static final int THREAD_PRIORITY = Math.min(Thread.MAX_PRIORITY, Thread.MIN_PRIORITY + 1);
    private static final Logger logger = new Logger(DownloadAndSaveTileTask.class);

    private final TilesDownloader downloader;
    private final String zoomifyBaseUrl;
    private final TileId tileId;
    private final TileDownloadResultHandler handler;

    private OtherIOException otherIoException;
    private TooManyRedirectionsException tooManyRedirectionsException;
    private ImageServerResponseException imageServerResponseException;
    private InvalidDataException invalidXmlException;

    /**
     * @param downloader     initialized Tiles downloader, not null
     * @param zoomifyBaseUrl Zoomify base url, not null
     * @param tileId         Tile id, not null
     * @param handler        Tile download result handler, not null
     * @param tilesCache
     */
    public DownloadAndSaveTileTask(TilesDownloader downloader, String zoomifyBaseUrl, TileId tileId,
                                   TileDownloadResultHandler handler) {
        this.downloader = downloader;
        this.zoomifyBaseUrl = zoomifyBaseUrl;
        this.tileId = tileId;
        this.handler = handler;
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
        // Thread thread = Thread.currentThread();
        // thread.setPriority(THREAD_PRIORITY);
        // ThreadGroup group = thread.getThreadGroup();
        // int threadPriority = thread.getPriority();
        // TestLoggers.THREADS.d(String.format("bmp download: priority: %d, TG: name: %s, active: %d, max priority: %d, ",
        // threadPriority, group.getName(), group.activeCount(), group.getMaxPriority()));
        try {
            if (!isCancelled()) {
                Bitmap tile = downloader.downloadTile(tileId);
                if (!isCancelled()) {
                    if (tile != null) {
                        CacheManager.getTilesCache().storeTile(tile, zoomifyBaseUrl, tileId);
                        logger.v(String.format("tile downloaded and saved to disk cache: base url: '%s', tile: '%s'",
                                zoomifyBaseUrl, tileId));
                    } else {
                        // TODO: examine this
                        logger.w("tile is null");
                    }
                } else {
                    logger.v(String
                            .format("tile processing canceled task after downloading and before saving data: base url: '%s', tile: '%s'",
                                    zoomifyBaseUrl, tileId));
                }
            } else {
                logger.v(String.format(
                        "tile processing task canceled before download started: base url: '%s', tile: '%s'",
                        zoomifyBaseUrl, tileId));
            }
        } catch (TooManyRedirectionsException e) {
            tooManyRedirectionsException = e;
        } catch (ImageServerResponseException e) {
            imageServerResponseException = e;
        } catch (OtherIOException e) {
            otherIoException = e;
        }
        // TODO
        // catch (InvalidDataException e) {
        // invalidXmlException = e;
        // }
        finally {
            // Log.d(TAG, "tile processing task finished");
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        downloader.getTaskRegistry().unregisterTask(tileId);
        if (tooManyRedirectionsException != null) {
            handler.onRedirectionLoop(tileId, tooManyRedirectionsException.getUrl(),
                    tooManyRedirectionsException.getRedirections());
        } else if (imageServerResponseException != null) {
            handler.onUnhandableResponseCode(tileId, imageServerResponseException.getUrl(),
                    imageServerResponseException.getErrorCode());
        } else if (invalidXmlException != null) {
            handler.onInvalidData(tileId, invalidXmlException.getUrl(), invalidXmlException.getMessage());
        } else if (otherIoException != null) {
            handler.onDataTransferError(tileId, otherIoException.getUrl(), otherIoException.getMessage());
        } else {
            handler.onSuccess(tileId, bitmap);
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        downloader.getTaskRegistry().unregisterTask(tileId);
    }

    public interface TileDownloadResultHandler {

        public void onSuccess(TileId tileId, Bitmap bitmap);

        public void onUnhandableResponseCode(TileId tileId, String tileUrl, int responseCode);

        public void onRedirectionLoop(TileId tileId, String tileUrl, int redirections);

        public void onDataTransferError(TileId tileId, String tileUrl, String errorMessage);

        public void onInvalidData(TileId tileId, String tileUrl, String errorMessage);

    }

}