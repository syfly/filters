package jp.co.cyberagent.android.av;
import java.io.File;
import java.io.IOException;

import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;









//import com.android.grafika.TextureMovieEncoder2;
//import com.android.grafika.VideoEncoderCore;
//import com.android.grafika.RecordFBOActivity.ActivityHandler;
//import com.android.grafika.RecordFBOActivity.RenderHandler;
import com.android.grafika.gles.Drawable2d;
import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FlatShadedProgram;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.GlUtil;
import com.android.grafika.gles.Sprite2d;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

public class RenderHelper extends Thread {
        // Object must be created on render thread to get correct Looper, but is used from
        // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully

		private static final String TAG = "";
        // Used to wait for the thread to start.

        private volatile SurfaceHolder mSurfaceHolder;  // may be updated by UI thread
        private EglCore mEglCore;
        private WindowSurface mWindowSurface;

        // Orthographic projection matrix.
        private float[] mDisplayProjectionMatrix = new float[16];

        private final Drawable2d mTriDrawable = new Drawable2d(Drawable2d.Prefab.TRIANGLE);
        private final Drawable2d mRectDrawable = new Drawable2d(Drawable2d.Prefab.RECTANGLE);

        // One spinning triangle, one bouncing rectangle, and four edge-boxes.
        private Sprite2d mTri;
        private Sprite2d mRect;
        private Sprite2d mEdges[];
        private Sprite2d mRecordRect;
        private float mRectVelX, mRectVelY;     // velocity, in viewport units per second
        private float mInnerLeft, mInnerTop, mInnerRight, mInnerBottom;

        private final float[] mIdentityMatrix;
        
        // Previous frame time.
        private long mPrevTimeNanos;

        // Used for off-screen rendering.
        public FullFrameRect mFullScreen;
        // Used for recording.
        private File mOutputFile;
        public WindowSurface mInputWindowSurface;
        public TextureMovieEncoder2 mVideoEncoder;
        public Rect mVideoRect;

        /**
         * Pass in the SurfaceView's SurfaceHolder.  Note the Surface may not yet exist.
         */
        public RenderHelper(SurfaceHolder holder, File outputFile, long refreshPeriodNs) {
            mSurfaceHolder = holder;
            mOutputFile = outputFile;

            mVideoRect = new Rect();

            mIdentityMatrix = new float[16];
            Matrix.setIdentityM(mIdentityMatrix, 0);

            mTri = new Sprite2d(mTriDrawable);
            mRect = new Sprite2d(mRectDrawable);
            mEdges = new Sprite2d[4];
            for (int i = 0; i < mEdges.length; i++) {
                mEdges[i] = new Sprite2d(mRectDrawable);
            }
            mRecordRect = new Sprite2d(mRectDrawable);
            
            prepareGl();
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);
        }

        private void prepareGl() {
            Log.d(TAG, "prepareGl");

            //mWindowSurface = new WindowSurface(mEglCore, surface, false);
            //mWindowSurface.makeCurrent();

            // Used for blitting texture to FBO.
//            mFullScreen = new FullFrameRect(
//                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

            // Program used for drawing onto the screen.
            //mProgram = new FlatShadedProgram();

            // Set the background color.
            //GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

            // Disable depth testing -- we're 2D only.
            //GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            // Don't need backface culling.  (If you're feeling pedantic, you can turn it on to
            // make sure we're defining our shapes correctly.)
            //GLES20.glDisable(GLES20.GL_CULL_FACE);

            //mActivityHandler.sendGlesVersion(mEglCore.getGlVersion());
        }
        
        /**
         * Shuts everything down.
         */
        public void shutdown() {
            Log.d(TAG, "shutdown");
            stopEncoder();
        }

        /**
         * Prepares the surface.
         */
        public void surfaceCreated() {
            Surface surface = mSurfaceHolder.getSurface();
        }


       /**
         * Handles changes to the size of the underlying surface.  Adjusts viewport as needed.
         * Must be called before we start drawing.
         * (Called from RenderHandler.)
         */
        public void surfaceChanged(int width, int height) {
            Log.d(TAG, "surfaceChanged " + width + "x" + height);

            //prepareFramebuffer(width, height);

            // Use full window.
            GLES20.glViewport(0, 0, width, height);

            // Simple orthographic projection, with (0,0) in lower-left corner.
            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);

            int smallDim = Math.min(width, height);

            // Set initial shape size / position / velocity based on window size.  Movement
            // has the same "feel" on all devices, but the actual path will vary depending
            // on the screen proportions.  We do it here, rather than defining fixed values
            // and tweaking the projection matrix, so that our squares are square.
            mTri.setColor(0.1f, 0.9f, 0.1f);
            mTri.setScale(smallDim / 4.0f, smallDim / 4.0f);
            mTri.setPosition(width / 2.0f, height / 2.0f);
            mRect.setColor(0.9f, 0.1f, 0.1f);
            mRect.setScale(smallDim / 8.0f, smallDim / 8.0f);
            mRect.setPosition(width / 2.0f, height / 2.0f);
            mRectVelX = 1 + smallDim / 4.0f;
            mRectVelY = 1 + smallDim / 5.0f;

            // left edge
            float edgeWidth = 1 + width / 64.0f;
            mEdges[0].setScale(edgeWidth, height);
            mEdges[0].setPosition(edgeWidth / 2.0f, height / 2.0f);
            // right edge
            mEdges[1].setScale(edgeWidth, height);
            mEdges[1].setPosition(width - edgeWidth / 2.0f, height / 2.0f);
            // top edge
            mEdges[2].setScale(width, edgeWidth);
            mEdges[2].setPosition(width / 2.0f, height - edgeWidth / 2.0f);
            // bottom edge
            mEdges[3].setScale(width, edgeWidth);
            mEdges[3].setPosition(width / 2.0f, edgeWidth / 2.0f);

            mRecordRect.setColor(1.0f, 1.0f, 1.0f);
            mRecordRect.setScale(edgeWidth * 2f, edgeWidth * 2f);
            mRecordRect.setPosition(edgeWidth / 2.0f, edgeWidth / 2.0f);

            // Inner bounding rect, used to bounce objects off the walls.
            mInnerLeft = mInnerBottom = edgeWidth;
            mInnerRight = width - 1 - edgeWidth;
            mInnerTop = height - 1 - edgeWidth;

            Log.d(TAG, "mTri: " + mTri);
            Log.d(TAG, "mRect: " + mRect);
        }

        /**
         * Releases most of the GL resources we currently hold.
         * <p>
         * Does not release EglCore.
         */

        /**
         * Creates the video encoder object and starts the encoder thread.  Creates an EGL
         * surface for encoder input.
         */
        public void startEncoder(int windowWidth, int windowHeight) {
            Log.d(TAG, "starting to record");
            // Record at 1280x720, regardless of the window dimensions.  The encoder may
            // explode if given "strange" dimensions, e.g. a width that is not a multiple
            // of 16.  We can box it as needed to preserve dimensions.
            final int BIT_RATE = 4000000;   // 4Mbps
            final int VIDEO_WIDTH = 1280;
            final int VIDEO_HEIGHT = 720;
//            int windowWidth = mWindowSurface.getWidth();
//            int windowHeight = mWindowSurface.getHeight();
            float windowAspect = (float) windowHeight / (float) windowWidth;
            int outWidth, outHeight;
            if (VIDEO_HEIGHT > VIDEO_WIDTH * windowAspect) {
                // limited by narrow width; reduce height
                outWidth = VIDEO_WIDTH;
                outHeight = (int) (VIDEO_WIDTH * windowAspect);
            } else {
                // limited by short height; restrict width
                outHeight = VIDEO_HEIGHT;
                outWidth = (int) (VIDEO_HEIGHT / windowAspect);
            }
            int offX = (VIDEO_WIDTH - outWidth) / 2;
            int offY = (VIDEO_HEIGHT - outHeight) / 2;
            mVideoRect.set(offX, offY, offX + outWidth, offY + outHeight);
            Log.d(TAG, "Adjusting window " + windowWidth + "x" + windowHeight +
                    " to +" + offX + ",+" + offY + " " +
                    mVideoRect.width() + "x" + mVideoRect.height());

            VideoEncoderCore encoderCore;
            try {
                encoderCore = new VideoEncoderCore(VIDEO_WIDTH, VIDEO_HEIGHT,
                        BIT_RATE, mOutputFile);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mInputWindowSurface = new WindowSurface(mEglCore, encoderCore.getInputSurface(), true);
            mVideoEncoder = new TextureMovieEncoder2(encoderCore);
        }

        public void drain() {
        	mInputWindowSurface.makeCurrent();
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);    // again, only really need to
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     //  clear pixels outside rect
            GLES20.glViewport(mVideoRect.left, mVideoRect.top,
                    mVideoRect.width(), mVideoRect.height());
            //mFullScreen.drawFrame(mOffscreenTexture, mIdentityMatrix);
            //mInputWindowSurface.setPresentationTime(1);
            //mInputWindowSurface.swapBuffers();
        }
        /**
         * Stops the video encoder if it's running.
         */
        public void stopEncoder() {
            if (mVideoEncoder != null) {
                Log.d(TAG, "stopping recorder, mVideoEncoder=" + mVideoEncoder);
                mVideoEncoder.stopRecording();
                // TODO: wait (briefly) until it finishes shutting down so we know file is
                //       complete, or have a callback that updates the UI
                mVideoEncoder = null;
            }
            if (mInputWindowSurface != null) {
                mInputWindowSurface.release();
                mInputWindowSurface = null;
            }
        }
        /**
         * We use the time delta from the previous event to determine how far everything
         * moves.  Ideally this will yield identical animation sequences regardless of
         * the device's actual refresh rate.
         */
        public void update(long timeStampNanos) {
            // Compute time from previous frame.
            long intervalNanos;
            if (mPrevTimeNanos == 0) {
                intervalNanos = 0;
            } else {
                intervalNanos = timeStampNanos - mPrevTimeNanos;

                final long ONE_SECOND_NANOS = 1000000000L;
                if (intervalNanos > ONE_SECOND_NANOS) {
                    // A gap this big should only happen if something paused us.  We can
                    // either cap the delta at one second, or just pretend like this is
                    // the first frame and not advance at all.
                    Log.d(TAG, "Time delta too large: " +
                            (double) intervalNanos / ONE_SECOND_NANOS + " sec");
                    intervalNanos = 0;
                }
            }
            mPrevTimeNanos = timeStampNanos;

            final float ONE_BILLION_F = 1000000000.0f;
            final float elapsedSeconds = intervalNanos / ONE_BILLION_F;

            // Spin the triangle.  We want one full 360-degree rotation every 3 seconds,
            // or 120 degrees per second.
            final int SECS_PER_SPIN = 3;
            float angleDelta = (360.0f / SECS_PER_SPIN) * elapsedSeconds;
            mTri.setRotation(mTri.getRotation() + angleDelta);

            // Bounce the rect around the screen.  The rect is a 1x1 square scaled up to NxN.
            // We don't do fancy collision detection, so it's possible for the box to slightly
            // overlap the edges.  We draw the edges last, so it's not noticeable.
            float xpos = mRect.getPositionX();
            float ypos = mRect.getPositionY();
            float xscale = mRect.getScaleX();
            float yscale = mRect.getScaleY();
            xpos += mRectVelX * elapsedSeconds;
            ypos += mRectVelY * elapsedSeconds;
            if ((mRectVelX < 0 && xpos - xscale/2 < mInnerLeft) ||
                    (mRectVelX > 0 && xpos + xscale/2 > mInnerRight+1)) {
                mRectVelX = -mRectVelX;
            }
            if ((mRectVelY < 0 && ypos - yscale/2 < mInnerBottom) ||
                    (mRectVelY > 0 && ypos + yscale/2 > mInnerTop+1)) {
                mRectVelY = -mRectVelY;
            }
            mRect.setPosition(xpos, ypos);
        }
}