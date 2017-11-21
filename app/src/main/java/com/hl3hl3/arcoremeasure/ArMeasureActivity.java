package com.hl3hl3.arcoremeasure;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PlaneHitResult;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.helloar.CameraPermissionHelper;
import com.google.ar.core.examples.java.helloar.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PlaneAttachment;
import com.google.ar.core.examples.java.helloar.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.NotTrackingException;
import com.hl3hl3.arcoremeasure.renderer.RectanglePolygonRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.fabric.sdk.android.Fabric;

/**
 * Created by user on 2017/9/25.
 */

public class ArMeasureActivity extends AppCompatActivity {
    private static final String TAG = "ArRulerActivity";
    private static final String ASSET_NAME_CUBE_OBJ = "cube.obj";
    private static final String ASSET_NAME_CUBE = "cube_green.png";
    private static final String ASSET_NAME_CUBE_SELECTED = "cube_cyan.png";

    private static final String LINK_ARCORE_SERVICE = "https://github.com/google-ar/arcore-android-sdk/releases/download/sdk-preview/arcore-preview.apk";
    private static final String LINK_INFO = "https://developers.google.com/ar/develop/java/getting-started";
    private static final String NEED_ALERT = "needAlert";
    private static final int MAX_CUBE_COUNT = 16;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView mSurfaceView = null;

    private Config mDefaultConfig;
    private Session mSession = null;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private Snackbar mLoadingMessageSnackbar = null;

    private RectanglePolygonRenderer mRectRenderer;

    private PlaneRenderer mPlaneRenderer = new PlaneRenderer();
    private PointCloudRenderer mPointCloud = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] mAnchorMatrix = new float[MAX_CUBE_COUNT];
    private final ImageView[] ivCubeIconList = new ImageView[MAX_CUBE_COUNT];
    private final int[] cubeIconIdArray = {
            R.id.iv_cube1,
            R.id.iv_cube2,
            R.id.iv_cube3,
            R.id.iv_cube4,
            R.id.iv_cube5,
            R.id.iv_cube6,
            R.id.iv_cube7,
            R.id.iv_cube8,
            R.id.iv_cube9,
            R.id.iv_cube10,
            R.id.iv_cube11,
            R.id.iv_cube12,
            R.id.iv_cube13,
            R.id.iv_cube14,
            R.id.iv_cube15,
            R.id.iv_cube16
    };

    // Tap handling and UI.
    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayBlockingQueue<MotionEvent> mQueuedLongPress = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayList<PlaneAttachment> mTouches = new ArrayList<>();
    private ArrayList<Float> mShowingTapPointX = new ArrayList<>();
    private ArrayList<Float> mShowingTapPointY = new ArrayList<>();

    private ArrayBlockingQueue<Float> mQueuedScrollDx = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);
    private ArrayBlockingQueue<Float> mQueuedScrollDy = new ArrayBlockingQueue<>(MAX_CUBE_COUNT);

    private ObjectRenderer mCube = new ObjectRenderer();
    private ObjectRenderer mCubeSelected = new ObjectRenderer();

    private void log(String tag, String log){
        if(BuildConfig.DEBUG) {
            Log.d(tag, log);
        }
    }

    private void log(Exception e){
        try {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }catch (Exception ex){
            if (BuildConfig.DEBUG) {
                ex.printStackTrace();
            }
        }
    }

    private void logStatus(String msg){
        try {
            Crashlytics.log(msg);
        }catch (Exception e){
            log(e);
        }
    }

    //    OverlayView overlayViewForTest;
    TextView tv_result;
    FloatingActionButton fab;

    private GLSurfaceRenderer glSerfaceRenderer = null;
    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return mGestureDetector.onTouchEvent(event);
        }
    };
    private GestureDetector.SimpleOnGestureListener gestureDetectorListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Queue tap if there is space. Tap is lost if queue is full.
            mQueuedSingleTaps.offer(e);
//            log(TAG, "onSingleTapUp, e=" + e.getRawX() + ", " + e.getRawY());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mQueuedLongPress.offer(e);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
//            log(TAG, "onScroll, dx=" + distanceX + " dy=" + distanceY);
            mQueuedScrollDx.offer(distanceX);
            mQueuedScrollDy.offer(distanceY);
            return true;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

//        overlayViewForTest = (OverlayView)findViewById(R.id.overlay_for_test);
        tv_result = (TextView) findViewById(R.id.tv_result);
        fab = (FloatingActionButton) findViewById(R.id.fab);

        for(int i=0; i<cubeIconIdArray.length; i++){
            ivCubeIconList[i] = (ImageView) findViewById(cubeIconIdArray[i]);
            ivCubeIconList[i].setTag(i);
            ivCubeIconList[i].setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view) {
                    try {
                        int index = Integer.valueOf(view.getTag().toString());
                        logStatus("click index cube: " + index);
                        glSerfaceRenderer.setNowTouchingPointIndex(index);
                        glSerfaceRenderer.showMoreAction();
                    }catch (Exception e){
                        log(e);
                    }
                }
            });
        }

        fab.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                logStatus("click fab");
                PopupWindow popUp = getPopupWindow();
//                popUp.showAsDropDown(v, 0, 0); // show popup like dropdown list
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    float screenWidth = getResources().getDisplayMetrics().widthPixels;
                    float screenHeight = getResources().getDisplayMetrics().heightPixels;
                    popUp.showAtLocation(v, Gravity.NO_GRAVITY, (int)screenWidth/2, (int)screenHeight/2);
                } else {
                    popUp.showAsDropDown(v);
                }
            }
        });
        fab.hide();

        if(getSharedPreferences().getBoolean(NEED_ALERT, true)){
            showInitAlert();
        }else{
            initSurface();
        }

    }

    private void toast(int stringResId){
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }
    private boolean isVerticalMode = false;
    private PopupWindow popupWindow;
    private PopupWindow getPopupWindow() {

        // initialize a pop up window type
        popupWindow = new PopupWindow(this);

        ArrayList<String> sortList = new ArrayList<>();
        sortList.add(getString(R.string.action_1));
        sortList.add(getString(R.string.action_2));
        sortList.add(getString(R.string.action_3));
        sortList.add(getString(R.string.action_4));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                sortList);
        // the drop down list is a list view
        ListView listViewSort = new ListView(this);
        // set our adapter and pass our pop up window contents
        listViewSort.setAdapter(adapter);
        listViewSort.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 3:// move vertical axis
                        toast(R.string.action_4_toast);
                        break;
                    case 0:// delete
                        toast(R.string.action_1_toast);
                        break;
                    case 1:// set as first
                        toast(R.string.action_2_toast);
                        break;
                    case 2:// move horizontal axis
                    default:
                        toast(R.string.action_3_toast);
                        break;
                }
                return true;
            }
        });
        // set on item selected
        listViewSort.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 3:// move vertical axis
                        isVerticalMode = true;
                        popupWindow.dismiss();
                        break;
                    case 0:// delete
                        glSerfaceRenderer.deleteNowSelection();
                        popupWindow.dismiss();
                        fab.hide();
                        break;
                    case 1:// set as first
                        glSerfaceRenderer.setNowSelectionAsFirst();
                        popupWindow.dismiss();
                        fab.hide();
                        break;
                    case 2:// move horizontal axis
                    default:
                        isVerticalMode = false;
                        popupWindow.dismiss();
                        break;
                }

            }
        });
        // some other visual settings for popup window
        popupWindow.setFocusable(true);
        popupWindow.setWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.4f));
        // popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.white));
        popupWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        // set the listview as popup content
        popupWindow.setContentView(listViewSort);
        return popupWindow;
    }

    private SharedPreferences getSharedPreferences(){
        return getSharedPreferences("ArMeasureActivity", MODE_PRIVATE);
    }

    private void showInitAlert(){
        final View view = View.inflate(this, R.layout.alert, null);

        view.findViewById(R.id.tv_dontshow).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                v.setActivated(!v.isActivated());
            }
        });

        view.findViewById(R.id.tv_ok).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                boolean isChecked = view.findViewById(R.id.tv_dontshow).isActivated();
                getSharedPreferences().edit().putBoolean(NEED_ALERT, !isChecked).apply();
                initDialog.dismiss();
                if(initSurface()) {
                    surfaceOnResume();
                }
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        builder.setCancelable(false);
        initDialog = builder.create();
        initDialog.show();
    }

    Dialog initDialog = null;

    private void showNotSupportAlert(){
        logStatus("showNotSupportAlert()");
        final View view = View.inflate(this, R.layout.alert_notsupport, null);
        view.findViewById(R.id.tv_ok).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        builder.setCancelable(false);
        builder.create().show();
    }

    private boolean initSurface(){
        logStatus("initSurface()");
        mSession = new Session(this);

        // Create default config, check is supported, create session from that config.
        mDefaultConfig = Config.createDefaultConfig();
        if (!mSession.isSupported(mDefaultConfig)) {
            showNotSupportAlert();
            return false;
        }else {
            // Set up renderer.
            glSerfaceRenderer = new GLSurfaceRenderer(this);
            FrameLayout flContent = (FrameLayout)findViewById(R.id.fl_content);
            mSurfaceView = new GLSurfaceView(this);
            flContent.addView(mSurfaceView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mSurfaceView.setPreserveEGLContextOnPause(true);
            mSurfaceView.setEGLContextClientVersion(2);
            mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
            mSurfaceView.setRenderer(glSerfaceRenderer);
            mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

            // Set up tap listener.
            mGestureDetector = new GestureDetector(this, gestureDetectorListener);
            mSurfaceView.setOnTouchListener(onTouchListener);
            return true;
        }
    }

    private boolean isSurfaceResume = false;

    private void surfaceOnResume(){
        logStatus("surfaceOnResume()");
        if(isSurfaceResume){
            return;
        }
        if(mSession == null){
            return;
        }
        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            // Note that order matters - see the note in onPause(), the reverse applies here.
            if (mSurfaceView != null) {
                showLoadingMessage();
                mSession.resume(mDefaultConfig);
                mSurfaceView.onResume();
                isSurfaceResume = true;
            }
        } else {
            CameraPermissionHelper.requestCameraPermission(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        logStatus("onResume()");
        surfaceOnResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        logStatus("onPause()");
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        if(isSurfaceResume) {
            if (mSession != null) {
                if (mSurfaceView != null) {
                    mSurfaceView.onPause();
                }
                mSession.pause();
                isSurfaceResume = false;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        logStatus("onRequestPermissionsResult()");
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, R.string.need_permission, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        logStatus("onWindowFocusChanged()");
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar = Snackbar.make(
                        ArMeasureActivity.this.findViewById(android.R.id.content),
                        "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE);
                mLoadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
                mLoadingMessageSnackbar.show();
            }
        });
    }

    private void hideLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar.dismiss();
                mLoadingMessageSnackbar = null;
            }
        });
    }

    private class GLSurfaceRenderer implements GLSurfaceView.Renderer{
        private static final String TAG = "GLSurfaceRenderer";
        private Context context;
        private final int DEFAULT_VALUE = -1;
        private int nowTouchingPointIndex = DEFAULT_VALUE;
        private int viewWidth = 0;
        private int viewHeight = 0;
        // according to cube.obj, cube diameter = 0.02f
        private final float cubeHitAreaRadius = 0.08f;
        private final float[] centerVertexOfCube = {0f, 0f, 0f, 1};
        private final float[] vertexResult = new float[4];

        private float[] tempTranslation = new float[3];
        private float[] tempRotation = new float[4];
        private float[] projmtx = new float[16];
        private float[] viewmtx = new float[16];

        public GLSurfaceRenderer(Context context){
            this.context = context;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            logStatus("onSurfaceCreated()");
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

            // Create the texture and pass it to ARCore session to be filled during update().
            mBackgroundRenderer.createOnGlThread(context);
            mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());
            mRectRenderer = new RectanglePolygonRenderer();
            // Prepare the other rendering objects.
            try {
                mCube.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE);
                mCube.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
                mCubeSelected.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE_SELECTED);
                mCubeSelected.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
            } catch (IOException e) {
                log(TAG, "Failed to read obj file");
            }
            try {
                mPlaneRenderer.createOnGlThread(context, "trigrid.png");
            } catch (IOException e) {
                log(TAG, "Failed to read plane texture");
            }
            mPointCloud.createOnGlThread(context);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            if(width <= 0 || height <= 0){
                logStatus("onSurfaceChanged(), <= 0");
                return;
            }
            logStatus("onSurfaceChanged()");
            GLES20.glViewport(0, 0, width, height);
            // Notify ARCore session that the view size changed so that the perspective matrix and
            // the video background can be properly adjusted.
            mSession.setDisplayGeometry(width, height);
            viewWidth = width;
            viewHeight = height;
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public void deleteNowSelection(){
            logStatus("deleteNowSelection()");
            int index = nowTouchingPointIndex;
            if (index > -1){
                if(index < mTouches.size()) {
                    mSession.removeAnchors(Arrays.asList(mTouches.remove(index).getAnchor()));
                }
                if(index < mShowingTapPointX.size()) {
                    mShowingTapPointX.remove(index);
                }
                if(index < mShowingTapPointY.size()) {
                    mShowingTapPointY.remove(index);
                }
            }
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public void setNowSelectionAsFirst(){
            logStatus("setNowSelectionAsFirst()");
            int index = nowTouchingPointIndex;
            if (index > -1 && index < mTouches.size()) {
                if(index < mTouches.size()){
                    for(int i=0; i<index; i++){
                        mTouches.add(mTouches.remove(0));
                    }
                }
                if(index < mShowingTapPointX.size()){
                    for(int i=0; i<index; i++){
                        mShowingTapPointX.add(mShowingTapPointX.remove(0));
                    }
                }
                if(index < mShowingTapPointY.size()){
                    for(int i=0; i<index; i++){
                        mShowingTapPointY.add(mShowingTapPointY.remove(0));
                    }
                }
            }
            setNowTouchingPointIndex(DEFAULT_VALUE);
        }

        public int getNowTouchingPointIndex(){
            return nowTouchingPointIndex;
        }

        public void setNowTouchingPointIndex(int index){
            nowTouchingPointIndex = index;
            showCubeStatus();
        }

        @Override
        public void onDrawFrame(GL10 gl) {
//            log(TAG, "onDrawFrame(), mTouches.size=" + mTouches.size());
            // Clear screen to notify driver it should not load any pixels from previous frame.
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            if(viewWidth == 0 || viewWidth == 0){
                return;
            }
            try {
                // Obtain the current frame from ARSession. When the configuration is set to
                // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
                // camera framerate.
                Frame frame = mSession.update();
                // Draw background.
                mBackgroundRenderer.draw(frame);

                // If not tracking, don't draw 3d objects.
                if (frame.getTrackingState() == Frame.TrackingState.NOT_TRACKING) {
                    return;
                }

                // Get projection matrix.
                mSession.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

                // Get camera matrix and draw.
                frame.getViewMatrix(viewmtx, 0);

                // Compute lighting from average intensity of the image.
                final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

                // Visualize tracked points.
                mPointCloud.update(frame.getPointCloud());
                mPointCloud.draw(frame.getPointCloudPose(), viewmtx, projmtx);

                // Check if we detected at least one plane. If so, hide the loading message.
                if (mLoadingMessageSnackbar != null) {
                    for (Plane plane : mSession.getAllPlanes()) {
                        if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                plane.getTrackingState() == Plane.TrackingState.TRACKING) {
                            hideLoadingMessage();
                            break;
                        }
                    }
                }

                // Visualize planes.
                mPlaneRenderer.drawPlanes(mSession.getAllPlanes(), frame.getPose(), projmtx);

                // draw cube & line from last frame
                if(mTouches.size() < 1){
                    // no point
                    showResult("");
                }else{
                    // draw selected cube
                    if(nowTouchingPointIndex != DEFAULT_VALUE) {
                        drawObj(mTouches.get(nowTouchingPointIndex).getPoseNoAlign(), mCubeSelected, viewmtx, projmtx, lightIntensity);
                        checkIfHit(mCubeSelected, nowTouchingPointIndex);
                    }
                    StringBuilder sb = new StringBuilder();
                    double total = 0;
                    Pose point1;
                    // draw first cube
                    Pose point0 = mTouches.get(0).getPoseNoAlign();
                    drawObj(point0, mCube, viewmtx, projmtx, lightIntensity);
                    checkIfHit(mCube, 0);
                    // draw the rest cube
                    for(int i = 1; i < mTouches.size(); i++){
                        point1 = mTouches.get(i).getPoseNoAlign();
                        drawObj(point1, mCube, viewmtx, projmtx, lightIntensity);
                        checkIfHit(mCube, i);
                        drawLine(point0, point1, viewmtx, projmtx);

                        float distanceCm = ((int)(getDistance(point0, point1) * 1000))/10.0f;
                        total += distanceCm;
                        sb.append(" + ").append(distanceCm);

                        point0 = point1;
                    }

                    // show result
                    String result = sb.toString().replaceFirst("[+]", "") + " = " + (((int)(total * 10f))/10f) + "cm";
                    showResult(result);
                }

                // check if there is any touch event
                MotionEvent tap = mQueuedSingleTaps.poll();
                if(tap != null){
                    for (HitResult hit : frame.hitTest(tap)) {
                        // Check if any plane was hit, and if it was hit inside the plane polygon.
                        if (hit instanceof PlaneHitResult && ((PlaneHitResult) hit).isHitInPolygon()) {
                            // Cap the number of objects created. This avoids overloading both the
                            // rendering system and ARCore.
                            if (mTouches.size() >= 16) {
                                mSession.removeAnchors(Arrays.asList(mTouches.get(0).getAnchor()));
                                mTouches.remove(0);

                                mShowingTapPointX.remove(0);
                                mShowingTapPointY.remove(0);
                            }

                            // Adding an Anchor tells ARCore that it should track this position in
                            // space. This anchor will be used in PlaneAttachment to place the 3d model
                            // in the correct position relative both to the world and to the plane.
                            mTouches.add(new PlaneAttachment(
                                    ((PlaneHitResult) hit).getPlane(),
                                    mSession.addAnchor(hit.getHitPose())));

                            mShowingTapPointX.add(tap.getX());
                            mShowingTapPointY.add(tap.getY());

                            if(mTouches.size() == 0){
                                nowTouchingPointIndex = DEFAULT_VALUE;
                            }else {
                                nowTouchingPointIndex = mTouches.size() - 1;
                            }

                            showMoreAction();
                            showCubeStatus();
                            break;
                        }
                    }
                }else{
                    handleMoveEvent(nowTouchingPointIndex);
                }
            } catch (Throwable t) {
                // Avoid crashing the application due to unhandled exceptions.
                Log.e(TAG, "Exception on the OpenGL thread", t);
            }
        }

        private void handleMoveEvent(int nowSelectedIndex){
            try {
                if (mShowingTapPointX.size() < 1 || mQueuedScrollDx.size() < 2) {
                    // no action, don't move
                    return;
                }
                if (nowTouchingPointIndex == DEFAULT_VALUE) {
                    // no selected cube, don't move
                    return;
                }
                if (nowSelectedIndex >= mShowingTapPointX.size()) {
                    // wrong index, don't move.
                    return;
                }
                float scrollDx = 0;
                float scrollDy = 0;
                int scrollQueueSize = mQueuedScrollDx.size();
                for (int i = 0; i < scrollQueueSize; i++) {
                    scrollDx += mQueuedScrollDx.poll();
                    scrollDy += mQueuedScrollDy.poll();
                }

                if (isVerticalMode) {
                    PlaneAttachment attachment = mTouches.remove(nowSelectedIndex);
                    mSession.removeAnchors(Arrays.asList(attachment.getAnchor()));
                    setPoseDataToTempArray(attachment.getPoseNoAlign());
//                        log(TAG, "point[" + nowSelectedIndex + "] move vertical "+ (scrollDy / viewHeight) + ", tY=" + tempTranslation[1]
//                                + ", new tY=" + (tempTranslation[1] += (scrollDy / viewHeight)));
                    tempTranslation[1] += (scrollDy / viewHeight);
                    mTouches.add(nowSelectedIndex, new PlaneAttachment(
                            attachment.getPlane(),
                            mSession.addAnchor(new Pose(tempTranslation, tempRotation))));
                } else {
                    float toX = mShowingTapPointX.get(nowSelectedIndex) - scrollDx;
                    mShowingTapPointX.remove(nowSelectedIndex);
                    mShowingTapPointX.add(nowSelectedIndex, toX);

                    float toY = mShowingTapPointY.get(nowSelectedIndex) - scrollDy;
                    mShowingTapPointY.remove(nowSelectedIndex);
                    mShowingTapPointY.add(nowSelectedIndex, toY);

                    if (mTouches.size() > nowSelectedIndex) {
                        PlaneAttachment attachment = mTouches.remove(nowSelectedIndex);
                        // remove duplicated anchor
                        mSession.removeAnchors(Arrays.asList(attachment.getAnchor()));
                        setPoseDataToTempArray(attachment.getPoseNoAlign());
                        tempTranslation[0] -= (scrollDx / viewWidth);
                        tempTranslation[2] -= (scrollDy / viewHeight);
                        mTouches.add(nowSelectedIndex, new PlaneAttachment(
                                attachment.getPlane(),
                                mSession.addAnchor(new Pose(tempTranslation, tempRotation))));
                    }
                }
            } catch (NotTrackingException e) {
                e.printStackTrace();
            }
        }

        private void setPoseDataToTempArray(Pose pose){
            pose.getTranslation(tempTranslation, 0);
            pose.getRotationQuaternion(tempRotation, 0);
        }

        private void drawLine(Pose pose0, Pose pose1, float[] viewmtx, float[] projmtx){
            float lineWidth = 0.002f;
            float lineWidthH = lineWidth / viewHeight * viewWidth;
            mRectRenderer.setVerts(
                    pose0.tx() - lineWidth, pose0.ty() + lineWidthH, pose0.tz() - lineWidth,
                    pose0.tx() + lineWidth, pose0.ty() + lineWidthH, pose0.tz() + lineWidth,
                    pose1.tx() + lineWidth, pose1.ty() + lineWidthH, pose1.tz() + lineWidth,
                    pose1.tx() - lineWidth, pose1.ty() + lineWidthH, pose1.tz() - lineWidth
                    ,
                    pose0.tx() - lineWidth, pose0.ty() - lineWidthH, pose0.tz() - lineWidth,
                    pose0.tx() + lineWidth, pose0.ty() - lineWidthH, pose0.tz() + lineWidth,
                    pose1.tx() + lineWidth, pose1.ty() - lineWidthH, pose1.tz() + lineWidth,
                    pose1.tx() - lineWidth, pose1.ty() - lineWidthH, pose1.tz() - lineWidth
            );

            mRectRenderer.draw(viewmtx, projmtx);
        }

        private void drawObj(Pose pose, ObjectRenderer renderer, float[] cameraView, float[] cameraPerspective, float lightIntensity){
            pose.toMatrix(mAnchorMatrix, 0);
            renderer.updateModelMatrix(mAnchorMatrix, 1);
            renderer.draw(cameraView, cameraPerspective, lightIntensity);
        }

        private void checkIfHit(ObjectRenderer renderer, int cubeIndex){
            if(isMVPMatrixHitMotionEvent(renderer.getModelViewProjectionMatrix(), mQueuedLongPress.peek())){
                // long press hit a cube, show context menu for the cube
                nowTouchingPointIndex = cubeIndex;
                mQueuedLongPress.poll();
                showMoreAction();
                showCubeStatus();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fab.performClick();
                    }
                });
            }else if(isMVPMatrixHitMotionEvent(renderer.getModelViewProjectionMatrix(), mQueuedSingleTaps.peek())){
                nowTouchingPointIndex = cubeIndex;
                mQueuedSingleTaps.poll();
                showMoreAction();
                showCubeStatus();
            }
        }

        private boolean isMVPMatrixHitMotionEvent(float[] ModelViewProjectionMatrix, MotionEvent event){
            if(event == null){
                return false;
            }
            Matrix.multiplyMV(vertexResult, 0, ModelViewProjectionMatrix, 0, centerVertexOfCube, 0);
            /**
             * vertexResult = [x, y, z, w]
             *
             * coordinates in View
             * ┌─────────────────────────────────────────┐╮
             * │[0, 0]                     [viewWidth, 0]│
             * │       [viewWidth/2, viewHeight/2]       │view height
             * │[0, viewHeight]   [viewWidth, viewHeight]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             *
             * coordinates in GLSurfaceView frame
             * ┌─────────────────────────────────────────┐╮
             * │[-1.0,  1.0]                  [1.0,  1.0]│
             * │                 [0, 0]                  │view height
             * │[-1.0, -1.0]                  [1.0, -1.0]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             */
            // circle hit test
            float radius = (viewWidth / 2) * (cubeHitAreaRadius/vertexResult[3]);
            float dx = event.getX() - (viewWidth / 2) * (1 + vertexResult[0]/vertexResult[3]);
            float dy = event.getY() - (viewHeight / 2) * (1 - vertexResult[1]/vertexResult[3]);
            double distance = Math.sqrt(dx * dx + dy * dy);
//            // for debug
//            overlayViewForTest.setPoint("cubeCenter", screenX, screenY);
//            overlayViewForTest.postInvalidate();
            return distance < radius;
        }

        private double getDistance(Pose pose0, Pose pose1){
            float dx = pose0.tx() - pose1.tx();
            float dy = pose0.ty() - pose1.ty();
            float dz = pose0.tz() - pose1.tz();
            return Math.sqrt(dx * dx + dz * dz + dy * dy);
        }

        private void showResult(final String result){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_result.setText(result);
                }
            });
        }

        private void showMoreAction(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fab.show();
                }
            });
        }

        private void hideMoreAction(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fab.hide();
                }
            });
        }

        private void showCubeStatus(){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int nowSelectIndex = glSerfaceRenderer.getNowTouchingPointIndex();
                    for(int i=0; i<ivCubeIconList.length && i<mTouches.size(); i++){
                        ivCubeIconList[i].setEnabled(true);
                        ivCubeIconList[i].setActivated(i == nowSelectIndex);
                    }
                    for(int i=mTouches.size(); i<ivCubeIconList.length; i++){
                        ivCubeIconList[i].setEnabled(false);
                    }
                }
            });
        }

    }
}
