package io.github.qzcsfchh.android.pdf;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
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
import androidx.core.util.Supplier;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
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
        mAdapter = new PdfPageAdapter();
        mViewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                mCurrentIndex = position;
            }
        });
        mViewPager2.setAdapter(mAdapter);
    }

    private final ViewPager2.OnPageChangeCallback mOnPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            if (mTvCounter != null) {
                mTvCounter.setVisibility(View.VISIBLE);
                mTvCounter.setText(String.format(Locale.getDefault(), "%d/%d", mCurrentIndex + 1, mAdapter.getItemCount()));
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
            mViewPager2.registerOnPageChangeCallback(mOnPageChangeCallback);
        } else if (!mShowIndicator && mTvCounter != null) {
            mViewPager2.unregisterOnPageChangeCallback(mOnPageChangeCallback);
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
                    if (mAdapter.getItemCount() > 0) {
                        mViewPager2.setCurrentItem(mCurrentIndex, false);
                    }
                }
            });
        }
    };

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        int pageCount = getNumberOfPages();
        if (pageCount<=0) return;
        mCurrentIndex = currentIndex;
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

    private Bitmap syncRenderPdfPage(int index){
        final PdfRenderer renderer = mPdfRenderer.get();
        if (renderer == null) {
            Log.w(TAG, "syncGetPdfPage: PdfRenderer is null");
            return null;
        }
        int pageCount = renderer.getPageCount();
        if (index < 0) index = 0;
        if (index >= pageCount) index = pageCount - 1;
        final PdfRenderer.Page page = renderer.openPage(index);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        final int dpi = metrics.densityDpi;
        double width = dpi * page.getWidth();
        double height = dpi * page.getHeight();
        final double docRatio = width / height;
        // max width pixels set to 2048.
        width = Math.min(metrics.widthPixels, 2048);
        height = (int) (width / docRatio);
        final Bitmap bitmap = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        return bitmap;
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
        mPdfRenderer.set(mRendererSupplier.get());
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
//        String uniqueKey();
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
            // rendering a pdf page is remarkably fast, no need to show loading view.
            holder.progressBar.setVisibility(View.GONE);
            final Bitmap bitmap = syncRenderPdfPage(position);
            holder.imageView.post(new Runnable() {
                @Override
                public void run() {
                    holder.imageView.setImageBitmap(bitmap);
                }
            });

        }

        @Override
        public int getItemCount() {
            return getNumberOfPages();
        }
    }


    static int dp2px(final float dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
}
