package io.github.qzcsfchh.android.pdf;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.os.Process;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import uk.co.senab.photoview.PhotoView;


/**
 * A PDF file render View.
 */
public class PdfView extends FrameLayout {
    private static final String TAG = "PdfView";
    private HandlerThread mRenderThread;
    private Handler mRenderHandler;
    private int mCurrentIndex = 0;
    private final AtomicReference<PdfRenderer> mPdfRenderer = new AtomicReference<>();
    private final PdfPageAdapter mAdapter;
    private PdfRendererSupplier mRendererSupplier;
    private final ViewPager2 mViewPager2;
    private TextView mTvCounter;
    private boolean mShowIndicator;

    public PdfView(@NonNull Context context) {
        this(context,null);
    }

    public PdfView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public PdfView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PdfView, defStyleAttr, 0);
        int orientation = ta.getInt(R.styleable.PdfView_android_orientation, ViewPager2.ORIENTATION_VERTICAL);
        boolean showIndicator = ta.getBoolean(R.styleable.PdfView_pdfShowIndicator, true);
        ta.recycle();
        mViewPager2 = new ViewPager2(context);
        setOrientation(orientation);
        addView(mViewPager2, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setShowIndicator(showIndicator);
        mViewPager2.registerOnPageChangeCallback(mOnPageChangeCallback);
        mAdapter = new PdfPageAdapter();
        mAdapter.registerAdapterDataObserver(mDataObserver);
        mViewPager2.setAdapter(mAdapter);
    }

    private final ViewPager2.OnPageChangeCallback mOnPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            mCurrentIndex = position;
            if (mTvCounter != null) {
                mTvCounter.setText(String.format(Locale.getDefault(), "%d/%d", mCurrentIndex + 1, mAdapter.getItemCount()));
            }
        }
    };

    private final RecyclerView.AdapterDataObserver mDataObserver = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            if (mTvCounter != null && mTvCounter.getVisibility() != View.VISIBLE) {
                mTvCounter.setVisibility(View.VISIBLE);
                final int itemCount = mAdapter.getItemCount();
                mTvCounter.setText(String.format(Locale.getDefault(), "%d/%d", itemCount > 0 ? mCurrentIndex + 1 : 0, itemCount));
            }
        }
    };

    public void registerOnPageChangeCallback(ViewPager2.OnPageChangeCallback callback){
        mViewPager2.registerOnPageChangeCallback(callback);
    }

    public void unregisterOnPageChangeCallback(ViewPager2.OnPageChangeCallback callback){
        mViewPager2.unregisterOnPageChangeCallback(callback);
    }

    public boolean isShowIndicator() {
        return mShowIndicator;
    }

    public void setShowIndicator(boolean showIndicator) {
        mShowIndicator = showIndicator;
        if (mShowIndicator && mTvCounter == null) {
            mTvCounter = createDefaultIndicator();
            mTvCounter.setVisibility(View.GONE);
            addView(mTvCounter);
        } else if (!mShowIndicator && mTvCounter != null) {
            removeView(mTvCounter);
            mTvCounter = null;
        }
    }

    public void setOrientation(int orientation){
        mViewPager2.setOrientation(orientation);
    }

    public int getOrientation(){
        return mViewPager2.getOrientation();
    }

    private TextView createDefaultIndicator(){
        TextView tvCounter = new TextView(getContext());
        tvCounter.setPadding(dp2px(8), dp2px(3), dp2px(8), dp2px(3));
        tvCounter.setTextSize(13f);
        tvCounter.setTextColor(Color.WHITE);
        tvCounter.setGravity(Gravity.CENTER);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp2px(21) / 2f);
        drawable.setColor(Color.parseColor("#66000000"));
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int dp16 = dp2px(16);
        layoutParams.setMargins(dp16, dp16, dp16, dp16);
        layoutParams.gravity = Gravity.START;
        tvCounter.setLayoutParams(layoutParams);
        tvCounter.setBackground(drawable);
        return tvCounter;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mRenderThread = new HandlerThread("PdfViewThread", Process.THREAD_PRIORITY_BACKGROUND);
        mRenderThread.start();
        mRenderHandler = new Handler(mRenderThread.getLooper());
        mRenderHandler.post(mRefreshTask);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRenderHandler.removeCallbacksAndMessages(null);
        try {
            mRenderThread.quitSafely();
        } catch (Exception e) {
            e.printStackTrace();
        }
        closeRenderer();
    }


    public void setPdfFilePath(@NonNull final String pdfFilePath) {
        setPdfFilePath(new File(pdfFilePath));
    }

    public void setPdfFilePath(final File pdfFile) {
        setRendererSupplier(new PdfRendererSupplier() {
            @Override
            public PdfRenderer get() {
                if (pdfFile != null && pdfFile.exists()) {
                    try {
                        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                        return new PdfRenderer(pfd);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            public String uniqueKey() {
                final String filePath = pdfFile == null ? null : pdfFile.getAbsolutePath();
                return String.valueOf(filePath == null ? 0 : filePath.hashCode());
            }
        });
    }

    public void setPdfAssetsPath(final String assetsPath){
        setRendererSupplier(new PdfRendererSupplier() {
            @Override
            public PdfRenderer get() {
                if (TextUtils.isEmpty(assetsPath)) return null;
                try {
                    ParcelFileDescriptor pfd = getContext().getAssets().openFd(assetsPath).getParcelFileDescriptor();
                    return new PdfRenderer(pfd);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            public String uniqueKey() {
                return String.valueOf(assetsPath == null ? 1 : assetsPath.hashCode());
            }
        });
    }

    public void setRendererSupplier(PdfRendererSupplier supplier){
        mRendererSupplier = supplier;
        // Reset page index.
        mCurrentIndex = 0;
        if (mRenderHandler != null) {
            mRenderHandler.post(mRefreshTask);
        }
    }

    private final Runnable mRefreshTask = new Runnable() {
        @Override
        public void run() {
            closeRenderer();
            prepareRenderer();
            post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                    mViewPager2.setCurrentItem(mCurrentIndex, false);
                }
            });
        }
    };

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        mCurrentIndex = currentIndex;
        int pageCount = getNumberOfPages();
        if (mCurrentIndex < 0) mCurrentIndex = 0;
        if (mCurrentIndex >= pageCount) mCurrentIndex = pageCount - 1;
        mViewPager2.setCurrentItem(mCurrentIndex, false);
    }

    public int getPageCount(){
        return mAdapter.getItemCount();
    }

    private int getNumberOfPages(){
        final PdfRenderer renderer = mPdfRenderer.get();
        if (renderer == null) return 0;
        return renderer.getPageCount();
    }


    @WorkerThread
    private Bitmap syncGetPdfPage(int index){
        final PdfRenderer renderer = mPdfRenderer.get();
        if (renderer == null) {
            Log.w(TAG, "syncGetPdfPage: PdfRenderer is null");
            return null;
        }
        String uniqueKey = mRendererSupplier.uniqueKey();
        if (TextUtils.isEmpty(uniqueKey)) {
            Log.w(TAG, "syncGetPdfPage: uniqueKey is null");
            return null;
        }
        int pageCount = renderer.getPageCount();
        if (index < 0) index = 0;
        if (index >= pageCount) index = pageCount - 1;
        final String bitmapName = uniqueKey + "#" + index;

        final File dir = getPdfCacheDir(getContext());
        if (dir != null) {
            File bmpFile = new File(dir, bitmapName);
            if (bmpFile.exists()) {
                return BitmapFactory.decodeFile(bmpFile.getAbsolutePath());
            }
        }
        final PdfRenderer.Page page = renderer.openPage(index);
        final int dpi = getResources().getDisplayMetrics().densityDpi;
        double width = dpi * page.getWidth();
        double height = dpi * page.getHeight();
        final double docRatio = width / height;

        width = 2048;
        height = (int) (width / docRatio);
        final Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        if (dir != null) {
            mRenderHandler.post(new Runnable() {
                @Override
                public void run() {
                    File bmpFile = new File(dir, bitmapName);
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(bmpFile);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } finally {
                        close(fos);
                    }
                }
            });
        }
        return bitmap;
    }

    private void asyncGetPdfPage(final int index, final Consumer<Bitmap> consumer){
        mRenderHandler.post(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = syncGetPdfPage(index);
                post(new Runnable() {
                    @Override
                    public void run() {
                        consumer.accept(bitmap);
                    }
                });
            }
        });
    }

    private void closeRenderer(){
        final PdfRenderer renderer = mPdfRenderer.get();
        if (renderer != null) {
            try {
                renderer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mPdfRenderer.set(null);
        }
    }

    private void prepareRenderer() {
        if (mPdfRenderer.get() != null) return;
        if (mRendererSupplier == null) return;
        final PdfRenderer renderer = mRendererSupplier.get();
        mPdfRenderer.set(renderer);
    }

    static final class PdfPageHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final ProgressBar progressBar;
        public PdfPageHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(android.R.id.icon1);
            progressBar = itemView.findViewById(android.R.id.progress);
        }
    }

    public interface PdfRendererSupplier extends Supplier<PdfRenderer>{
        String uniqueKey();
    }

    final class PdfPageAdapter extends RecyclerView.Adapter<PdfPageHolder> {
        @NonNull
        @Override
        public PdfPageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            FrameLayout frameLayout = new FrameLayout(context);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ImageView imageView = new PhotoView(context);
            imageView.setId(android.R.id.icon1);
            frameLayout.addView(imageView, lp);
            ProgressBar progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(true);
            progressBar.setId(android.R.id.progress);
            LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.CENTER;
            frameLayout.addView(progressBar, layoutParams);
            frameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new PdfPageHolder(frameLayout);
        }

        @Override
        public void onBindViewHolder(@NonNull final PdfPageHolder holder, int position) {
            final int reqPosition = position;
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.imageView.setImageBitmap(null);
            asyncGetPdfPage(reqPosition, new Consumer<Bitmap>() {
                @Override
                public void accept(Bitmap bitmap) {
                    if (holder.getAdapterPosition() == reqPosition) {
                        holder.progressBar.setVisibility(View.GONE);
                        holder.imageView.setImageBitmap(bitmap);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return getNumberOfPages();
        }
    }

    static File getPdfCacheDir(Context context){
        return context.getExternalFilesDir("pdfview");
    }

    static void close(Closeable closeable){
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static int dp2px(final float dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
