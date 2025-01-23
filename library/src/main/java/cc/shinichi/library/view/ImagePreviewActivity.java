package cc.shinichi.library.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import cc.shinichi.library.ImagePreview;
import cc.shinichi.library.R;
import cc.shinichi.library.bean.ImageInfo;
import cc.shinichi.library.glide.ImageLoader;
import cc.shinichi.library.glide.engine.ProgressTarget;
import cc.shinichi.library.tool.utility.common.HandlerUtils;
import cc.shinichi.library.tool.utility.image.DownloadPictureUtil;
import cc.shinichi.library.tool.utility.ui.ToastUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import java.io.File;
import java.util.List;

import static android.support.v4.content.PermissionChecker.PERMISSION_GRANTED;
import android.widget.PopupWindow;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.OnPermissionCallback;
import android.support.v7.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.widget.ImageButton;

public class ImagePreviewActivity extends AppCompatActivity implements Handler.Callback, View.OnClickListener {
public static final Handler HANDLER = new Handler(Looper.getMainLooper());
    public static final String TAG = "ImagePreview";
    private PopupWindow mPermissionPopup;
    private Context context;

    private List<ImageInfo> imageInfoList;
    private int currentItem;// 当前显示的图片索引
    private boolean isShowDownButton;
    private boolean isShowCloseButton;
    private boolean isShowOriginButton;
    private boolean isShowIndicator;

    private ImagePreviewAdapter imagePreviewAdapter;
    private HackyViewPager viewPager;
    private TextView tv_indicator;
    private FrameLayout fm_image_show_origin_container;
    private FrameLayout fm_center_progress_container;
    private Button btn_show_origin;
    private ImageButton img_download;
    private ImageView imgCloseButton;
    private View rootView;
    private View progressParentLayout;

    private boolean isUserCustomProgressView = false;

    // 指示器显示状态
    private boolean indicatorStatus = false;
    // 原图按钮显示状态
    private boolean originalStatus = false;
    // 下载按钮显示状态
    private boolean downloadButtonStatus = false;
    // 关闭按钮显示状态
    private boolean closeButtonStatus = false;

    private String currentItemOriginPathUrl = "";// 当前显示的原图链接
    private HandlerUtils.HandlerHolder handlerHolder;
    private int lastProgress = 0;
    private AlertDialog dialog;
    View customDialogView;
    public static void activityStart(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setClass(context, ImagePreviewActivity.class);
        context.startActivity(intent);
        ((Activity) context).overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sh_layout_preview);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        context = this;
        handlerHolder = new HandlerUtils.HandlerHolder(this);

        imageInfoList = ImagePreview.getInstance().getImageInfoList();
        if (null == imageInfoList || imageInfoList.size() == 0) {
            onBackPressed();
            return;
        }
        currentItem = ImagePreview.getInstance().getIndex();
        isShowDownButton = ImagePreview.getInstance().isShowDownButton();
        isShowCloseButton = ImagePreview.getInstance().isShowCloseButton();
        isShowIndicator = ImagePreview.getInstance().isShowIndicator();

        currentItemOriginPathUrl = imageInfoList.get(currentItem).getOriginUrl();

        isShowOriginButton = ImagePreview.getInstance().isShowOriginButton(currentItem);
        if (isShowOriginButton) {
            // 检查缓存是否存在
            checkCache(currentItemOriginPathUrl);
        }

        rootView = findViewById(R.id.rootView);
        viewPager = findViewById(R.id.viewPager);
        tv_indicator = findViewById(R.id.tv_indicator);

        fm_image_show_origin_container = findViewById(R.id.fm_image_show_origin_container);
        fm_center_progress_container = findViewById(R.id.fm_center_progress_container);

        fm_image_show_origin_container.setVisibility(View.GONE);
        fm_center_progress_container.setVisibility(View.GONE);

        int progressLayoutId = ImagePreview.getInstance().getProgressLayoutId();
        // != -1 即用户自定义了view
        if (progressLayoutId != -1) {
            // add用户自定义的view到frameLayout中，回调进度和view
            progressParentLayout = View.inflate(context, ImagePreview.getInstance().getProgressLayoutId(), null);
            if (progressParentLayout != null) {
                fm_center_progress_container.removeAllViews();
                fm_center_progress_container.addView(progressParentLayout);
                isUserCustomProgressView = true;
            } else {
                // 使用默认的textView进行百分比的显示
                isUserCustomProgressView = false;
            }
        } else {
            // 使用默认的textView进行百分比的显示
            isUserCustomProgressView = false;
        }

        btn_show_origin = findViewById(R.id.btn_show_origin);
        img_download = findViewById(R.id.img_download);
        imgCloseButton = findViewById(R.id.imgCloseButton);

        img_download.setImageResource(ImagePreview.getInstance().getDownIconResId());
        imgCloseButton.setImageResource(ImagePreview.getInstance().getCloseIconResId());

        // 关闭页面按钮
        imgCloseButton.setOnClickListener(this);
        // 查看与原图按钮
        btn_show_origin.setOnClickListener(this);
        // 下载图片按钮
        img_download.setOnClickListener(this);

        if (!isShowIndicator) {
            tv_indicator.setVisibility(View.GONE);
            indicatorStatus = false;
        } else {
            if (imageInfoList.size() > 1) {
                tv_indicator.setVisibility(View.VISIBLE);
                indicatorStatus = true;
            } else {
                tv_indicator.setVisibility(View.GONE);
                indicatorStatus = false;
            }
        }

        if (isShowDownButton) {
            img_download.setVisibility(View.VISIBLE);
            downloadButtonStatus = true;
        } else {
            img_download.setVisibility(View.GONE);
            downloadButtonStatus = false;
        }

        if (isShowCloseButton) {
            imgCloseButton.setVisibility(View.VISIBLE);
            closeButtonStatus = true;
        } else {
            imgCloseButton.setVisibility(View.GONE);
            closeButtonStatus = false;
        }

        // 更新进度指示器
        tv_indicator.setText(
            String.format(getString(R.string.indicator), currentItem + 1 + "", "" + imageInfoList.size()));

        imagePreviewAdapter = new ImagePreviewAdapter(this, imageInfoList);
        viewPager.setAdapter(imagePreviewAdapter);
        viewPager.setCurrentItem(currentItem);
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    if (ImagePreview.getInstance().getBigImagePageChangeListener() != null) {
                        ImagePreview.getInstance().getBigImagePageChangeListener().onPageSelected(position);
                    }
                    currentItem = position;
                    currentItemOriginPathUrl = imageInfoList.get(position).getOriginUrl();

                    isShowOriginButton = ImagePreview.getInstance().isShowOriginButton(currentItem);
                    if (isShowOriginButton) {
                        // 检查缓存是否存在
                        checkCache(currentItemOriginPathUrl);
                    } else {
                        gone();
                    }
                    // 更新进度指示器
                    tv_indicator.setText(
                        String.format(getString(R.string.indicator), currentItem + 1 + "", "" + imageInfoList.size()));
                    // 如果是自定义百分比进度view，每次切换都先隐藏，并重置百分比
                    if (isUserCustomProgressView) {
                        fm_center_progress_container.setVisibility(View.GONE);
                        lastProgress = 0;
                    }
                }

                @Override public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels);

                    if (ImagePreview.getInstance().getBigImagePageChangeListener() != null) {
                        ImagePreview.getInstance()
                            .getBigImagePageChangeListener()
                            .onPageScrolled(position, positionOffset, positionOffsetPixels);
                    }
                }

                @Override public void onPageScrollStateChanged(int state) {
                    super.onPageScrollStateChanged(state);

                    if (ImagePreview.getInstance().getBigImagePageChangeListener() != null) {
                        ImagePreview.getInstance().getBigImagePageChangeListener().onPageScrollStateChanged(state);
                    }
                }
            });
    }

    /**
     * 下载当前图片到SD卡
     */
    private void downloadCurrentImg() {
        DownloadPictureUtil.downloadPicture(context.getApplicationContext(), currentItemOriginPathUrl);
    }

    @Override public void onBackPressed() {
        finish();
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    public int convertPercentToBlackAlphaColor(float percent) {
        percent = Math.min(1, Math.max(0, percent));
        int intAlpha = (int) (percent * 255);
        String stringAlpha = Integer.toHexString(intAlpha).toLowerCase();
        String color = "#" + (stringAlpha.length() < 2 ? "0" : "") + stringAlpha + "000000";
        return Color.parseColor(color);
    }

    public void setAlpha(float alpha) {
        int colorId = convertPercentToBlackAlphaColor(alpha);
        rootView.setBackgroundColor(colorId);
        if (alpha >= 1) {
            if (indicatorStatus) {
                tv_indicator.setVisibility(View.VISIBLE);
            }
            if (originalStatus) {
                fm_image_show_origin_container.setVisibility(View.VISIBLE);
            }
            if (downloadButtonStatus) {
                img_download.setVisibility(View.VISIBLE);
            }
            if (closeButtonStatus) {
                imgCloseButton.setVisibility(View.VISIBLE);
            }
        } else {
            tv_indicator.setVisibility(View.GONE);
            fm_image_show_origin_container.setVisibility(View.GONE);
            img_download.setVisibility(View.GONE);
            imgCloseButton.setVisibility(View.GONE);
        }
    }

    @Override public boolean handleMessage(Message msg) {
        if (msg.what == 0) {// 点击查看原图按钮，开始加载原图
            final String path = imageInfoList.get(currentItem).getOriginUrl();
            visible();
            if (isUserCustomProgressView) {
                gone();
            } else {
                btn_show_origin.setText("0 %");
            }

            if (checkCache(path)) {
                Message message = handlerHolder.obtainMessage();
                Bundle bundle = new Bundle();
                bundle.putString("url", path);
                message.what = 1;
                message.obj = bundle;
                handlerHolder.sendMessage(message);
                return true;
            }
            loadOriginImage(path);
        } else if (msg.what == 1) {// 加载完成
            Bundle bundle = (Bundle) msg.obj;
            String url = bundle.getString("url");
            gone();
            if (currentItem == getRealIndexWithPath(url)) {
                if (isUserCustomProgressView) {
                    fm_center_progress_container.setVisibility(View.GONE);
                    if (ImagePreview.getInstance().getOnOriginProgressListener() != null) {
                        progressParentLayout.setVisibility(View.GONE);
                        ImagePreview.getInstance().getOnOriginProgressListener().finish(progressParentLayout);
                    }
                    imagePreviewAdapter.loadOrigin(imageInfoList.get(currentItem));
                } else {
                    imagePreviewAdapter.loadOrigin(imageInfoList.get(currentItem));
                }
            }
        } else if (msg.what == 2) {// 加载中
            Bundle bundle = (Bundle) msg.obj;
            String url = bundle.getString("url");
            int progress = bundle.getInt("progress");
            if (currentItem == getRealIndexWithPath(url)) {
                if (isUserCustomProgressView) {
                    gone();
                    fm_center_progress_container.setVisibility(View.VISIBLE);
                    if (ImagePreview.getInstance().getOnOriginProgressListener() != null) {
                        progressParentLayout.setVisibility(View.VISIBLE);
                        ImagePreview.getInstance()
                            .getOnOriginProgressListener()
                            .progress(progressParentLayout, progress);
                    }
                } else {
                    visible();
                    btn_show_origin.setText(String.format("%s %%", String.valueOf(progress)));
                }
            }
        } else if (msg.what == 3) {// 隐藏查看原图按钮
            btn_show_origin.setText("查看原图");
            fm_image_show_origin_container.setVisibility(View.GONE);
            originalStatus = false;
        } else if (msg.what == 4) {// 显示查看原图按钮
            fm_image_show_origin_container.setVisibility(View.VISIBLE);
            originalStatus = true;
        }
        return true;
    }

    private int getRealIndexWithPath(String path) {
        for (int i = 0; i < imageInfoList.size(); i++) {
            if (path.equalsIgnoreCase(imageInfoList.get(i).getOriginUrl())) {
                return i;
            }
        }
        return 0;
    }

    private boolean checkCache(String url) {
        File cacheFile = ImageLoader.getGlideCacheFile(context, url);
        if (cacheFile != null && cacheFile.exists()) {
            gone();
            return true;
        } else {
            visible();
            return false;
        }
    }

    @Override public void onClick(final View v) {
        int i = v.getId();
        if (i == R.id.img_download) {// 检查权限
            
            XXPermissions.with(this).permission("android.permission.MANAGE_EXTERNAL_STORAGE").unchecked().request(new OnPermissionCallback(){

                    @Override
                    public void onGranted(List<String> list, boolean p) {
                        downloadCurrentImg();
                        dismissPopupWindow();
                    }


                    @Override
                    public void onDenied(final List<String> list, boolean p) {
                        if (p) {
                            showdialog(list);

                        }
                        dismissPopupWindow();

                    }


                });
            HANDLER.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        showPopupWindow((Activity)v.getContext());
                    }

                    
                },300);
        } else if (i == R.id.btn_show_origin) {
            handlerHolder.sendEmptyMessage(0);
        } else if (i == R.id.imgCloseButton) {
            onBackPressed();
        }
    }
    private void showPopupWindow(Activity activity) {
        if (mPermissionPopup == null) {
            View contentView = LayoutInflater.from(activity)
                .inflate(R.layout.popupwindow, (ViewGroup)getWindow().getDecorView(), false);
            mPermissionPopup = new PopupWindow(activity);
            mPermissionPopup.setContentView(contentView);
            mPermissionPopup.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
            mPermissionPopup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
            mPermissionPopup.setAnimationStyle(android.R.style.Animation_Dialog);

            mPermissionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mPermissionPopup.setTouchable(true);
            mPermissionPopup.setOutsideTouchable(true);
        }
        PackageManager packageManager = getPackageManager();
        Drawable appIcon = packageManager.getApplicationIcon(getApplicationInfo());

        TextView dialogTitleView = mPermissionPopup.getContentView().findViewById(R.id.dialogTitle);
        TextView messageView = mPermissionPopup.getContentView().findViewById(R.id.dialogText);
        ImageView icon=mPermissionPopup.getContentView().findViewById(R.id.icon);
        icon.setImageDrawable(appIcon);
        dialogTitleView.setText("电工仿真实练请求\"照片,媒体内容和文件\"权限");
        messageView.setText("授权通过后方便为你提供保存图片文件到手机相册的能力");
        // 注意：这里的 PopupWindow 只是示例，没有监听 Activity onDestroy 来处理 PopupWindow 生命周期
        mPermissionPopup.showAtLocation(getWindow().getDecorView(), Gravity.TOP, 0, 0);
    }

    private void dismissPopupWindow() {
        if (mPermissionPopup == null) {
            return;
        }
        if (!mPermissionPopup.isShowing()) {
            return;
        }
        mPermissionPopup.dismiss();
    }



    @Override protected void onDestroy() {
        super.onDestroy();
        ImagePreview.getInstance().reset();
        if (imagePreviewAdapter != null) {
            imagePreviewAdapter.closePage();
        }
    }

    private void gone() {
        handlerHolder.sendEmptyMessage(3);
    }

    private void visible() {
        handlerHolder.sendEmptyMessage(4);
    }

    private void loadOriginImage(final String path) {
        Glide.with(this).load(path).downloadOnly(new ProgressTarget<String, File>(path, null) {
                @Override public void onProgress(String url, long bytesRead, long expectedLength) {
                    int progress = (int) ((float) bytesRead * 100 / (float) expectedLength);

                    if (bytesRead == expectedLength) {// 加载完成
                        Message message = handlerHolder.obtainMessage();
                        Bundle bundle = new Bundle();
                        bundle.putString("url", url);
                        message.what = 1;
                        message.obj = bundle;
                        handlerHolder.sendMessage(message);
                    } else {// 加载中
                        Message message = handlerHolder.obtainMessage();
                        Bundle bundle = new Bundle();
                        bundle.putString("url", url);
                        bundle.putInt("progress", progress);
                        message.what = 2;
                        message.obj = bundle;
                        handlerHolder.sendMessage(message);
                    }
                }

                @Override public void onResourceReady(File resource, GlideAnimation<? super File> animation) {
                    super.onResourceReady(resource, animation);
                    Message message = handlerHolder.obtainMessage();
                    Bundle bundle = new Bundle();
                    bundle.putString("url", path);
                    message.what = 1;
                    message.obj = bundle;
                    handlerHolder.sendMessage(message);
                }

                @Override public void getSize(SizeReadyCallback cb) {
                    cb.onSizeReady(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
                }
            });
    }
    
    
    public void showdialog(final List<String> list) {
        customDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_privacy_policys, null);


        // 初始化自定义布局中的控件
        TextView contentTextView = customDialogView.findViewById(R.id.dialog_content);
        Button agreeButton = customDialogView.findViewById(R.id.button_agree);
        Button disagreeButton = customDialogView.findViewById(R.id.button_disagree);


        contentTextView.setText("为正常使用该功能，请前往系统\"应用设置-管理\"中允许访问手机存储");
        contentTextView.setMovementMethod(LinkMovementMethod.getInstance());

        // 创建对话框并显示
        dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(customDialogView)
            .setCancelable(false)
            .setInverseBackgroundForced(true)
            .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {

                @Override
                public void onShow(DialogInterface dialog) {
                    
                }

                // 应用摇动动画

            });


        // 设置按钮点击事件
        agreeButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {


                    dismissPopupWindow();
                    dialog.dismiss();
                    
                    XXPermissions.startPermissionActivity(v.getContext(), list);

                }


            });

        disagreeButton.setOnClickListener(new View.OnClickListener(){

                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    dismissPopupWindow();
                }


            }

        );

        dialog.show();
    }
}
