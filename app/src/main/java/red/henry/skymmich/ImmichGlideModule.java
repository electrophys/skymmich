package red.henry.skymmich;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

import java.io.InputStream;

import okhttp3.OkHttpClient;

@GlideModule
public class ImmichGlideModule extends AppGlideModule {

    private static final String TAG = "ImmichGlide";
    private static OkHttpClient sClient;

    /**
     * Call this before Glide is initialized to set the OkHttpClient with API key interceptor.
     */
    public static void setOkHttpClient(OkHttpClient client) {
        sClient = client;
    }

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        // 20MB memory cache
        builder.setMemoryCache(new LruResourceCache(20 * 1024 * 1024));

        // 100MB disk cache
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "glide_cache", 100 * 1024 * 1024));

        // Use RGB_565 to halve memory per bitmap
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .format(DecodeFormat.PREFER_RGB_565)
                        .encodeFormat(Bitmap.CompressFormat.JPEG)
                        .encodeQuality(80)
        );

        builder.setLogLevel(Log.WARN);
    }

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        if (sClient != null) {
            registry.replace(GlideUrl.class, InputStream.class,
                    new OkHttpUrlLoader.Factory(sClient));
        }
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
